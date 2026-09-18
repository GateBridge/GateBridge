# GateBridge Scientific Benchmark & Saturation Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a scientific, reproducible capacity & saturation benchmark suite for GateBridge with system telemetry, configurable `activeRequests` cap, 13 granular concurrency tiers, 3-run repeatability, configurable warm-up/duration/runs, goodput vs throughput metrics, status classification vs saturation curve analysis, Fast-Path comparison, and ASCII visualization charts.

**Architecture:** Create `SystemMetricsCollector` for JVM/OS CPU/RAM/GC/Thread/FD telemetry, update `UndertowHttpTransport` to support configurable `activeRequests` cap (system property `gatebridge.active.requests.cap`), enhance `MetricsCollector` for goodput, 503 cap errors, and p95/p99.9 percentiles, and upgrade `BenchmarkRunner` with configurable warm-up/duration/runs, median calculations across 3 runs, dual Fast-Path comparative mode, and ASCII chart output.

**Tech Stack:** Java 21, Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), `java.lang.management` MXBeans, JUnit 5.

## Global Constraints
- Target Package: `hexacloud.infra.benchmark`
- Client Protocol Package: `hexacloud.infra.benchmark.protocol`
- Active Requests Cap System Property: `gatebridge.active.requests.cap` (default: 1500, 0 = unlimited).
- Fast-Path Bypass: Supported in dual-mode comparison (`Fast-Path: DISABLED` vs `Fast-Path: ENABLED`).
- Concurrency Tiers: 100, 500, 1000, 2500, 5000, 7500, 10000, 12500, 15000, 17500, 20000, 22500, 25000.
- Point Health Rules: `STABLE` (error <= 1.0%, p99 <= 2000ms), `DEGRADED` (error <= 1.0%, p99 > 2000ms), `FAILED` (error > 1.0%).
- Curve Saturation Rules: Analyzes growth region, throughput plateau/inflection points, and latency knee across the full curve.

---

### Task 1: System Telemetry & Configurable Active Requests Cap

**Files:**
- Create: `java/src/hexacloud/infra/benchmark/SystemMetricsCollector.java`
- Modify: `java/src/hexacloud/infra/server/UndertowHttpTransport.java`
- Create: `test/hexacloud/infra/benchmark/SystemMetricsCollectorTest.java`

**Interfaces:**
- Consumes: `java.lang.management.ManagementFactory`, `OperatingSystemMXBean`, `MemoryMXBean`, `GarbageCollectorMXBean`, `ThreadMXBean`, `System.getProperty("gatebridge.active.requests.cap")`.
- Produces: `SystemMetricsCollector.SystemSnapshot captureSnapshot()`, `SystemMetricsCollector.EnvironmentInfo getEnvironmentInfo()`, configurable `activeRequests` cap in `UndertowHttpTransport`.

- [ ] **Step 1: Write the failing unit test**

```java
package hexacloud.infra.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SystemMetricsCollectorTest {

    @Test
    public void testEnvironmentInfoCollection() {
        SystemMetricsCollector collector = new SystemMetricsCollector();
        SystemMetricsCollector.EnvironmentInfo env = collector.getEnvironmentInfo();

        assertNotNull(env.getOsName());
        assertNotNull(env.getJavaVersion());
        assertTrue(env.getAvailableProcessors() > 0);
    }

    @Test
    public void testSnapshotSampling() {
        SystemMetricsCollector collector = new SystemMetricsCollector();
        SystemMetricsCollector.SystemSnapshot snap1 = collector.captureSnapshot();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {}
        SystemMetricsCollector.SystemSnapshot snap2 = collector.captureSnapshot();

        assertTrue(snap2.getHeapUsedMb() >= 0);
        assertTrue(snap2.getActiveThreads() > 0);
        assertNotNull(collector.computeDelta(snap1, snap2));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SystemMetricsCollectorTest`
Expected: FAIL (Cannot resolve symbol `SystemMetricsCollector`)

- [ ] **Step 3: Implement minimal SystemMetricsCollector & UndertowHttpTransport Cap Config**

