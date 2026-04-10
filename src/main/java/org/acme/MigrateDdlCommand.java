package org.acme;

import java.util.List;
import java.util.concurrent.Callable;

import org.eclipse.microprofile.config.Config;
import org.acme.flyway.BusinessFlywayMigrationService;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Unremovable
@Dependent
@Command(
    name = "migrate_ddl",
    description = "Run Flyway DDL migration for one or more tenant database sources.",
    mixinStandardHelpOptions = true
)
public class MigrateDdlCommand implements Callable<Integer> {

    @Inject
    BusinessFlywayMigrationService businessFlywayMigrationService;

    @Inject
    Config config;

    @Option(
        names = "--tenant",
        description = "Single tenant db source to migrate. Defaults to repro.flyway.tenant-db-sources or tenant_a."
    )
    String tenantDbSource;

    @Override
    public Integer call() {
        List<String> tenantDbSources = tenantDbSource == null
            ? TenantDbSources.resolve(config)
            : List.of(TenantDbSources.normalize(tenantDbSource));

        for (String tenant : tenantDbSources) {
            businessFlywayMigrationService.migrateTenant(tenant);
        }

        return 0;
    }
}
