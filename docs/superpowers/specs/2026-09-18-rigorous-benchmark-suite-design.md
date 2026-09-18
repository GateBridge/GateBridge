# GateBridge Scientific Benchmark & Saturation Suite Design

## Executive Summary
This design specifies a scientific, reproducible, and non-destructive benchmarking infrastructure for **GateBridge**. It provides precise measurement of gateway throughput, goodput, latency percentiles, error rates, system telemetry, and saturation behavior across 13 granular concurrency tiers up to 25,000 concurrent clients.

The suite follows a strict scientific methodology:
```
OBSERVE  --->  MEASURE  --->  CORRELATE  --->  CONCLUDE
```

No architectural performance optimizations will be applied prematurely. All hypotheses will be empirically tested against baseline measurements.

---

## 1. Experimental Variables & Hypotheses Protocol

### 1.1 Variable A: Active Requests Overload Cap (`activeRequests`)
- **Location:** `UndertowHttpTransport.java` (lines 153-167)
- **Current Default:** `1500` active requests cap. Excess requests return `HTTP 503 Service Unavailable - Gateway Overloaded`.
- **Design Requirement:** Make this cap fully configurable via `gatebridge.active.requests.cap` (System property / API):
  - `CAP=1500` (Default production guardrail)
  - `CAP=3000`
  - `CAP=6000`
  - `CAP=UNLIMITED` (Cap set to 0 / disabled for raw capacity measurement)
- **Experimental Protocol:** Run a focused comparison across `1000`, `2500`, `5000`, `10000` clients under `CAP=1500` vs `CAP=UNLIMITED` to measure:
  - Total requests arriving at cap
  - Number of HTTP 503 responses generated exclusively by the cap
  - Difference in p99 latency and error rates

### 1.2 Hypothesis B: `UUID.randomUUID()` Synchronization Contention
- **Hypothesis:** Calling `UUID.randomUUID()` per HTTP request triggers internal `SecureRandom` lock contention.
- **Measurement Protocol (Do NOT optimize yet):** Measure throughput (RPS), CPU utilization %, allocation rate, GC pauses, and latency with:
  1. Standard `UUID.randomUUID()`
  2. Sequential atomic identifier (`AtomicLong` counter)
  3. Disabled connection ID generation
- **Goal:** Determine if UUID generation is a primary bottleneck or negligible overhead.

### 1.3 Hypothesis C: `ConnectionRegistry` Per-Request Tracking Overhead
- **Hypothesis:** Registering and unregistering HTTP contexts in `ConnectionRegistry` per request incurs map mutation overhead.
- **Measurement Protocol (Do NOT optimize yet):** Measure the architectural cost of:
  - `registerConnection() -> request processing -> unregisterConnection()` vs
  - Request processing bypassing `ConnectionRegistry`
- **Goal:** Document the exact latency and RPS delta attributable to connection tracking.

---

## 2. Granular Load Ramp (13 Tiers)

The benchmark executes sequentially through 13 concurrency tiers:

```
Tier 1:   100 clients
Tier 2:   500 clients
Tier 3:   1,000 clients
Tier 4:   2,500 clients
Tier 5:   5,000 clients
Tier 6:   7,500 clients
Tier 7:   10,000 clients
Tier 8:   12,500 clients
Tier 9:   15,000 clients
Tier 10:  17,500 clients
Tier 11:  20,000 clients
Tier 12:  22,500 clients
Tier 13:  25,000 clients
```

> **Execution Rule:** If a tier fails or triggers errors, the suite **does NOT stop**. It marks the point as `FAILED` and continues through all 13 tiers (unless unrecoverable connection loss occurs) to capture the full saturation curve.

---

## 3. Configurable Execution Parameters

The execution parameters are fully configurable via CLI flags:
- `--warmup 3s` (Warm-up phase per run, default: 3 seconds)
- `--duration 10s` (Measurement window per run, default: 10 seconds)
- `--runs 3` (Number of runs per load point for repeatability, default: 3)

---

## 4. Point Health Status vs Saturation Curve Analysis

The report strictly separates **Point Health Status** from **Curve Saturation Analysis**:

### 4.1 Point Health Status (Single Load Point)
- `STABLE`: Error Rate $\le 1.0\%$ AND p99 Latency $\le 2000\text{ ms}$
- `DEGRADED`: Error Rate $\le 1.0\%$, but p99 Latency $> 2000\text{ ms}$
- `FAILED`: Error Rate $> 1.0\%$

### 4.2 Curve Saturation Analysis (Whole Curve Property)
Saturation is evaluated across the curve independent of error rates:
- **Growth Region:** Throughput scales proportionally with concurrency.
- **Plateau / Inflection Region:** Throughput stops scaling or begins declining (e.g., 10k -> 12.6k RPS, 12.5k -> 12.4k RPS, 15k -> 11.8k RPS).
- **Latency Knee:** Point where p95/p99 latency increases non-linearly.

---

## 5. Metrics & Goodput Specifications

### 5.1 Goodput vs Throughput
- **Total RPS:** All completed requests per second.
- **Goodput (Successful RPS):** HTTP 2xx / 3xx requests per second.
- **Failed RPS:** HTTP 4xx, 5xx, timeouts, or connection error requests per second.
- **Cap 503 RPS:** HTTP 503 responses generated specifically by `activeRequests` cap.

### 5.2 Latency Percentiles
- Calculated over **successful responses (Goodput)** using a 1ms resolution histogram up to 10,000ms:
  - `p50`, `p90`, `p95`, `p99`, `p99.9`

### 5.3 Concurrency & Connection Semantics
- **Open TCP Connections:** Total persistent TCP sockets held open by client pool.
- **Request Concurrency:** Number of active request worker tasks executing simultaneously.
- **Requests per Connection:** Total requests served over persistent Keep-Alive connections vs connection churn.

### 5.4 System & Telemetry Metrics (`SystemMetricsCollector`)
- CPU Utilization % (Process & System)
- Resident Set Size (RSS / Memory MB)
- JVM Heap Memory Used / Committed (MB)
- Allocation Rate (MB/sec, if available from MXBean)
- GC Collection Count & Total Pause Time (ms)
- Live Threads & Peak Thread Count
- System Load Average (1-minute)
- Open File Descriptors (if readable from `/proc/self/status` or `UnixOperatingSystemMXBean`)

> Note: Any metric unavailable on the target platform is explicitly marked as `UNAVAILABLE`.

---

## 6. Environment & System Registration

The benchmark automatically logs:
- CPU Model, Cores, Threads
- Total RAM (GB)
- OS Name, Kernel Version, Architecture
- Java Version, Vendor, Virtual Thread Support
- Relevant JVM Flags (`-Xms`, `-Xmx`, GC algorithm)
- GateBridge Git Commit Hash
- Fast-Path Mode (`ENABLED` or `DISABLED`)
- Server Config (`CAP=1500` or `CAP=UNLIMITED`)

---

## 7. Fast-Path Comparison Framework

Separate, isolated benchmark runs for:
1. `Fast-Path: DISABLED` (standard pipeline)
2. `Fast-Path: ENABLED` (direct handler execution)

Outputs side-by-side comparison tables:
```text
CLIENTS | OFF GOODPUT (RPS) | OFF P99 | ON GOODPUT (RPS) | ON P99 | GOODPUT DELTA
```

---

## 8. Endpoint Validation (`GET /hello`)

Before benchmark execution:
- **Target:** `GET /hello`
- **Expected Status:** `200 OK`
- **Expected Content-Type:** `text/plain`
- **Expected Response Body:** `HELLO FROM MINIMAL APPLICATION ROUTE!` (or `HELLO`)
- **Size:** ~38 bytes

---

## 9. Output Deliverables Structure

The final output is organized into three distinct sections:

1. **Part A: Measured Benchmark Data** (Formatted table with 3 runs, median values, metrics, health status, and ASCII charts).
2. **Part B: Bottleneck Diagnostics & Empirical Evidence** (Observed bottlenecks supported by empirical metrics).
3. **Part C: Recommendations for Next Experiments** (Actionable recommendations for future optimization phases).
