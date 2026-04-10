package org.acme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.eclipse.microprofile.config.Config;

final class TenantDbSources {

    private static final String DEFAULT_TENANT_DB_SOURCE = "tenant_a";

    private TenantDbSources() {
    }

    static List<String> resolve(Config config) {
        String configuredTenantDbSources = config.getOptionalValue("repro.flyway.tenant-db-sources", String.class)
            .orElse("");
        List<String> parsedTenantDbSources = parse(configuredTenantDbSources);
        return parsedTenantDbSources.isEmpty() ? List.of(DEFAULT_TENANT_DB_SOURCE) : parsedTenantDbSources;
    }

    static List<String> parse(String configuredTenantDbSources) {
        if (configuredTenantDbSources == null || configuredTenantDbSources.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> normalizedTenantDbSources = Arrays.stream(configuredTenantDbSources.split(","))
            .map(String::trim)
            .filter(tenantDbSource -> !tenantDbSource.isEmpty())
            .map(TenantDbSources::normalize)
            .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        return new ArrayList<>(normalizedTenantDbSources);
    }

    static String normalize(String tenantDbSource) {
        if (tenantDbSource == null) {
            throw new IllegalArgumentException("Tenant db source must not be null");
        }

        String normalizedTenantDbSource = tenantDbSource.trim().toLowerCase(Locale.ROOT);
        if (normalizedTenantDbSource.isEmpty()) {
            throw new IllegalArgumentException("Tenant db source must not be blank");
        }

        return normalizedTenantDbSource;
    }
}
