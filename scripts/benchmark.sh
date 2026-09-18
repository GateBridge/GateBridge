#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"

NEEDS_BUILD=0
if [ ! -d "target/classes" ]; then
    NEEDS_BUILD=1
elif [ -n "$(find java/src -type f -newer target/classes 2>/dev/null)" ]; then
    NEEDS_BUILD=1
fi

if [ "$NEEDS_BUILD" -eq 1 ]; then
    echo "Building GateBridge benchmark components..."
    mvn compile -DskipTests -q
fi

exec mvn exec:java -q -Dexec.mainClass="hexacloud.infra.benchmark.BenchmarkRunner" -Dexec.args="$*"
