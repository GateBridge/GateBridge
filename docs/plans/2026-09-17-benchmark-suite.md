# Benchmark Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the multi-level benchmark suite (`BenchmarkRunner`, `MetricsCollector`, protocol drivers `HttpBenchmarkClient`, `TcpBenchmarkClient`, `WsBenchmarkClient`, `TelnetBenchmarkClient`, and `scripts/benchmark.sh` CLI wrapper) in `GateBridge/GateBridge`.

**Architecture:** Create package `hexacloud.infra.benchmark` and `hexacloud.infra.benchmark.protocol`. Build Loom-virtual-thread load generators that execute production traffic through standard transport handlers without fast-path bypasses. Support `quick` (smoke) and `stress` (ramp-up loop) modes across HTTP, TCP, WS, and Telnet.

**Tech Stack:** Java 21, Project Loom Virtual Threads, LongAdder, JUnit 5, Bash.

## Global Constraints
- Package: `hexacloud.infra.benchmark`
- Protocol Package: `hexacloud.infra.benchmark.protocol`
- Fast-Path Bypass: DISABLED (Always execute through standard filters & route controllers).
- Stopping Thresholds: Error rate > 1.0% or p99 latency > 2000 ms.

---

### Task 1: Create MetricsCollector & Benchmark Drivers

**Files:**
- Create: `java/src/hexacloud/infra/benchmark/MetricsCollector.java`
- Create: `java/src/hexacloud/infra/benchmark/protocol/HttpBenchmarkClient.java`
- Create: `java/src/hexacloud/infra/benchmark/protocol/TcpBenchmarkClient.java`
- Create: `java/test/hexacloud/infra/benchmark/MetricsCollectorTest.java`

**Interfaces:**
- Consumes: `hexacloud.core.utils.concurrent.ThreadManager`
- Produces: `MetricsCollector` for lock-free latency and throughput aggregation.

- [ ] **Step 1: Write `MetricsCollector.java`**

```java
package hexacloud.infra.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

public class MetricsCollector {
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder errorRequests = new LongAdder();
    private final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

    public void recordRequest(long latencyMs, boolean success) {
        totalRequests.increment();
        if (!success) {
            errorRequests.increment();
        }
        if (latencies.size() < 100_000) {
            latencies.add(latencyMs);
        }
    }

    public long getTotalRequests() {
        return totalRequests.sum();
    }

    public long getErrorRequests() {
        return errorRequests.sum();
    }

    public double getErrorPercentage() {
        long total = getTotalRequests();
        return total == 0 ? 0.0 : (double) getErrorRequests() / total * 100.0;
    }

    public long getPercentileLatency(double percentile) {
        synchronized (latencies) {
            if (latencies.isEmpty()) return 0;
            List<Long> sorted = new ArrayList<>(latencies);
            Collections.sort(sorted);
            int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
            index = Math.max(0, Math.min(index, sorted.size() - 1));
            return sorted.get(index);
        }
    }

    public void reset() {
        totalRequests.reset();
        errorRequests.reset();
        latencies.clear();
    }
}
```

- [ ] **Step 2: Write unit test in `MetricsCollectorTest.java`**

Verify percentile calculations and error percentage logic.

- [ ] **Step 3: Run `MetricsCollectorTest`**

Run: `mvn test -Dtest=MetricsCollectorTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit Task 1**

```bash
git add java/src/hexacloud/infra/benchmark/ java/test/hexacloud/infra/benchmark/
git commit -m "feat(benchmark): add MetricsCollector and protocol drivers baseline"
```

---

### Task 2: Create BenchmarkRunner Engine & Console Formatter

**Files:**
- Create: `java/src/hexacloud/infra/benchmark/BenchmarkRunner.java`
- Create: `java/test/hexacloud/infra/benchmark/BenchmarkRunnerTest.java`

- [ ] **Step 1: Write `BenchmarkRunner.java`**

Implement CLI parsing for `--mode=quick|stress`, `--protocol=http|tcp|ws|telnet|all`, and `--target=...`.
Implement `quick` mode (5s @ 100 clients) and `stress` mode (ramp-up loop `[100, 500, 1000, 2500, 5000, 10000, 25000, 50000]`).
Implement automatic stopping criteria (error > 1% or p99 > 2000ms).

- [ ] **Step 2: Write unit test in `BenchmarkRunnerTest.java`**

Verify arguments parsing and runner initialization.

- [ ] **Step 3: Run `BenchmarkRunnerTest`**

Run: `mvn test -Dtest=BenchmarkRunnerTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit Task 2**

```bash
git add java/src/hexacloud/infra/benchmark/BenchmarkRunner.java java/test/hexacloud/infra/benchmark/BenchmarkRunnerTest.java
git commit -m "feat(benchmark): implement BenchmarkRunner CLI runner and saturation loop engine"
```

---

### Task 3: Create CLI Executable Script `scripts/benchmark.sh`

**Files:**
- Create: `scripts/benchmark.sh`
- Modify: `docs/architecture.md` (add benchmark suite section)

- [ ] **Step 1: Create `scripts/benchmark.sh`**

```bash
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
```

- [ ] **Step 2: Make `scripts/benchmark.sh` executable**

Run: `chmod +x scripts/benchmark.sh`

- [ ] **Step 3: Run full verification suite**

Run: `mvn test`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit Task 3**

```bash
git add scripts/benchmark.sh docs/architecture.md
git commit -m "feat(benchmark): add scripts/benchmark.sh wrapper and document benchmark suite"
```
