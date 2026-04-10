package org.acme.flyway;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.flywaydb.database.oracle.OracleConfigurationExtension;
import org.flywaydb.database.oracle.OracleDatabaseType;

// Reflection registration covers the Oracle Flyway types themselves.
// Service-loader resources and Flyway plugin runtime initialization live under META-INF/native-image.
@RegisterForReflection(targets = {
        OracleDatabaseType.class,
        OracleConfigurationExtension.class
})
final class FlywayOracleNativeConfig {
    private FlywayOracleNativeConfig() {
    }
}
