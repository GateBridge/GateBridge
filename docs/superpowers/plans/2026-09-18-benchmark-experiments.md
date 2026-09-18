# GateBridge Benchmark Experiments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement experimental controls for XNIO Socket Backlog (16384), ID Generator (`-Dgatebridge.connection.id.generator=uuid|atomic`), and Connection Registry (`-Dgatebridge.connection.registry.enabled=true|false`), and run all 4 bottleneck experiments.

**Architecture:** Modify `UndertowHttpTransport.java` to read experimental system properties, add unit tests, run the benchmarks using `BenchmarkRunner`, and generate an experimental report.

**Tech Stack:** Java 21, Undertow, JUnit 5, POSIX bash.

## Global Constraints
- System Property: `gatebridge.connection.id.generator` (`uuid` | `atomic`, default `uuid`).
- System Property: `gatebridge.connection.registry.enabled` (`true` | `false`, default `true`).
- Socket Backlog: Increased to `16384` for MAX_PERFORMANCE profile.

---

### Task 1: Implement Experimental System Property Controls

**Files:**
- Modify: `java/src/hexacloud/infra/server/UndertowHttpTransport.java`
- Modify: `java/test/hexacloud/infra/server/UndertowHttpTransportTest.java`

- [ ] **Step 1: Write failing test for system property controls**

```java
package hexacloud.infra.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UndertowHttpTransportTest {

    @Test
    public void testExperimentalSystemProperties() {
        System.setProperty("gatebridge.connection.id.generator", "atomic");
        System.setProperty("gatebridge.connection.registry.enabled", "false");

        UndertowHttpTransport transport = new UndertowHttpTransport();
        assertNotNull(transport);

        System.clearProperty("gatebridge.connection.id.generator");
        System.clearProperty("gatebridge.connection.registry.enabled");
    }
}
```

- [ ] **Step 2: Run test to verify it fails/compiles**

Run: `mvn test -Dtest=UndertowHttpTransportTest`

- [ ] **Step 3: Implement system property controls in UndertowHttpTransport**

In `UndertowHttpTransport.java`:
1. Increase `org.xnio.Options.BACKLOG` to `16384`.
2. Implement ID generator switch:
   ```java
   private static final java.util.concurrent.atomic.AtomicLong CONNECTION_COUNTER = new java.util.concurrent.atomic.AtomicLong(0);

   private String generateConnectionId() {
       String mode = System.getProperty("gatebridge.connection.id.generator", "uuid");
       if ("atomic".equalsIgnoreCase(mode)) {
           return String.valueOf(CONNECTION_COUNTER.incrementAndGet());
       }
       return java.util.UUID.randomUUID().toString();
   }
   ```
3. Implement `ConnectionRegistry` bypass switch:
   ```java
   boolean registryEnabled = Boolean.parseBoolean(System.getProperty("gatebridge.connection.registry.enabled", "true"));
   if (registryEnabled && connectionRegistry != null) {
       // register context
   }
   ```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=UndertowHttpTransportTest`

- [ ] **Step 5: Commit Task 1**

```bash
git add java/src/hexacloud/infra/server/UndertowHttpTransport.java java/test/hexacloud/infra/server/UndertowHttpTransportTest.java
git commit -m "feat(server): add experimental system property controls for backlog, ID generator, and registry bypass"
```

---

### Task 2: Execute All 4 Bottleneck Experiments & Generate Report

**Files:**
- Run benchmarks via `BenchmarkRunner` / `scripts/benchmark.sh`

- [ ] **Step 1: Execute Experimento 1 (Socket Backlog 16384)**
- [ ] **Step 2: Execute Experimento 2 (AtomicLong vs UUID)**
- [ ] **Step 3: Execute Experimento 3 (ConnectionRegistry Enabled vs Bypassed)**
- [ ] **Step 4: Execute Experimento 4 (Fast-Path Dual Comparison)**
- [ ] **Step 5: Synthesize Final Experimental Results**
