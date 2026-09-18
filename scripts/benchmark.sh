#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"

NEEDS_BUILD=0
if [ ! -d "target/classes" ] || [ ! -f "target/gatebridge-1.0.0-SNAPSHOT.jar" ]; then
    NEEDS_BUILD=1
elif [ -n "$(find java/src -type f -newer target/classes 2>/dev/null)" ]; then
    NEEDS_BUILD=1
fi

if [ "$NEEDS_BUILD" -eq 1 ]; then
    echo "Building GateBridge benchmark components..."
    mvn compile -DskipTests
    if [ ! -f "target/gatebridge-1.0.0-SNAPSHOT.jar" ]; then
        mvn package -DskipTests
    fi
fi

java -cp "target/classes:target/gatebridge-1.0.0-SNAPSHOT.jar" hexacloud.infra.benchmark.BenchmarkRunner "$@"
