# GateBridge Multi-Level Benchmark Suite Design Spec

> **Date:** 2026-09-17  
> **Status:** Draft (Approved Design)  
> **Scope:** Multi-level performance and saturation benchmarking engine for `GateBridge/GateBridge`.

---

## 1. Overview & Goals

GateBridge requires a standardized, production-realistic performance benchmarking suite. Unlike static synthetic benchmarks, this suite executes against standard, un-optimized production pipelines (full `HttpFilterChainImpl`, standard `HttpRequest`/`HttpResponse` wrappers, and real transport execution) to provide genuine capacity diagnostics.

### Key Goals:
1. **Production Realism**: Disable fast-path bypasses; execute all filter chain intercepts and standard domain routing.
2. **Multi-Level Execution Modes**:
   - **`quick`**: Fast 5-second smoke benchmark with 100 concurrent connections to validate throughput & latency sanity.
   - **`stress`**: Dynamic saturation ramp-up loop escalating concurrent connections (`100 -> 500 -> 1,000 -> 2,500 -> 5,000 -> 10,000 -> 25,000 -> 50,000`) until error or latency ceilings are reached.
3. **Multi-Protocol Coverage**: Benchmark Layer 7 HTTP REST/Proxy, Layer 4 TCP Proxy tunneling, WebSocket event streaming, and Telnet command execution (`--protocol=http|tcp|ws|telnet|all`).
4. **Zero External Runtime Dependencies**: Built directly using Java 21 Project Loom virtual threads for client generation and wrapped in a simple CLI script (`scripts/benchmark.sh`).

---

## 2. Benchmark Execution Modes

### 2.1 Mode `quick` (Smoke Validation)
- **Duration**: 5 seconds per protocol.
- **Concurrency**: 100 persistent virtual client connections.
- **Purpose**: Quick CI/CD sanity check and local developer baseline verification.
- **Output**: Immediate throughput (RPS), average latency (ms), and error percentage.

### 2.2 Mode `stress` (Dynamic Saturation Ramp-Up Loop)
- **Step Scale**: `[100, 500, 1000, 2500, 5000, 10000, 25000, 50000]` concurrent clients.
- **Step Duration**: 10 seconds per concurrency tier.
- **Automatic Stopping Criteria**:
  - Error rate exceeds **1.0%** of total requests.
  - p99 Latency exceeds **2000 ms**.
  - Socket connection refusal or connection reset threshold.
- **Output**: Step-by-step capacity matrix and Maximum Stable Capacity summary.

---

## 3. Architecture & Component Decomposition

```text
scripts/benchmark.sh
   └── java -cp target/gatebridge-*.jar hexacloud.infra.benchmark.BenchmarkRunner
          ├── Mode: quick | stress
          ├── Protocol Drivers:
          │     ├── HttpBenchmarkClient (HTTP/1.1 Keep-Alive through FilterChain)
          │     ├── TcpBenchmarkClient (L4 Raw Socket Tunneling)
          │     ├── WsBenchmarkClient (WebSocket Handshake & Telemetry Push)
          │     └── TelnetBenchmarkClient (Telnet CRLF Command Exec)
          └── MetricsCollector & ReportFormatter
```

### 3.1 Components
1. **`scripts/benchmark.sh`**: Executable wrapper script managing Maven build checks, JVM arguments, and passing CLI flags (`--mode=quick|stress`, `--protocol=http|tcp|ws|telnet|all`, `--target=...`).
2. **`BenchmarkRunner.java`** (`hexacloud.infra.benchmark`): Main entrypoint orchestrating test steps, spawning Loom virtual client threads, collecting metrics, and formatting console reports.
3. **Protocol Clients** (`hexacloud.infra.benchmark.protocol`):
   - `HttpBenchmarkClient`: Issues HTTP GET/POST requests through the complete filter chain.
   - `TcpBenchmarkClient`: Opens raw TCP sockets to test L4 proxy throughput.
   - `WsBenchmarkClient`: Maintains open WebSocket connections and counts received broadcast packets.
   - `TelnetBenchmarkClient`: Issues raw Telnet commands (e.g. `PING`, `STATUS`).
4. **`MetricsCollector.java`**: Lock-free metrics collector using `LongAdder` for request/error counters and concurrent atomic histogram buckets for p50, p90, and p99 latency calculation.

---

## 4. Production Realism Rules

- **No Fast-Path Bypass**: Benchmarks MUST NOT use shortcuts or hardcoded fast-path byte writers. All requests MUST pass through `HttpFilterChainImpl`, CORS filters, and normal response writers.
- **Loom-Based Load Generators**: Client threads MUST be spawned using `ThreadManager.startVirtual(...)` to ensure low-overhead, high-concurrency client generation without OS thread exhaustion on the host machine.
- **Isolated Metrics Window**: Each step in `stress` mode resets its `MetricsCollector` at the start of the step window to prevent previous step stats from skewing the current tier.

---

## 5. Console Output Format

```text
================================================================================
           GATEBRIDGE BENCHMARK REPORT (Mode: STRESS RAMP-UP)
================================================================================
Protocol: HTTP | Fast-Path: DISABLED | Target: http://127.0.0.1:3001/v1/ping

STEP    CLIENTS    THROUGHPUT (RPS)    LATENCY p50    LATENCY p99    ERROR RATE    STATUS
--------------------------------------------------------------------------------
 1          100          15,200 req/s        1.4 ms         5.2 ms        0.00%    OK
 2          500          38,400 req/s        2.8 ms        11.5 ms        0.00%    OK
 3        1,000          64,100 req/s        4.9 ms        22.1 ms        0.00%    OK
 4        2,500          92,300 req/s        9.5 ms        48.3 ms        0.00%    OK
 5        5,000         105,400 req/s       18.2 ms       112.0 ms        0.02%    OK
 6       10,000          74,100 req/s       65.0 ms      2140.0 ms        1.45%    STOPPED (p99 > 2000ms)

--------------------------------------------------------------------------------
SUMMARY:
- Maximum Stable Capacity: 5,000 concurrent clients @ 105,400 RPS
- Bottleneck Reached: Step 6 (10,000 clients - Latency p99: 2140ms > 2000ms threshold)
================================================================================
```
