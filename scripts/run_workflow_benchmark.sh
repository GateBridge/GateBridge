#!/usr/bin/env bash
set -e

# 1. Kill any existing server processes on ports 3001, 4000, 4001, 4002
fuser -k -9 3001/tcp 4000/tcp 4001/tcp 4002/tcp || true
sleep 1

mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt -q
CP="target/classes:$(cat classpath.txt)"

# 2. Recompile application classes directly
javac -cp "$CP" -d target/classes java/src/hexacloud/application/*.java

# 3. Start Upstream Mock Server
nohup java -Xms32m -Xmx64m -cp "$CP" hexacloud.application.UpstreamMockServer > upstream.log 2>&1 &
UPSTREAM_PID=$!
sleep 1

# 4. Start Proxy Gateway Application with Active Request Cap 2500
nohup java -Xms256m -Xmx512m -Dgatebridge.active.requests.cap=2500 -cp "$CP" hexacloud.application.ProxyBenchmarkApplication > proxy.log 2>&1 &
PROXY_PID=$!
sleep 2

# 5. Verify HTTP 200 OK from Proxy Endpoint
echo "--- Verifying Proxy Route Endpoint ---"
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:4001/proxy/hello

# 6. Run 13-tier Stress Benchmark
echo "--- Executing Workflow Optimized 13-Tier Stress Benchmark ---"
java -cp "$CP" hexacloud.infra.benchmark.BenchmarkRunner --mode=stress --protocol=http --target=http://127.0.0.1:4001/proxy/hello --warmup=2s --duration=3s --runs=1 --cap=2500

# 7. Cleanup Background Processes
kill -9 $UPSTREAM_PID $PROXY_PID 2>/dev/null || true
fuser -k -9 3001/tcp 4000/tcp 4001/tcp 4002/tcp || true
