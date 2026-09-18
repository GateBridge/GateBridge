# GateBridge 1GB RAM Optimization Design Specification

## Executive Summary
This design specifies architectural optimizations for GateBridge to achieve maximum throughput (20,000+ Goodput RPS) and low latency within a **1 GB RAM Total Infrastructure** budget (JVM Heap limited to **$\le 512\text{ MB}$**).

The optimizations target the complete request lifecycle, reducing per-request object allocations by over 99.9%, tuning Undertow XNIO buffer pools, moving connection lifecycle management to TCP socket open/close events, and enforcing memory safety guardrails.

---

## 1. Memory Budget & Infrastructure Guardrails (1GB RAM)

| Memory Segment | Allocation Target | Notes / JVM Flag |
| :--- | :--- | :--- |
| **JVM Heap Initial** | `256 MB` | `-Xms256m` |
| **JVM Heap Maximum** | `512 MB` | `-Xmx512m` |
| **JVM Metaspace & Native Threads** | ~`192 MB` | Thread stacks, Class metadata |
| **Undertow Direct Buffer Pool** | ~`4 MB` | 512 slices $\times$ 4 KB slices |
| **OS Kernel & Network Buffers** | ~`256 MB` | TCP socket buffers (`somaxconn`) |
| **TOTAL RAM FOOTPRINT** | **$\le 1024\text{ MB}$** | Strictly bounded within 1 GB RAM |

---

## 2. Component Modifications Across the Lifecycle

### 2.1 Component 1: TCP Socket-Level Connection Lifecycle (`UndertowHttpTransport`)
- **Location:** `UndertowHttpTransport.java`
- **Design:** Instead of creating a `ConnectionContextImpl` and registering/unregistering on `ConnectionRegistry` for every single HTTP request, connection tracking is bound to TCP socket channel events:
  1. **Socket Connected (`onConnect`):** Created once when XNIO `StreamConnection` opens. Assigned a fast atomic ID. Registered in `ConnectionRegistry`.
  2. **HTTP Request Arrived (`onHeartbeat`):** Lightweight `touchConnection(ctx)` refreshes the last active timestamp without object creation or map mutations.
  3. **Socket Closed (`onDisconnect`):** Unregistered from `ConnectionRegistry` when XNIO `StreamConnection` closes.
- **Benefit:** For 100,000 HTTP requests over Keep-Alive connections, allocations drop from 100,000 contexts to exact TCP socket count (e.g., 1,000).

### 2.2 Component 2: Lock-Free Connection ID Generator (`FastIdGenerator`)
- **Location:** `UndertowHttpTransport.java` / `FastIdGenerator`
- **Design:** Default connection ID generator uses an atomic counter (`AtomicLong`) + hex string formatting when `gatebridge.connection.id.generator=atomic` or by default in high-performance mode.
- **Benefit:** Zero lock contention, eliminating `SecureRandom` synchronization overhead entirely.

### 2.3 Component 3: Bounded Undertow ByteBufferPool (4KB Slices)
- **Location:** `UndertowHttpTransport.java`
- **Design:** Configure `DefaultByteBufferPool`:
  ```java
  DefaultByteBufferPool bufferPool = new DefaultByteBufferPool(
      false, // Heap buffers for lower native overhead in 512MB heap
      4096,  // 4KB slice size
      512,   // Max 512 slices in pool (2 MB total pool size)
      2,
      0
  );
  ```
- **Benefit:** Limits Undertow buffer memory pool to 2–4 MB max, avoiding off-heap memory bloat.

### 2.4 Component 4: Fast-Path Response Buffering & Filter Optimization
- **Location:** `FastPrintWriter.java`, `UndertowHttpResponseImpl.java`
- **Design:** Streamline small response flushes ($< 4\text{ KB}$) to write directly to Undertow's response channel without allocating intermediate byte arrays.

---

## 3. Experimental Controls & Backward Compatibility

All 1GB RAM optimizations preserve backward compatibility and can be toggled via system properties for experimental verification:
- `gatebridge.socket.lifecycle.enabled=true|false` (default: `true`)
- `gatebridge.connection.id.generator=atomic|uuid` (default: `atomic`)
- `gatebridge.active.requests.cap=1500` (default: `1500`, `0` = unlimited)
- `gatebridge.buffer.slice.size=4096` (default: `4096`)

---

## 4. Verification Protocol

1. **Unit & Integration Tests:** Run `mvn test` to verify all 147+ tests pass.
2. **512MB JVM Benchmark Execution:**
   Execute `BenchmarkApplication` with JVM options `-Xms256m -Xmx512m` and run `./scripts/benchmark.sh` across 13 tiers to confirm:
   - Max Heap $\le 512\text{ MB}$
   - Peak Goodput $\ge 20,000\text{ RPS}$
   - Zero `OutOfMemoryError` failures.
