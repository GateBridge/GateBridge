# GateBridge Rigorous Benchmark & Saturation Suite Design

## Executive Summary
This design specifies a scientific, reproducible, and non-destructive benchmarking infrastructure for **GateBridge**. It provides precise measurement of gateway throughput, latency percentiles, error rates, and system resource consumption across granular concurrency steps up to 25,000 concurrent clients.

The suite distinguishes between **Stable Capacity** (SLA-compliant operation) and **Saturation Behavior** (degradation and breakdown modes), without hiding failing steps or altering core gateway semantics.

---

## 1. Architectural Diagnostics & Bottleneck Hypotheses

Based on code analysis of `UndertowHttpTransport`, `ServerManager`, `ConnectionRegistry`, and `ThreadManager`:

### Hypothesis A: Hardcoded Active Request Concurrency Cap (503 Overloaded)
- **Code Path:** `UndertowHttpTransport.java` (lines 153-167)
- **Mechanism:** `activeRequests.incrementAndGet() <= 1500` caps concurrent request dispatching to virtual threads at 1,500 active tasks.
- **Evidence:** When client concurrency exceeds 2,500+ clients with latency > 100ms, active requests exceed 1,500, triggering instant `HTTP 503 Service Unavailable - Gateway Overloaded` responses (error rate inflation).
- **Verification Strategy:** Track HTTP 503 response counts vs socket/connection errors.

### Hypothesis B: Lock Contention in Connection Lifecycle (`SecureRandom` / UUID)
- **Code Path:** `UndertowHttpTransport.processRequest` -> `new ConnectionContextImpl(UUID.randomUUID().toString(), ...)`
- **Mechanism:** Calling `UUID.randomUUID()` on every HTTP request acquires an internal `synchronized` lock on Java's `SecureRandom` instance.
- **Evidence:** Under 20,000+ RPS, hundreds of Virtual Threads contend for the single `SecureRandom` lock, leading to CPU context-switching overhead and p99 latency spikes.
- **Verification Strategy:** Record JVM lock contention and thread state via `ThreadMXBean`.

### Hypothesis C: GC Allocation Pressure in Hot Path
- **Code Path:** Per-request wrapping objects (`UndertowHttpRequestImpl`, `UndertowHttpResponseImpl`, `HttpFilterChainImpl`, `FastPrintWriter`).
- **Mechanism:** High RPS creates thousands of transient objects per second, increasing GC pause frequencies and buffer allocations.
- **Verification Strategy:** Sample `GarbageCollectorMXBean` collection counts and cumulative pause times during benchmark steps.

---

## 2. Benchmark Concurrency Ramp & Granularity

The ramp-up execution proceeds sequentially through 13 granular concurrency tiers:

```
Tier 1:  100 clients
Tier 2:  500 clients
Tier 3:  1,000 clients
Tier 4:  2,500 clients
Tier 5:  5,000 clients
Tier 6:  7,500 clients
Tier 7:  10,000 clients
Tier 8:  12,500 clients
Tier 9:  15,000 clients
Tier 10: 17,500 clients
Tier 11: 20,000 clients
Tier 12: 22,500 clients
Tier 13: 25,000 clients
```

> **Note:** If a tier fails or triggers SLA breaches, execution continues through all 13 steps (unless unrecoverable connection failure occurs) to map the complete saturation curve.

---

## 3. Classification Criteria for Load Points

Each load point is evaluated against strict, non-destructive criteria:

| Status Code | Criteria / Conditions | Description |
| :--- | :--- | :--- |
| `STABLE` | Error Rate $\le 1.0\%$ AND p99 Latency $\le 2000\text{ ms}$ | Fully SLA-compliant stable capacity. |
| `DEGRADED` | Error Rate $\le 1.0\%$ AND p99 Latency $> 2000\text{ ms}$ | Throughput maintained, but latency exceeds SLA threshold. |
| `SATURATED` | Error Rate $\le 1.0\%$, RPS plateaus/declines | Gateway reached hardware/throughput bottleneck. |
| `FAILED` | Error Rate $> 1.0\%$ | Rejection, timeout, or HTTP 5xx error rate exceeded 1%. |

---

## 4. Metrics Collection Framework

### 4.1 Client & Traffic Metrics (Per Load Point)
- `concurrent_clients`: Target concurrency (virtual thread workers).
- `total_requests`: Total requests sent during measurement window.
- `successful_requests`: Requests returning HTTP `2xx` or `3xx`.
- `failed_requests`: Requests returning HTTP `4xx`, `5xx`, timeouts, or socket errors.
- `throughput_rps`: `total_requests / measurement_duration_seconds`.
- `latency_p50`: 50th percentile latency (ms).
- `latency_p90`: 90th percentile latency (ms).
- `latency_p95`: 95th percentile latency (ms).
- `latency_p99`: 99th percentile latency (ms).
- `latency_p99_9`: 99.9th percentile latency (ms).
- `error_rate_pct`: `(failed_requests / total_requests) * 100.0`.
- `test_duration_sec`: Duration of measurement window in seconds.

