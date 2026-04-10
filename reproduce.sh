#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${ROOT_DIR}"

ORACLE_CONTAINER_NAME="${ORACLE_CONTAINER_NAME:-repro-oracle-xe}"
ORACLE_IMAGE="${ORACLE_IMAGE:-gvenzl/oracle-xe:21}"
ORACLE_PORT="${ORACLE_PORT:-1521}"
ORACLE_SYS_PASSWORD="${ORACLE_SYS_PASSWORD:-OracleSysPass1!}"
TENANT_A_USERNAME="${TENANT_A_USERNAME:-app_tenant_a}"
TENANT_A_PASSWORD="${TENANT_A_PASSWORD:-TENANT_A_PASS_123}"
JDBC_URL="jdbc:oracle:thin:@//localhost:${ORACLE_PORT}/XEPDB1"
EXPECTED_ERROR="No JDBC driver found to handle ${JDBC_URL}"
BUILD_LOG="target/reproduce-native-build.log"
RUN_LOG="target/reproduce-native-run.log"

require_command() {
    local command_name="$1"
    if ! command -v "${command_name}" >/dev/null 2>&1; then
        echo "Missing required command: ${command_name}" >&2
        exit 1
    fi
}

ensure_port_available() {
    local conflicting
    conflicting="$(docker ps --filter "publish=${ORACLE_PORT}" --format '{{.Names}} {{.Image}}' | grep -v "^${ORACLE_CONTAINER_NAME} " || true)"
    if [[ -n "${conflicting}" ]]; then
        if echo "${conflicting}" | grep -qi 'oracle-xe'; then
            printf '%s\n' "${conflicting}"
            return 0
        fi

        echo "Port ${ORACLE_PORT} is already in use by container(s):" >&2
        echo "${conflicting}" >&2
        echo "Stop those containers or rerun with a different ORACLE_PORT." >&2
        exit 1
    fi

    return 1
}

ensure_oracle_container() {
    if docker ps --format '{{.Names}}' | grep -qx "${ORACLE_CONTAINER_NAME}"; then
        echo "Reusing running Oracle XE container ${ORACLE_CONTAINER_NAME}"
        return
    fi

    if docker ps -a --format '{{.Names}}' | grep -qx "${ORACLE_CONTAINER_NAME}"; then
        echo "Starting existing Oracle XE container ${ORACLE_CONTAINER_NAME}"
        docker start "${ORACLE_CONTAINER_NAME}" >/dev/null
        return
    fi

    local existing_container
    existing_container="$(ensure_port_available || true)"
    if [[ -n "${existing_container}" ]]; then
        echo "Reusing existing Oracle XE container already publishing port ${ORACLE_PORT}:"
        echo "${existing_container}"
        return
    fi

    echo "Starting Oracle XE container ${ORACLE_CONTAINER_NAME}"
    docker run -d \
      --name "${ORACLE_CONTAINER_NAME}" \
      -p "${ORACLE_PORT}:1521" \
      -e "ORACLE_PASSWORD=${ORACLE_SYS_PASSWORD}" \
      -e "APP_USER=${TENANT_A_USERNAME}" \
      -e "APP_USER_PASSWORD=${TENANT_A_PASSWORD}" \
      "${ORACLE_IMAGE}" >/dev/null
}

build_native_runner() {
    mkdir -p target
    echo "Building native runner with Quarkus container build"
    ./mvnw -q -DskipTests -Dnative -Dquarkus.native.container-build=true package | tee "${BUILD_LOG}"
}

find_runner() {
    find target -maxdepth 1 -type f -name '*-runner' | head -n 1
}

run_native_reproducer() {
    local runner_path="$1"
    echo "Running ${runner_path} migrate_ddl to reproduce the native Flyway JDBC-driver failure"

    set +e
    REPRO_DB_TENANT_A_URL="${JDBC_URL}" \
    REPRO_DB_TENANT_A_USERNAME="${TENANT_A_USERNAME}" \
    REPRO_DB_TENANT_A_PASSWORD="${TENANT_A_PASSWORD}" \
    REPRO_FLYWAY_TENANT_DB_SOURCES='tenant_a' \
    REPRO_FLYWAY_PATCH_ENABLED=true \
    REPRO_FLYWAY_CUTOVER_VERSION='20260325.0000.00' \
    "${runner_path}" migrate_ddl 2>&1 | tee "${RUN_LOG}"
    local command_status=${PIPESTATUS[0]}
    set -e

    if [[ "${command_status}" -eq 0 ]]; then
        echo "Expected migrate_ddl to fail in native mode, but it exited successfully." >&2
        exit 1
    fi

    if ! grep -Fq "${EXPECTED_ERROR}" "${RUN_LOG}"; then
        echo "Native command failed, but the expected Flyway JDBC-driver error was not found." >&2
        echo "Expected to find: ${EXPECTED_ERROR}" >&2
        exit 1
    fi

    echo
    echo "Reproduced expected native failure."
    echo "Build log: ${BUILD_LOG}"
    echo "Run log:   ${RUN_LOG}"
}

main() {
    require_command bash
    require_command docker

    if [[ ! -x ./mvnw ]]; then
        echo "Missing executable Maven wrapper at ./mvnw" >&2
        exit 1
    fi

    ensure_oracle_container
    build_native_runner

    local runner_path
    runner_path="$(find_runner)"
    if [[ -z "${runner_path}" ]]; then
        echo "Native runner was not produced under target/" >&2
        exit 1
    fi

    run_native_reproducer "${runner_path}"
}

main "$@"
