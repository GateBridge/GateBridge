#!/usr/bin/env bash
set -euo pipefail

# Change directory to the project root folder
cd "$(dirname "$0")/.."

# Run helper for GateBridge Terminal UI bootstrap
OUTDIR=${OUTDIR:-/tmp/gatebridge}
BUILD_NATIVE=${BUILD_NATIVE:-1}
JAVA_HOME=${JAVA_HOME:-}

echo "GateBridge Terminal TUI Launcher"

OSNAME=$(uname -s)
case "$OSNAME" in
    Linux*)   PLATFORM=linux ;;
    Darwin*)  PLATFORM=darwin ;;
    MINGW*|MSYS*|CYGWIN*) PLATFORM=windows ;;
    *) PLATFORM=unknown ;;
esac

find_java() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
        echo "${JAVA_HOME}/bin/java"
    else
        command -v java || true
    fi
}

find_javac() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/javac" ]; then
        echo "${JAVA_HOME}/bin/javac"
    else
        command -v javac || true
    fi
}

JAVA_BIN=$(find_java)
JAVAC_BIN=$(find_javac)

if [ -z "${JAVA_BIN}" ] || [ -z "${JAVAC_BIN}" ]; then
    echo "ERROR: java or javac not found. Please install a JDK and/or set JAVA_HOME." >&2
    exit 1
fi

# Determine JDK include dirs for JNI compilation
JDK_INCLUDE_DIR=""
if [ -n "${JAVA_HOME:-}" ]; then
    JDK_INCLUDE_DIR="${JAVA_HOME}/include"
else
    JAVA_PATH=$(${JAVA_BIN} -XshowSettings:properties -version 2>&1 | awk -F ' = ' '/java.home/ {print $2; exit}') || true
    if [ -n "${JAVA_PATH}" ]; then
        JDK_INCLUDE_DIR="${JAVA_PATH}/include"
    fi
fi

mkdir -p "${OUTDIR}"

if [ "${BUILD_NATIVE}" -ne 0 ]; then
    if command -v gcc >/dev/null 2>&1 && [ -n "${JDK_INCLUDE_DIR}" ] && [ -d "${JDK_INCLUDE_DIR}" ] && { [ "${PLATFORM}" = "linux" ] || [ "${PLATFORM}" = "darwin" ]; }; then
        JNI_OS_INCLUDE="${JDK_INCLUDE_DIR}/${PLATFORM}"
        LIB_NAME="libhexaterminal"
        OUT_LIB="java/${LIB_NAME}.so"
        if [ "${PLATFORM}" = "darwin" ]; then
            OUT_LIB="java/${LIB_NAME}.dylib"
        fi

        if [ -d "${JNI_OS_INCLUDE}" ]; then
            gcc -shared -fPIC -o "${OUT_LIB}" -I"${JDK_INCLUDE_DIR}" -I"${JNI_OS_INCLUDE}" c/hexaterminal.c || {
                echo "Warning: native build failed, continuing without native terminal library." >&2
            }
            cp -f "${OUT_LIB}" /tmp/ || true
            mkdir -p java/resources/native target/classes/native
            cp -f "${OUT_LIB}" java/resources/native/ || true
            cp -f "${OUT_LIB}" target/classes/native/ || true
        fi
    fi
fi

# Parse arguments for Java 8 target
MODE="java21"
for arg in "$@"; do
    if [ "$arg" = "java8" ] || [ "$arg" = "--java8" ]; then
        MODE="java8"
    fi
done

# Compile Java sources via Maven to handle overlays correctly
if [ "$MODE" = "java8" ]; then
    echo "Compiling GateBridge for Java 8 using Maven..."
    mvn clean compile -Pjava8
else
    echo "Compiling GateBridge for Java 21 using Maven..."
    mvn clean compile
fi

# Launch GateBridge TerminalMain TUI Bootstrap
mvn exec:java -Dexec.mainClass="hexacloud.application.TerminalMain"