### 4.2 System & Server Telemetry (`SystemMetricsCollector`)
- `cpu_utilization_pct`: Process CPU usage percentage (0.0% - 100.0% * cores).
- `ram_rss_mb`: Resident Set Size / JVM memory consumption in MB.
- `heap_used_mb`: JVM Heap Memory used in MB.
- `gc_count_delta`: Number of GC collections during step window.
- `gc_time_ms_delta`: Total GC pause time (ms) during step window.
- `active_threads`: Total live JVM threads.
- `system_load_avg`: 1-minute system load average.

---

## 5. Repeatability, Warm-up & Connection Semantics

1. **Repeatability (3 Runs per Tier):**
   - Each load tier executes 3 consecutive runs (`Run 1`, `Run 2`, `Run 3`).
   - The reported summary metrics calculate the **Mediana** across the 3 runs for RPS, p50, and p99.
2. **Warm-up & Measurement Window:**
   - **Warm-up Duration:** 3 seconds per run (discarded from metrics to stabilize JIT compiler and connection pools).
   - **Measurement Duration:** 10 seconds per run.
3. **Connection Semantics & Keep-Alive:**
   - Client workers use persistent HTTP/1.1 connections (`Keep-Alive`) with connection pooling enabled.
   - Request workers reuse open TCP sockets; connection establishment occurs primarily during the warm-up phase.

---

## 6. Environment & System Registration

The benchmark automatically logs system hardware and environment metadata:

- **CPU Model & Architecture:** e.g., AMD Ryzen / Intel Core
- **Cores & Threads:** Logical and physical CPU count
- **Total RAM:** System memory in GB
- **OS & Kernel Version:** e.g., Linux 6.x
- **Java Version & JVM:** e.g., OpenJDK 21.0.2
- **JVM Flags:** Heap settings, GC configuration
- **GateBridge Commit Hash:** Git HEAD commit ID
- **Fast-Path Status:** `ENABLED` or `DISABLED`

---

## 7. Fast-Path Comparison Protocol

The runner supports running isolated benchmark runs for:
1. `Fast-Path: DISABLED` (standard pipeline: filters, route resolution, event controllers)
2. `Fast-Path: ENABLED` (direct route handler execution bypassing standard filter overhead)

Outputs side-by-side comparison tables and delta metrics (% RPS gain, latency reduction).

---

## 8. Endpoint Validation (`GET /hello`)

Before executing benchmarks, the runner validates the target endpoint:
- **Endpoint:** `GET /hello`
- **Expected Status Code:** `200 OK`
- **Expected Content-Type:** `text/plain`
- **Expected Response Body:** `HELLO FROM MINIMAL APPLICATION ROUTE!` (or `HELLO`)
- **Payload Size:** ~38 bytes

If the endpoint returns unexpected status codes or body content, the benchmark aborts with a descriptive error.

---

## 9. Output Format & Visualization

The benchmark outputs structured markdown text and ASCII visualization charts:

```text
================================================================================
GATEBRIDGE BENCHMARK REPORT
================================================================================
Environment:
  CPU: 12-Core AMD Ryzen
  RAM: 32 GB
  OS: Linux 6.10
  Java: OpenJDK 21.0.2
  Commit: 87fc2b6

Configuration:
  Protocol: HTTP
  Endpoint: /hello
  Fast-Path: DISABLED
  Keep-Alive: ENABLED
  Warm-up: 3s
  Measurement: 10s
  Runs per point: 3

--------------------------------------------------------------------------------
CLIENTS   RUN 1 (RPS)  RUN 2 (RPS)  RUN 3 (RPS)  MEDIAN RPS   P50     P99     ERRORS   STATUS
--------------------------------------------------------------------------------
100       18,100       18,050       18,200       18,100       5ms     21ms    0.00%    STABLE
500       15,000       14,800       14,900       14,900       30ms    99ms    0.00%    STABLE
...

--------------------------------------------------------------------------------
SATURATION ANALYSIS
--------------------------------------------------------------------------------
Stable region: 100 - 10,000 clients
Degradation begins around: 12,500 clients
Observed saturation: 15,000 clients @ 22,500 RPS
Failure threshold: 25,000 clients (6.89% error rate)
Likely bottleneck: Hardcoded activeRequests cap (1500) & SecureRandom lock contention
Evidence: HTTP 503 responses and p99 latency inflation
================================================================================
```

### ASCII Visual Charts Included:
1. Concurrency vs Throughput (RPS)
2. Concurrency vs Latency (p50 & p99)
3. Concurrency vs Error Rate (%)
4. Concurrency vs System CPU %
5. Concurrency vs RAM Usage (MB)
