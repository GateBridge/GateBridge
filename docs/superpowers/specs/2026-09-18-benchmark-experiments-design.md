# GateBridge Bottleneck Experiments Specification

## Executive Summary
This design specifies 4 targeted experimental studies on GateBridge:
1. **Socket Backlog Optimization:** Enlarge XNIO backlog to 16,384 to reduce TCP connect timeouts at high concurrency.
2. **UUID Identifier Benchmark:** Compare `UUID.randomUUID()` vs `AtomicLong` fast ID generator via system property `-Dgatebridge.connection.id.generator=uuid|atomic`.
3. **ConnectionRegistry Overhead Benchmark:** Compare per-request HTTP `ConnectionRegistry` registration via system property `-Dgatebridge.connection.registry.enabled=true|false`.
4. **Fast-Path Comparison Study:** Run dual comparative benchmark (`Fast-Path: DISABLED` vs `Fast-Path: ENABLED`).

---

## 1. Experiment 1: Socket Backlog Configuration
- **Property:** Update `UndertowHttpTransport.java` to configure XNIO socket backlog from `1024`/`8192` to `16384`.
- **Target Metric:** Rejection rate and p99 latency at 17,500, 20,000, 22,500, and 25,000 clients.

---

## 2. Experiment 2: `UUID.randomUUID()` vs `AtomicLong` ID Generator
- **System Property:** `-Dgatebridge.connection.id.generator=uuid|atomic` (default: `uuid`).
- **Implementation:**
  - `uuid`: `UUID.randomUUID().toString()`
  - `atomic`: `String.valueOf(COUNTER.incrementAndGet())`
- **Target Metrics:** Goodput RPS, p50/p99 latency, CPU %, RAM MB.

---

## 3. Experiment 3: `ConnectionRegistry` Overhead Study
- **System Property:** `-Dgatebridge.connection.registry.enabled=true|false` (default: `true`).
- **Implementation:** When `false`, HTTP request pipeline skips `registerConnection` and `unregisterConnection` on `ConnectionRegistry`.
- **Target Metrics:** Goodput RPS, p50/p99 latency, CPU %, RAM MB.

---

## 4. Experiment 4: Fast-Path OFF vs Fast-Path ON Comparative Run
- **Execution:** `./scripts/benchmark.sh --mode=compare-fastpath --target=http://127.0.0.1:4001/hello`
- **Target Output:** Comparative side-by-side table across 13 tiers with deltas for Goodput RPS and p99 latency.
