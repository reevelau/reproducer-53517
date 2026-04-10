# reproducer-53517

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev -Dquarkus.args='migrate_ddl --help'
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/reproducer-53517-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- Picocli ([guide](https://quarkus.io/guides/picocli)): Develop command line applications with Picocli

## CLI Usage

The packaged application exposes a root command with the `migrate_ddl` subcommand.
You can inspect the available options with:

```shell script
java -jar target/quarkus-app/quarkus-run.jar --help
java -jar target/quarkus-app/quarkus-run.jar migrate_ddl --help
```


## Local Oracle XE Multi-Tenant Verification

You can verify the CLI against multiple tenant schemas in one Oracle XE container by creating one user per tenant in the same `XEPDB1` database.

Start Oracle XE with the first tenant user:

```shell script
docker run -d \
  --name repro-oracle-xe \
  -p 1521:1521 \
  -e ORACLE_PASSWORD='OracleSysPass1!' \
  -e APP_USER='app_tenant_a' \
  -e APP_USER_PASSWORD='TENANT_A_PASS_123' \
  gvenzl/oracle-xe:21
```

Wait until the DB is ready to use
```shell script
docker logs -f repro-oracle-xe

#########################
DATABASE IS READY TO USE!
#########################
```

Create or reset the second tenant schema user `app_tenant_b` after the container is ready.
This example uses a shell-safe password to avoid quoting issues during local verification:

```shell script
docker exec -i repro-oracle-xe sqlplus / as sysdba <<'SQL'
ALTER SESSION SET CONTAINER = XEPDB1;
CREATE USER app_tenant_b IDENTIFIED BY "TENANT_B_PASS_123";
GRANT CONNECT, RESOURCE TO app_tenant_b;
GRANT CREATE VIEW TO app_tenant_b;
GRANT UNLIMITED TABLESPACE TO app_tenant_b;
SQL
```

If `app_tenant_b` already exists, reset its password instead:

```shell script
docker exec -i repro-oracle-xe sqlplus / as sysdba <<'SQL'
ALTER SESSION SET CONTAINER = XEPDB1;
ALTER USER app_tenant_b IDENTIFIED BY "TENANT_B_PASS_123";
GRANT CREATE VIEW TO app_tenant_b;
SQL
```

Verify that the second tenant user can log in before running Flyway:

```shell script
docker exec -i repro-oracle-xe sqlplus -L 'app_tenant_b/TENANT_B_PASS_123@XEPDB1' <<'SQL'
SELECT USER FROM dual;
EXIT
SQL
```

Package the CLI once:

```shell script
./mvnw -q -DskipTests package
```

Run one multi-tenant migration by listing both tenants in `REPRO_FLYWAY_TENANT_DB_SOURCES` and omitting `--tenant`:

```shell script
REPRO_DB_TENANT_A_URL='jdbc:oracle:thin:@//localhost:1521/XEPDB1' \
REPRO_DB_TENANT_A_USERNAME='app_tenant_a' \
REPRO_DB_TENANT_A_PASSWORD='TENANT_A_PASS_123' \
REPRO_DB_TENANT_B_URL='jdbc:oracle:thin:@//localhost:1521/XEPDB1' \
REPRO_DB_TENANT_B_USERNAME='app_tenant_b' \
REPRO_DB_TENANT_B_PASSWORD='TENANT_B_PASS_123' \
REPRO_FLYWAY_TENANT_DB_SOURCES='tenant_a,tenant_b' \
REPRO_FLYWAY_PATCH_ENABLED=true \
REPRO_FLYWAY_CUTOVER_VERSION='20260325.0000.00' \
java -jar target/quarkus-app/quarkus-run.jar migrate_ddl
```

Verify that the seeded `USER_INFO` row exists in the `tenant_a` schema:

```shell script
docker exec -i repro-oracle-xe sqlplus -L 'app_tenant_a/TENANT_A_PASS_123@//localhost:1521/XEPDB1' <<'SQL'
SET PAGESIZE 100
SET LINESIZE 200
COLUMN FIRST_NAME FORMAT A12
COLUMN LAST_NAME FORMAT A12
COLUMN AD_ACC FORMAT A12
SELECT ID, FIRST_NAME, LAST_NAME, AD_ACC, ACTIVE
FROM USER_INFO
WHERE ID = 0
  AND AD_ACC = 'system';
EXIT
SQL
```

Verify that the same seeded row exists in the `tenant_b` schema:

```shell script
docker exec -i repro-oracle-xe sqlplus -L 'app_tenant_b/TENANT_B_PASS_123@//localhost:1521/XEPDB1' <<'SQL'
SET PAGESIZE 100
SET LINESIZE 200
COLUMN FIRST_NAME FORMAT A12
COLUMN LAST_NAME FORMAT A12
COLUMN AD_ACC FORMAT A12
SELECT ID, FIRST_NAME, LAST_NAME, AD_ACC, ACTIVE
FROM USER_INFO
WHERE ID = 0
  AND AD_ACC = 'system';
EXIT
SQL
```

Expected behavior:

- The CLI resolves the tenants in order: `tenant_a`, then `tenant_b`.
- Flyway runs sequentially and stops on the first failure.
- Each tenant schema gets its own Flyway history table and migrated objects.
- A second identical run should be idempotent apart from normal Flyway "up to date" output.

Suggested negative checks:

- Remove one `REPRO_DB_TENANT_B_*` variable and confirm the command fails.
- Skip the `app_tenant_b` user creation step and confirm the `tenant_b` migration fails while `tenant_a` may already have completed.
