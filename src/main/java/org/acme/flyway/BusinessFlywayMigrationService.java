package org.acme.flyway;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.flywaydb.core.Flyway;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class BusinessFlywayMigrationService {
    private static final Logger LOG = Logger.getLogger(BusinessFlywayMigrationService.class);
    private static final String DEFAULT_TENANT_DB_SOURCE = "tenant_a";

    @Inject
    Config config;

    @Inject
    BusinessFlywayFactory flywayFactory;

    @ConfigProperty(name = "repro.flyway.business.enabled", defaultValue = "true")
    boolean businessFlywayEnabled;

    @ConfigProperty(name = "repro.flyway.patch.enabled", defaultValue = "true")
    boolean patchEnabled;

    @ConfigProperty(name = "repro.flyway.cutover-version", defaultValue = "20260325.0000.00")
    String cutoverVersion;

    BusinessFlywayMigrationService() {
    }

    BusinessFlywayMigrationService(BusinessFlywayFactory flywayFactory,
                                   Config config,
                                   boolean businessFlywayEnabled,
                                   boolean patchEnabled,
                                   String cutoverVersion) {
        this.flywayFactory = flywayFactory;
        this.config = config;
        this.businessFlywayEnabled = businessFlywayEnabled;
        this.patchEnabled = patchEnabled;
        this.cutoverVersion = cutoverVersion;
    }

    public void migrate(@Observes @Priority(Interceptor.Priority.LIBRARY_BEFORE) StartupEvent startupEvent) {
        if (!businessFlywayEnabled) {
            LOG.info("Business Flyway migration is disabled by configuration.");
            return;
        }

        List<String> tenantDbSources = resolveStartupTenantDbSources();
        LOG.infof("Starting business Flyway migration with cutover version %s for tenant db sources %s",
                cutoverVersion, tenantDbSources);
        migrateTenants(tenantDbSources);
    }

    public void migrateTenant(String tenantKey) {
        TenantDatabaseConfig tenantConfig = resolveTenantDatabaseConfig(tenantKey);
        LOG.infof("Starting on-demand business Flyway migration with cutover version %s for tenant db source %s",
                cutoverVersion, tenantConfig.tenantKey());
        migrateResolvedTenant(tenantConfig);
    }

    List<String> resolveStartupTenantDbSources() {
        String configuredTenantDbSources = config.getOptionalValue("repro.flyway.tenant-db-sources", String.class)
                .orElse("");
        List<String> parsedTenantDbSources = parseTenantDbSources(configuredTenantDbSources);
        if (parsedTenantDbSources.isEmpty()) {
            return List.of(DEFAULT_TENANT_DB_SOURCE);
        }
        return parsedTenantDbSources;
    }

    static List<String> parseTenantDbSources(String configuredTenantDbSources) {
        if (configuredTenantDbSources == null || configuredTenantDbSources.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> normalizedTenantDbSources = Arrays.stream(configuredTenantDbSources.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(BusinessFlywayFactory::normalizeTenantKey)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        return new ArrayList<>(normalizedTenantDbSources);
    }

    void migrateTenants(List<String> tenantDbSources) {
        for (String tenantDbSource : tenantDbSources) {
            migrateResolvedTenant(resolveTenantDatabaseConfig(tenantDbSource));
        }
    }

    TenantDatabaseConfig resolveTenantDatabaseConfig(String tenantKey) {
        if (flywayFactory == null) {
            throw new IllegalStateException("Business Flyway factory is not available");
        }
        return flywayFactory.resolveTenantDatabaseConfig(tenantKey);
    }

    protected void migrateResolvedTenant(TenantDatabaseConfig tenantConfig) {
        Objects.requireNonNull(tenantConfig, "tenantConfig");
        migrateDomain(tenantConfig.tenantKey(), flywayFactory.migrationPhasesForTenant(tenantConfig.tenantKey()));
    }

    private void migrateDomain(String tenantKey, List<BusinessFlywayFactory.MigrationPhase> phases) {
        for (BusinessFlywayFactory.MigrationPhase phase : phases) {
            if (phase.patchPhase() && !patchEnabled) {
                LOG.infof("Skipping patch phase for tenant db source %s because repro.flyway.patch.enabled=false",
                        tenantKey);
                continue;
            }

            LOG.infof("Migrating tenant db source=%s phase=%s locations=[%s] table=%s",
                    tenantKey, phase.name(), phase.location(), phase.historyTable());
            resolveFlyway(tenantKey, phase).migrate();
        }
    }

    protected Flyway resolveFlyway(String tenantKey, BusinessFlywayFactory.MigrationPhase phase) {
        if (flywayFactory == null) {
            throw new IllegalStateException("Business Flyway factory is not available");
        }
        return flywayFactory.resolveFlyway(tenantKey, phase);
    }

    static String normalizeTenantKey(String tenantKey) {
        return BusinessFlywayFactory.normalizeTenantKey(tenantKey);
    }

    record TenantDatabaseConfig(String tenantKey,
                                String jdbcUrl,
                                String username,
                                String password) {
        TenantDatabaseConfig {
            Objects.requireNonNull(tenantKey, "tenantKey");
            Objects.requireNonNull(jdbcUrl, "jdbcUrl");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(password, "password");
        }
    }
}
