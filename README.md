# reproducer-53517

This repo reproduces a Quarkus native-image failure when `migrate_ddl` runs Flyway against an Oracle JDBC URL.

The native runner is expected to fail with:

```text
[org.acme.flyway.BusinessFlywayMigrationService] (main) Migrating tenant db source=tenant_a phase=baseline locations=[classpath:flyway-migrate/acme/0_baseline] table=repro_fw_tenant_a_baseline_hist
org.flywaydb.core.api.FlywayException: No JDBC driver found to handle jdbc:oracle:thin:@//localhost:1521/XEPDB1. See https://rd.gt/423f9a6 for troubleshooting
```

## Requirements

- Linux or macOS with `bash`
- Docker with a running daemon and permission to use it
- Java 21 to run `./mvnw`
- Internet access to pull:
  - `gvenzl/oracle-xe:21`
  - `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`
- Enough memory for native-image container builds
  - Expect several GB of RAM usage
  - Expect the native build to take multiple minutes

Notes:

- Local GraalVM is not required. The script uses Quarkus container-based native builds.
- The reproduction uses `localhost:1521` by default.
- The Oracle container is started to match the intended runtime setup, but the failure happens before Flyway successfully opens a JDBC connection.

## Quick Start

Run:

```bash
./reproduce.sh
```

The script will:

1. Start or reuse an Oracle XE container named `repro-oracle-xe`
2. Build the app in native mode with `-Dquarkus.native.container-build=true`
3. Run the native executable with `migrate_ddl`
4. Assert that the expected Flyway JDBC-driver error appears

If the expected failure is reproduced, the script exits successfully and prints the log file locations.
If the native command succeeds or fails for a different reason, the script exits with a non-zero status.

## Expected Result

During the native run, you should see output like:

