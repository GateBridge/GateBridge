#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"

if [ ! -f "target/gatebridge-1.0.0-SNAPSHOT.jar" ]; then
    echo "Building GateBridge JAR artifact..."
    mvn clean package -DskipTests
fi

java -cp "target/gatebridge-1.0.0-SNAPSHOT.jar:target/classes" hexacloud.infra.benchmark.BenchmarkRunner "$@"
