# GateBridge Rigorous Benchmark & Saturation Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a scientific, reproducible capacity & saturation benchmark suite for GateBridge with system telemetry, 13 granular concurrency tiers, 3-run repeatability, warm-up phases, status classification, Fast-Path comparison, and ASCII visualization charts.

**Architecture:** Create `SystemMetricsCollector` for JVM/OS CPU/RAM/GC/Thread telemetry, enhance `MetricsCollector` for p95 and p99.9 latency percentiles, and upgrade `BenchmarkRunner` with warm-up loops, median calculations across 3 runs, dual Fast-Path comparative mode, and ASCII chart output.

**Tech Stack:** Java 21, Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), `java.lang.management` MXBeans, JUnit 5.

## Global Constraints
- Target Package: `hexacloud.infra.benchmark`
- Client Protocol Package: `hexacloud.infra.benchmark.protocol`
- Fast-Path Bypass: Supported in dual-mode comparison (`Fast-Path: DISABLED` vs `Fast-Path: ENABLED`).
- Concurrency Tiers: 100, 500, 1000, 2500, 5000, 7500, 10000, 12500, 15000, 17500, 20000, 22500, 25000.
- Status Rules: `STABLE` (error <= 1.0%, p99 <= 2000ms), `DEGRADED` (error <= 1.0%, p99 > 2000ms), `SATURATED` (error <= 1.0%, RPS plateau), `FAILED` (error > 1.0%).

---

### Task 1: System Telemetry & Environment Collector

**Files:**
- Create: `java/src/hexacloud/infra/benchmark/SystemMetricsCollector.java`
- Create: `test/hexacloud/infra/benchmark/SystemMetricsCollectorTest.java`

**Interfaces:**
- Consumes: `java.lang.management.ManagementFactory`, `OperatingSystemMXBean`, `MemoryMXBean`, `GarbageCollectorMXBean`, `ThreadMXBean`.
- Produces: `SystemMetricsCollector.SystemSnapshot captureSnapshot()`, `SystemMetricsCollector.EnvironmentInfo getEnvironmentInfo()`.

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

- [ ] **Step 3: Implement minimal SystemMetricsCollector**

```java
package hexacloud.infra.benchmark;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

public class SystemMetricsCollector {

    public static class EnvironmentInfo {
        private final String osName;
        private final String osArch;
        private final String osVersion;
        private final int availableProcessors;
        private final String javaVersion;
        private final String javaVendor;
        private final long maxHeapMb;

        public EnvironmentInfo(String osName, String osArch, String osVersion, int availableProcessors,
                               String javaVersion, String javaVendor, long maxHeapMb) {
            this.osName = osName;
            this.osArch = osArch;
            this.osVersion = osVersion;
            this.availableProcessors = availableProcessors;
            this.javaVersion = javaVersion;
            this.javaVendor = javaVendor;
            this.maxHeapMb = maxHeapMb;
        }

        public String getOsName() { return osName; }
        public String getOsArch() { return osArch; }
        public String getOsVersion() { return osVersion; }
        public int getAvailableProcessors() { return availableProcessors; }
        public String getJavaVersion() { return javaVersion; }
        public String getJavaVendor() { return javaVendor; }
        public long getMaxHeapMb() { return maxHeapMb; }
    }

    public static class SystemSnapshot {
        private final long timestampMs;
        private final double processCpuLoad;
        private final long heapUsedMb;
        private final long totalGcCount;
        private final long totalGcTimeMs;
        private final int activeThreads;

        public SystemSnapshot(long timestampMs, double processCpuLoad, long heapUsedMb,
                              long totalGcCount, long totalGcTimeMs, int activeThreads) {
            this.timestampMs = timestampMs;
            this.processCpuLoad = processCpuLoad;
            this.heapUsedMb = heapUsedMb;
            this.totalGcCount = totalGcCount;
            this.totalGcTimeMs = totalGcTimeMs;
            this.activeThreads = activeThreads;
        }

        public long getTimestampMs() { return timestampMs; }
        public double getProcessCpuLoad() { return processCpuLoad; }
        public long getHeapUsedMb() { return heapUsedMb; }
        public long getTotalGcCount() { return totalGcCount; }
        public long getTotalGcTimeMs() { return totalGcTimeMs; }
        public int getActiveThreads() { return activeThreads; }
    }

    public static class MetricsDelta {
        private final double cpuPercent;
        private final long heapUsedMb;
        private final long gcCountDelta;
        private final long gcTimeMsDelta;
        private final int activeThreads;

        public MetricsDelta(double cpuPercent, long heapUsedMb, long gcCountDelta, long gcTimeMsDelta, int activeThreads) {
            this.cpuPercent = cpuPercent;
            this.heapUsedMb = heapUsedMb;
            this.gcCountDelta = gcCountDelta;
            this.gcTimeMsDelta = gcTimeMsDelta;
            this.activeThreads = activeThreads;
        }

        public double getCpuPercent() { return cpuPercent; }
        public long getHeapUsedMb() { return heapUsedMb; }
        public long getGcCountDelta() { return gcCountDelta; }
        public long getGcTimeMsDelta() { return gcTimeMsDelta; }
        public int getActiveThreads() { return activeThreads; }
    }

    private final OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> gcMxBeans = ManagementFactory.getGarbageCollectorMXBeans();
    private final ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();

    public EnvironmentInfo getEnvironmentInfo() {
        return new EnvironmentInfo(
                System.getProperty("os.name", "Unknown"),
                System.getProperty("os.arch", "Unknown"),
                System.getProperty("os.version", "Unknown"),
                Runtime.getRuntime().availableProcessors(),
                System.getProperty("java.version", "Unknown"),
                System.getProperty("java.vendor", "Unknown"),
                Runtime.getRuntime().maxMemory() / (1024 * 1024)
        );
    }

    public SystemSnapshot captureSnapshot() {
        long timestamp = System.currentTimeMillis();
        long heapUsed = memoryMxBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);

        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : gcMxBeans) {
            long count = gc.getCollectionCount();
            if (count > 0) gcCount += count;
            long time = gc.getCollectionTime();
            if (time > 0) gcTime += time;
        }

        int threads = threadMxBean.getThreadCount();
        double cpu = osMxBean.getSystemLoadAverage(); // Baseline CPU fallback

        return new SystemSnapshot(timestamp, cpu, heapUsed, gcCount, gcTime, threads);
    }

    public MetricsDelta computeDelta(SystemSnapshot start, SystemSnapshot end) {
        long gcCountDelta = Math.max(0, end.getTotalGcCount() - start.getTotalGcCount());
        long gcTimeMsDelta = Math.max(0, end.getTotalGcTimeMs() - start.getTotalGcTimeMs());
        double cpu = Math.max(0.0, end.getProcessCpuLoad());

        return new MetricsDelta(cpu, end.getHeapUsedMb(), gcCountDelta, gcTimeMsDelta, end.getActiveThreads());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SystemMetricsCollectorTest`