```text
123' REPRO_DB_TENANT_B_URL='jdbc:oracle:thin:@//localhost:1521/XEPDB1' REPRO_DB_TENANT_B_USERNAME='app_tenant_b' REPRO_DB_TENANT_B_PASSWORD='TENANT_B_PASS_123' REPRO_FLYWAY_TENANT_DB_SOURCES='tenant_a,tenant_b' REPRO_FLYWAY_PATCH_ENABLED=true REPRO_FLYWAY_CUTOVER_VERSION='20260325.0000.00' ./target/reproducer-53517-1.0.0-SNAPSHOT-runner migrate_ddl
__  ____  __  _____   ___  __ ____  ______
 --/ __ \/ / / / _ | / _ \/ //_/ / / / __/
 -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \
--\___\_\____/_/ |_/_/|_/_/|_|\____/___/
2026-04-10 13:57:07,532 INFO  [org.acme.flyway.BusinessFlywayMigrationService] (main) Business Flyway migration is disabled by configuration.
2026-04-10 13:57:07,532 INFO  [io.quarkus] (main) reproducer-53517 1.0.0-SNAPSHOT native (powered by Quarkus 3.34.3) started in 0.013s.
2026-04-10 13:57:07,532 INFO  [io.quarkus] (main) Profile prod activated.
2026-04-10 13:57:07,532 INFO  [io.quarkus] (main) Installed features: [agroal, cdi, flyway, jdbc-oracle, narayana-jta, picocli, smallrye-context-propagation]
2026-04-10 13:57:07,534 INFO  [org.acme.flyway.BusinessFlywayMigrationService] (main) Starting on-demand business Flyway migration with cutover version 20260325.0000.00 for tenant db source tenant_a
2026-04-10 13:57:07,534 INFO  [org.acme.flyway.BusinessFlywayMigrationService] (main) Migrating tenant db source=tenant_a phase=baseline locations=[classpath:flyway-migrate/acme/0_baseline] table=repro_fw_tenant_a_baseline_hist
org.flywaydb.core.api.FlywayException: No JDBC driver found to handle jdbc:oracle:thin:@//localhost:1521/XEPDB1. See https://rd.gt/423f9a6 for troubleshooting
        at org.flywaydb.core.internal.jdbc.DriverDataSource.<init>(DriverDataSource.java:165)
        at org.flywaydb.core.internal.jdbc.DriverDataSource.<init>(DriverDataSource.java:97)
        at org.flywaydb.core.api.configuration.ClassicConfiguration.setDataSource(ClassicConfiguration.java:1528)
        at org.flywaydb.core.api.configuration.FluentConfiguration.dataSource(FluentConfiguration.java:656)
        at org.acme.flyway.BusinessFlywayFactory.createFlyway(BusinessFlywayFactory.java:110)
        at org.acme.flyway.BusinessFlywayFactory.lambda$resolveFlyway$1(BusinessFlywayFactory.java:92)
        at java.base@21.0.6/java.util.concurrent.ConcurrentHashMap.computeIfAbsent(ConcurrentHashMap.java:1708)
        at org.acme.flyway.BusinessFlywayFactory.resolveFlyway(BusinessFlywayFactory.java:90)
        at org.acme.flyway.BusinessFlywayFactory_ClientProxy.resolveFlyway(Unknown Source)
        at org.acme.flyway.BusinessFlywayMigrationService.resolveFlyway(BusinessFlywayMigrationService.java:134)
        at org.acme.flyway.BusinessFlywayMigrationService.migrateDomain(BusinessFlywayMigrationService.java:126)
        at org.acme.flyway.BusinessFlywayMigrationService.migrateResolvedTenant(BusinessFlywayMigrationService.java:113)
        at org.acme.flyway.BusinessFlywayMigrationService.migrateTenant(BusinessFlywayMigrationService.java:71)
        at org.acme.flyway.BusinessFlywayMigrationService_ClientProxy.migrateTenant(Unknown Source)
        at org.acme.MigrateDdlCommand.call(MigrateDdlCommand.java:42)
        at org.acme.MigrateDdlCommand.call(MigrateDdlCommand.java:14)
        at picocli.CommandLine.executeUserObject(CommandLine.java:2031)
        at picocli.CommandLine.access$1500(CommandLine.java:148)
        at picocli.CommandLine$RunLast.executeUserObjectOfLastSubcommandWithSameParent(CommandLine.java:2469)
        at picocli.CommandLine$RunLast.handle(CommandLine.java:2461)
        at picocli.CommandLine$RunLast.handle(CommandLine.java:2423)
        at picocli.CommandLine$AbstractParseResultHandler.execute(CommandLine.java:2277)
        at picocli.CommandLine$RunLast.execute(CommandLine.java:2425)
        at io.quarkus.picocli.runtime.PicocliRunner$EventExecutionStrategy.execute(PicocliRunner.java:26)
        at picocli.CommandLine.execute(CommandLine.java:2174)
        at io.quarkus.picocli.runtime.PicocliRunner.run(PicocliRunner.java:40)
        at io.quarkus.runtime.ApplicationLifecycleManager.run(ApplicationLifecycleManager.java:149)
        at io.quarkus.runtime.Quarkus.run(Quarkus.java:79)
        at io.quarkus.runtime.Quarkus.run(Quarkus.java:50)
        at io.quarkus.runner.GeneratedMain.main(Unknown Source)
        at java.base@21.0.6/java.lang.invoke.LambdaForm$DMH/sa346b79c.invokeStaticInit(LambdaForm$DMH)
```

The script writes:

- native build output to `target/reproduce-native-build.log`
- native run output to `target/reproduce-native-run.log`

## Manual Path

If you want to run the steps manually, use:

```bash
docker run -d \
  --name repro-oracle-xe \
  -p 1521:1521 \
  -e ORACLE_PASSWORD='OracleSysPass1!' \
  -e APP_USER='app_tenant_a' \
  -e APP_USER_PASSWORD='TENANT_A_PASS_123' \
  gvenzl/oracle-xe:21
```

```bash
./mvnw -q -DskipTests -Dnative -Dquarkus.native.container-build=true package
```

```bash
REPRO_DB_TENANT_A_URL='jdbc:oracle:thin:@//localhost:1521/XEPDB1' \
REPRO_DB_TENANT_A_USERNAME='app_tenant_a' \
REPRO_DB_TENANT_A_PASSWORD='TENANT_A_PASS_123' \
REPRO_FLYWAY_TENANT_DB_SOURCES='tenant_a' \
REPRO_FLYWAY_PATCH_ENABLED=true \
REPRO_FLYWAY_CUTOVER_VERSION='20260325.0000.00' \
./target/reproducer-53517-1.0.0-SNAPSHOT-runner migrate_ddl
```

## Cleanup

To remove the Oracle XE container created for the reproduction:

```bash
docker rm -f repro-oracle-xe
```