1. Implement `SystemMetricsCollector.java` with OS/JVM/GC/Thread/Memory telemetry.
2. In `UndertowHttpTransport.java`:
   Read `gatebridge.active.requests.cap` system property (default `1500`). If `cap > 0`, enforce `activeRequests.incrementAndGet() <= cap`. If `cap <= 0`, allow unlimited concurrency without returning 503.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SystemMetricsCollectorTest`
Expected: PASS

- [ ] **Step 5: Commit Task 1**

```bash
git add java/src/hexacloud/infra/benchmark/SystemMetricsCollector.java java/src/hexacloud/infra/server/UndertowHttpTransport.java test/hexacloud/infra/benchmark/SystemMetricsCollectorTest.java
git commit -m "feat(benchmark): add SystemMetricsCollector and configurable activeRequests overload cap"
```

---

### Task 2: Goodput Metrics, 13 Granular Tiers & Configurable Runner

**Files:**
- Modify: `java/src/hexacloud/infra/benchmark/MetricsCollector.java`
- Modify: `java/src/hexacloud/infra/benchmark/protocol/HttpBenchmarkClient.java`
- Modify: `java/src/hexacloud/infra/benchmark/BenchmarkRunner.java`
- Modify: `test/hexacloud/infra/benchmark/BenchmarkRunnerTest.java`

**Interfaces:**
- Consumes: `MetricsCollector.recordRequest(latencyMs, statusCode, success)`, `SystemMetricsCollector`.
- Produces: 13-tier concurrency ramp, Goodput (successful RPS) vs Failed RPS, 503 cap errors, p95/p99.9 percentiles, CLI configurable warm-up/duration/runs (`--warmup`, `--duration`, `--runs`), median calculations across 3 runs, point health status (`STABLE`, `DEGRADED`, `FAILED`), curve saturation analysis.

- [ ] **Step 1: Update MetricsCollector & HttpBenchmarkClient with Goodput and 503 tracking**

In `MetricsCollector.java`:
- Add `LongAdder goodputRequests` (2xx/3xx), `LongAdder cap503Requests` (HTTP 503), `LongAdder otherErrorRequests`.
- Add `p95` and `p99.9` percentile calculation methods.

In `HttpBenchmarkClient.java`:
- Record HTTP status code and track 503 responses specifically.

- [ ] **Step 2: Update BenchmarkRunner with CLI parameters, 13 Tiers, 3 Runs & Saturation Analysis**

In `BenchmarkRunner.java`:
- CLI parsing for `--warmup=3s`, `--duration=10s`, `--runs=3`, `--cap=1500|0`.
- 13 concurrency tiers: `100, 500, 1000, 2500, 5000, 7500, 10000, 12500, 15000, 17500, 20000, 22500, 25000`.
- Continuous execution on tier failure (mark `FAILED` and proceed).
- Warm-up phase execution before recording each run.
- Execute 3 runs per tier, calculate median Goodput RPS, median p50, median p99.
- Separate Point Health Status from Saturation Curve Analysis.

- [ ] **Step 3: Update BenchmarkRunnerTest**

Run: `mvn test -Dtest=BenchmarkRunnerTest`
Expected: PASS

- [ ] **Step 4: Commit Task 2**

```bash
git add java/src/hexacloud/infra/benchmark/MetricsCollector.java java/src/hexacloud/infra/benchmark/protocol/HttpBenchmarkClient.java java/src/hexacloud/infra/benchmark/BenchmarkRunner.java test/hexacloud/infra/benchmark/BenchmarkRunnerTest.java
git commit -m "feat(benchmark): add Goodput tracking, 13 ramp tiers, 3-run median repeatability, and curve saturation analysis"
```

---

### Task 3: Fast-Path Dual Comparison & ASCII Visualization Charts

**Files:**
- Create: `java/src/hexacloud/infra/benchmark/BenchmarkChartGenerator.java`
- Modify: `java/src/hexacloud/infra/benchmark/BenchmarkRunner.java`
- Modify: `scripts/benchmark.sh`

**Interfaces:**
- Consumes: `BenchmarkResult`.
- Produces: Comparative summary table for Fast-Path OFF vs Fast-Path ON, ASCII visual charts.

- [ ] **Step 1: Create BenchmarkChartGenerator**

Implement ASCII chart generator for:
1. Clients vs Goodput (RPS)
2. Clients vs p50 Latency
3. Clients vs p99 Latency
4. Clients vs Error Rate (%)
5. Clients vs CPU %
6. Clients vs RAM (MB)

- [ ] **Step 2: Add Dual Fast-Path & Cap Experiment Modes in BenchmarkRunner**

Add CLI flags `--mode=compare-fastpath` and `--mode=compare-cap` to output side-by-side comparative tables.

- [ ] **Step 3: Update scripts/benchmark.sh**

Ensure `scripts/benchmark.sh` forwards arguments cleanly to `BenchmarkRunner`.

- [ ] **Step 4: Run full build and test suite**

Run: `mvn test`
Expected: PASS (135+ tests passing).

- [ ] **Step 5: Commit Task 3**

```bash
git add java/src/hexacloud/infra/benchmark/BenchmarkChartGenerator.java java/src/hexacloud/infra/benchmark/BenchmarkRunner.java scripts/benchmark.sh
git commit -m "feat(benchmark): add Fast-Path comparison mode, Cap experiment mode, and ASCII visualization charts"
```