Expected: PASS

- [ ] **Step 5: Commit Task 1**

```bash
git add java/src/hexacloud/infra/benchmark/SystemMetricsCollector.java test/hexacloud/infra/benchmark/SystemMetricsCollectorTest.java
git commit -m "feat(benchmark): add SystemMetricsCollector for OS, JVM, and GC telemetry"
```

---

### Task 2: Granular Saturation Engine & Multi-Run Repeatability

**Files:**
- Modify: `java/src/hexacloud/infra/benchmark/MetricsCollector.java`
- Modify: `java/src/hexacloud/infra/benchmark/BenchmarkRunner.java`
- Modify: `test/hexacloud/infra/benchmark/BenchmarkRunnerTest.java`

**Interfaces:**
- Consumes: `MetricsCollector.getPercentileLatency(double percentile)`, `SystemMetricsCollector`.
- Produces: 13-tier concurrency ramp, p95/p99.9 percentiles, 3-run warm-up runner, classification status (`STABLE`, `DEGRADED`, `SATURATED`, `FAILED`).

- [ ] **Step 1: Update MetricsCollector with p95 and p99.9 support**

Ensure `MetricsCollector.java` supports `getPercentileLatency(double percentile)` for 95.0 and 99.9 percentiles.

- [ ] **Step 2: Update BenchmarkRunner with 13 Granular Tiers & 3 Runs**

In `BenchmarkRunner.java`:
- Define `STRESS_RAMP_UP_CLIENTS = {100, 500, 1000, 2500, 5000, 7500, 10000, 12500, 15000, 17500, 20000, 22500, 25000}`.
- Implement warm-up phase (3 seconds) prior to recording metrics for each run.
- Execute 3 runs per concurrency level.
- Calculate median RPS, median p50, median p99 across runs.
- Classify tier status: `STABLE`, `DEGRADED`, `SATURATED`, `FAILED`.

- [ ] **Step 3: Update BenchmarkRunnerTest**

Run: `mvn test -Dtest=BenchmarkRunnerTest`
Expected: PASS

- [ ] **Step 4: Commit Task 2**

```bash
git add java/src/hexacloud/infra/benchmark/MetricsCollector.java java/src/hexacloud/infra/benchmark/BenchmarkRunner.java test/hexacloud/infra/benchmark/BenchmarkRunnerTest.java
git commit -m "feat(benchmark): expand ramp-up tiers to 13 steps with 3-run median repeatability and warm-up"
```

---

### Task 3: Fast-Path Dual Comparison & ASCII Charts

**Files:**
- Create: `java/src/hexacloud/infra/benchmark/BenchmarkChartGenerator.java`
- Modify: `java/src/hexacloud/infra/benchmark/BenchmarkRunner.java`
- Modify: `scripts/benchmark.sh`

**Interfaces:**
- Consumes: `BenchmarkResult`.
- Produces: Comparative summary table for Fast-Path OFF vs Fast-Path ON, ASCII visual charts.

- [ ] **Step 1: Create BenchmarkChartGenerator**

Implement ASCII chart generator for Clients vs RPS, p50, p99, Errors, CPU, and RAM.

- [ ] **Step 2: Add Dual Fast-Path Reporting in BenchmarkRunner**

Add `--fast-path=true|false` or `--mode=compare` CLI flag to output side-by-side comparative table.

- [ ] **Step 3: Update scripts/benchmark.sh**

Ensure `scripts/benchmark.sh` passes arguments cleanly to `BenchmarkRunner`.

- [ ] **Step 4: Run full build and test suite**

Run: `mvn test`
Expected: PASS (135+ tests passing).

- [ ] **Step 5: Commit Task 3**

```bash
git add java/src/hexacloud/infra/benchmark/BenchmarkChartGenerator.java java/src/hexacloud/infra/benchmark/BenchmarkRunner.java scripts/benchmark.sh
git commit -m "feat(benchmark): add Fast-Path comparison mode and ASCII visualization charts"
```
