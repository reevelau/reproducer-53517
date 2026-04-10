package org.acme.flyway;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

@ApplicationScoped
class BusinessFlywayFactory {
    private static final Pattern HISTORY_TABLE_SANITIZER = Pattern.compile("[^a-z0-9]+");
    private static final List<MigrationPhaseTemplate> MIGRATION_PHASE_TEMPLATES = List.of(
            new MigrationPhaseTemplate(
                    "baseline",
                    "baseline_hist",
                    "classpath:flyway-migrate/acme/0_baseline",
                    false
            ),
            new MigrationPhaseTemplate(
                    "schema_pre",
                    "pre_hist",
                    "classpath:flyway-migrate/acme/1_schema_pre",
                    false
            ),
            new MigrationPhaseTemplate(
                    "init",
                    "init_hist",
                    "classpath:flyway-migrate/acme/2_init",
                    false
            ),
            new MigrationPhaseTemplate(
                    "schema_post",
                    "post_hist",
                    "classpath:flyway-migrate/acme/3_schema_post",
                    false
            ),
            new MigrationPhaseTemplate(
                    "patch",
                    "patch_hist",
                    "classpath:flyway-migrate/acme/4_patch",
                    true
            )
    );

    @Inject
    Config config;

    @ConfigProperty(name = "repro.flyway.cutover-version", defaultValue = "20260325.0000.00")
    String cutoverVersion;

    private final ConcurrentMap<String, Flyway> flywayCache = new ConcurrentHashMap<>();

    BusinessFlywayFactory() {
    }

    BusinessFlywayFactory(Config config, String cutoverVersion) {
        this.config = config;
        this.cutoverVersion = cutoverVersion;
    }

    BusinessFlywayMigrationService.TenantDatabaseConfig resolveTenantDatabaseConfig(String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        return new BusinessFlywayMigrationService.TenantDatabaseConfig(
                normalizedTenantKey,
                getRequiredTenantConfig(normalizedTenantKey, "url"),
                getRequiredTenantConfig(normalizedTenantKey, "username"),
                getRequiredTenantConfig(normalizedTenantKey, "password")
        );
    }

    List<MigrationPhase> migrationPhasesForTenant(String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        return MIGRATION_PHASE_TEMPLATES.stream()
                .map(template -> template.forTenant(normalizedTenantKey))
                .toList();
    }

    Flyway resolveFlyway(String tenantKey, MigrationPhase phase) {
        Objects.requireNonNull(phase, "phase");
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        String cacheKey = normalizedTenantKey + ":" + phase.name();
        return flywayCache.computeIfAbsent(
                cacheKey,
                ignored -> createFlyway(resolveTenantDatabaseConfig(normalizedTenantKey), phase)
        );
    }

    static String normalizeTenantKey(String tenantKey) {
        if (tenantKey == null) {
            throw new IllegalArgumentException("Tenant db source must not be null");
        }

        String normalizedTenantKey = tenantKey.trim().toLowerCase(Locale.ROOT);
        if (normalizedTenantKey.isEmpty()) {
            throw new IllegalArgumentException("Tenant db source must not be blank");
        }
        return normalizedTenantKey;
    }

    private Flyway createFlyway(BusinessFlywayMigrationService.TenantDatabaseConfig tenantConfig, MigrationPhase phase) {
        return Flyway.configure()
                .dataSource(tenantConfig.jdbcUrl(), tenantConfig.username(), tenantConfig.password())
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion(cutoverVersion))
                .baselineDescription(tenantConfig.tenantKey() + ":" + phase.name() + ":cutover")
                .table(phase.historyTable())
                .locations(phase.location())
                .cleanDisabled(true)
                .load();
    }

    private String getRequiredTenantConfig(String tenantKey, String suffix) {
        String propertyName = "repro.db." + tenantKey + "." + suffix;
        return config.getOptionalValue(propertyName, String.class)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElseThrow(() -> new IllegalStateException(
                        "Missing required config property '" + propertyName + "' for tenant db source '" + tenantKey + "'"
                ));
    }

    private static String tenantHistoryPrefix(String tenantKey) {
        return "repro_fw_" + HISTORY_TABLE_SANITIZER.matcher(tenantKey).replaceAll("_");
    }

    record MigrationPhase(String name,
                          String historyTable,
                          String location,
                          boolean patchPhase) {
    }

    private record MigrationPhaseTemplate(String name,
                                          String historyTableSuffix,
                                          String location,
                                          boolean patchPhase) {
        private MigrationPhase forTenant(String tenantKey) {
            return new MigrationPhase(
                    name,
                    tenantHistoryPrefix(tenantKey) + "_" + historyTableSuffix,
                    location,
                    patchPhase
            );
        }
    }
}
