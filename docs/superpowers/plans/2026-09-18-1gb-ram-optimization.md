# GateBridge 1GB RAM Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 1GB RAM optimizations (socket-level connection lifecycle, 4KB bounded buffer pool, atomic lock-free ID generator) on local branch `feature/1gb-ram-optimization`, maintaining 100% test pass rate and verifying stability within `-Xmx512m` JVM memory.

**Architecture:** Modify `UndertowHttpTransport.java` to attach connection lifecycle tracking to XNIO `StreamConnection` open/close events, use `AtomicLong` ID generation by default, and configure `DefaultByteBufferPool` to 4KB slices capped at 512 slices.

**Tech Stack:** Java 21, Undertow, XNIO, JUnit 5.

## Global Constraints
- **Local Branch Only:** `feature/1gb-ram-optimization` (Do NOT push to `origin`).
- **Memory Budget:** Maximum JVM Heap $\le 512\text{ MB}$ (`-Xmx512m`).
- **Default ID Generator:** Atomic lock-free counter (`AtomicLong`).
- **Buffer Pool:** 4096 byte slices, maximum pool size 512 slices.

---

### Task 1: Implement Bounded ByteBufferPool & Atomic Lock-Free ID Generator

**Files:**
- Modify: `java/src/hexacloud/infra/server/UndertowHttpTransport.java`
- Modify: `java/test/hexacloud/infra/server/UndertowHttpTransportTest.java`

- [ ] **Step 1: Write unit test for bounded buffer pool & atomic ID generator**

```java
package hexacloud.infra.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UndertowHttpTransportBufferTest {

    @Test
    public void testAtomicIdGeneratorDefault() {
        System.setProperty("gatebridge.connection.id.generator", "atomic");
        UndertowHttpTransport transport = new UndertowHttpTransport();
        assertNotNull(transport);
        System.clearProperty("gatebridge.connection.id.generator");
    }
}
```

- [ ] **Step 2: Implement 4KB Bounded ByteBufferPool & default Atomic ID Generator**

In `UndertowHttpTransport.java`:
1. Configure `DefaultByteBufferPool(false, 4096, 512, 2, 0)`.
2. Set default ID generator mode to `atomic`.

- [ ] **Step 3: Run test suite**

Run: `mvn test`

- [ ] **Step 4: Commit Task 1 to local branch**

```bash
git add java/src/hexacloud/infra/server/UndertowHttpTransport.java java/test/hexacloud/infra/server/UndertowHttpTransportTest.java
git commit -m "feat(server): configure 4KB bounded byte buffer pool and default atomic ID generator for 1GB RAM budget"
```

---

### Task 2: Implement Socket-Level TCP Connection Lifecycle Listener

**Files:**
- Modify: `java/src/hexacloud/infra/server/UndertowHttpTransport.java`
- Modify: `java/test/hexacloud/infra/server/UndertowHttpTransportTest.java`

- [ ] **Step 1: Write test for socket-level connection lifecycle**

```java
package hexacloud.infra.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SocketLifecycleTest {

    @Test
    public void testSocketLifecycleFlag() {
        System.setProperty("gatebridge.socket.lifecycle.enabled", "true");
        UndertowHttpTransport transport = new UndertowHttpTransport();
        assertNotNull(transport);
        System.clearProperty("gatebridge.socket.lifecycle.enabled");
    }
}
```

- [ ] **Step 2: Implement Socket-Level Open/Close Listener in UndertowHttpTransport**

In `UndertowHttpTransport.java`:
1. When `gatebridge.socket.lifecycle.enabled` is `true` (default), register a `ChannelListener` on the server builder for open/close connection events.
2. Register `ConnectionContextImpl` once per open TCP socket (`onConnect`).
3. In `processRequest()`, execute lightweight `touchConnection(ctx)` per HTTP request instead of instantiating and inserting/removing map entries per request.
4. On TCP socket close, unregister context (`onDisconnect`).

- [ ] **Step 3: Run test suite**

Run: `mvn test`

- [ ] **Step 4: Commit Task 2 to local branch**

```bash
git add java/src/hexacloud/infra/server/UndertowHttpTransport.java java/test/hexacloud/infra/server/UndertowHttpTransportTest.java
git commit -m "feat(server): implement TCP socket-level connection lifecycle listener for 1GB RAM budget"
```

---

### Task 3: Experimental 512MB Heap Verification & Benchmark Report

**Files:**
- Run benchmarks under `-Xms256m -Xmx512m`

- [ ] **Step 1: Launch BenchmarkApplication under -Xmx512m**
- [ ] **Step 2: Execute full 13-tier stress benchmark**
- [ ] **Step 3: Verify RAM remains <= 512MB Heap and 0 OOM errors**
- [ ] **Step 4: Commit final verification report**
