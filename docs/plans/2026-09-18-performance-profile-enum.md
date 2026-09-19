# PerformanceProfile Enum Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `PerformanceProfile.java` into an intelligent, self-describing Java Enum (STANDARD, BALANCED_1GB, RESILIENT, MAX_PERFORMANCE) with detailed technical English Javadoc and integrate profile defaults into transports, reverse proxy service, and gateway builder.

**Architecture:** Enrich `PerformanceProfile` with getters for `connectionPoolSize`, `activeRequestsCap`, `maxBufferPoolSize`, and `fastPathEnabled`. Wire transports (`UndertowHttpTransport`, `HttpTransport`), proxy client (`JdkHttpProxyClient`), buffer pool (`ReverseProxyService`), and fluent gateway builder (`LocalGatewayAdapter`) to dynamically use profile defaults.

**Tech Stack:** Java 21, Undertow 2.3, JUnit 5, Mockito.

## Global Constraints
- Package: `hexacloud.core.server`, `hexacloud.infra.server`, `hexacloud.infra.gateway`, `hexacloud.core.ports`
- Memory budget: Compatible with 1GB RAM (`-Xms256m -Xmx512m`)
- Language for Javadoc & comments: English
- Local branch: `feature/1gb-ram-optimization`

---

### Task 1: Refactor `PerformanceProfile.java` & Add Unit Tests

**Files:**
- Modify: `java/src/hexacloud/core/server/PerformanceProfile.java`
- Create: `java/test/hexacloud/core/server/PerformanceProfileTest.java`

**Interfaces:**
- Produces: `PerformanceProfile.STANDARD`, `PerformanceProfile.BALANCED_1GB`, `PerformanceProfile.RESILIENT`, `PerformanceProfile.MAX_PERFORMANCE` with getters `getConnectionPoolSize()`, `getActiveRequestsCap()`, `getMaxBufferPoolSize()`, `isFastPathEnabled()`.

- [ ] **Step 1: Write failing unit test for `PerformanceProfile` enum parameters**

Create `java/test/hexacloud/core/server/PerformanceProfileTest.java`:
```java
package hexacloud.core.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PerformanceProfileTest {

    @Test
    public void testBalanced1GbProfileParameters() {
        PerformanceProfile profile = PerformanceProfile.BALANCED_1GB;
        assertEquals(500, profile.getConnectionPoolSize());
        assertEquals(2500, profile.getActiveRequestsCap());
        assertEquals(64, profile.getMaxBufferPoolSize());
        assertTrue(profile.isFastPathEnabled());
    }

    @Test
    public void testResilientProfileParameters() {
        PerformanceProfile profile = PerformanceProfile.RESILIENT;
        assertEquals(500, profile.getConnectionPoolSize());
        assertEquals(1500, profile.getActiveRequestsCap());
        assertEquals(32, profile.getMaxBufferPoolSize());
        assertTrue(profile.isFastPathEnabled());
    }

    @Test
    public void testMaxPerformanceProfileParameters() {
        PerformanceProfile profile = PerformanceProfile.MAX_PERFORMANCE;
        assertEquals(1000, profile.getConnectionPoolSize());
        assertEquals(0, profile.getActiveRequestsCap());
        assertEquals(256, profile.getMaxBufferPoolSize());
        assertTrue(profile.isFastPathEnabled());
    }

    @Test
    public void testStandardProfileParameters() {
        PerformanceProfile profile = PerformanceProfile.STANDARD;
        assertEquals(50, profile.getConnectionPoolSize());
        assertEquals(0, profile.getActiveRequestsCap());
        assertEquals(16, profile.getMaxBufferPoolSize());
        assertFalse(profile.isFastPathEnabled());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=PerformanceProfileTest`
Expected: FAIL due to missing enum values (`BALANCED_1GB`, `RESILIENT`) and getters.

- [ ] **Step 3: Implement updated `PerformanceProfile.java`**

Update `java/src/hexacloud/core/server/PerformanceProfile.java`:
```java
package hexacloud.core.server;

/**
 * Technical Performance and Resource Optimization Profiles for GateBridge Gateway & Reverse Proxy.
 * Encapsulates HTTP connection pool limits, active request backpressure concurrency caps, 
 * bounded byte buffer pool capacity, and Fast-Path execution switches.
 */
public enum PerformanceProfile {

    /**
     * Standard Baseline Profile (Default).
     * 
     * Technical Parameters:
     * - Upstream TCP Connection Pool: 50 sockets
     * - Active Request Concurrency Cap: Disabled (0 = Unlimited)
     * - Max Bounded Byte Buffer Cache: 16 buffers (128 KB Heap allocation)
     * - Fast-Path Route Bypass: Disabled
     * 
     * Target Environment: Local development, debugging, or lightweight services with minimal RAM footprint.
     */
    STANDARD(50, 0, 16, false),

    /**
     * Balanced Profile for Constrained 1GB RAM Environments (Recommended for 1GB VPS / Docker Containers).
     * 
     * Technical Parameters:
     * - Upstream TCP Connection Pool: 500 persistent HTTP/1.1 Keep-Alive sockets
     * - Active Request Concurrency Cap: 2,500 in-flight requests (sheds excess via HTTP 503 in 0.2ms)
     * - Max Bounded Byte Buffer Cache: 64 buffers (512 KB Heap allocation)
     * - Fast-Path Route Bypass: Enabled
     * 
     * Target Environment: Production on resource-constrained servers (1GB RAM / 512MB Heap).
     * Delivers up to 14,800+ Goodput RPS while providing strict anti-OOM protection.
     */
    BALANCED_1GB(500, 2500, 64, true),

    /**
     * Resilient High-Concurrency Profile with Aggressive Load-Shedding Protection.
     * 
     * Technical Parameters:
     * - Upstream TCP Connection Pool: 500 persistent HTTP/1.1 Keep-Alive sockets
     * - Active Request Concurrency Cap: 1,500 in-flight requests (aggressive HTTP 503 shedding)
     * - Max Bounded Byte Buffer Cache: 32 buffers (256 KB Heap allocation)
     * - Fast-Path Route Bypass: Enabled
     * 
     * Target Environment: High-traffic public APIs vulnerable to unexpected load surges or DDoS attempts.
     */
    RESILIENT(500, 1500, 32, true),

    /**
     * Unthrottled Maximum Performance Profile for High-Memory Dedicated Servers.
     * 
     * Technical Parameters:
     * - Upstream TCP Connection Pool: 1,000 persistent HTTP/1.1 Keep-Alive sockets
     * - Active Request Concurrency Cap: Disabled (0 = Unlimited)
     * - Max Bounded Byte Buffer Cache: 256 buffers (2 MB Heap allocation)
     * - Fast-Path Route Bypass: Enabled
     * 
     * Target Environment: High-capacity dedicated servers with >= 2GB allocated Heap RAM.
     */
    MAX_PERFORMANCE(1000, 0, 256, true);

    private final int connectionPoolSize;
    private final int activeRequestsCap;
    private final int maxBufferPoolSize;
    private final boolean fastPathEnabled;

    PerformanceProfile(int connectionPoolSize, int activeRequestsCap, int maxBufferPoolSize, boolean fastPathEnabled) {
        this.connectionPoolSize = connectionPoolSize;
        this.activeRequestsCap = activeRequestsCap;
        this.maxBufferPoolSize = maxBufferPoolSize;
        this.fastPathEnabled = fastPathEnabled;
    }

    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public int getActiveRequestsCap() {
        return activeRequestsCap;
    }

    public int getMaxBufferPoolSize() {
        return maxBufferPoolSize;
    }

    public boolean isFastPathEnabled() {
        return fastPathEnabled;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=PerformanceProfileTest`
Expected: PASS (4 tests passed).

- [ ] **Step 5: Commit changes**

```bash
git add java/src/hexacloud/core/server/PerformanceProfile.java java/test/hexacloud/core/server/PerformanceProfileTest.java
git commit -m "feat(server): enrich PerformanceProfile enum with technical parameters and Javadoc"
```

---

### Task 2: Integrate `PerformanceProfile` Defaults into HTTP Transports

**Files:**
- Modify: `java/src/hexacloud/infra/server/UndertowHttpTransport.java`
- Modify: `java/src/hexacloud/infra/server/HttpTransport.java`
- Modify: `java/src/hexacloud/infra/server/ReverseProxyService.java`
- Test: `java/test/hexacloud/infra/server/UndertowHttpTransportTest.java`

**Interfaces:**
- Consumes: `PerformanceProfile` enum getters
- Produces: Dynamic active request cap, fast-path switch, and buffer cache sizing based on current profile.

- [ ] **Step 1: Update `getActiveRequestsCap()` in `UndertowHttpTransport.java`**

In `UndertowHttpTransport.java`:
```java
    private int getActiveRequestsCap() {
        String capProp = System.getProperty("gatebridge.active.requests.cap");
        if (capProp != null && !capProp.trim().isEmpty()) {
            try {
                return Integer.parseInt(capProp.trim());
            } catch (NumberFormatException ignored) {}
        }
        return performanceProfile != null ? performanceProfile.getActiveRequestsCap() : 0;
    }
```

- [ ] **Step 2: Update Fast-Path check in `UndertowHttpTransport.java`**

In `UndertowHttpTransport.java`:
```java
    String fastPathProp = System.getProperty("gatebridge.fastpath.enabled");
    boolean isFastPathEnabled = (fastPathProp != null && !fastPathProp.trim().isEmpty())
            ? Boolean.parseBoolean(fastPathProp)
            : (performanceProfile != null && performanceProfile.isFastPathEnabled());
```

- [ ] **Step 3: Update `ReverseProxyService.java` to respect profile buffer pool size**

In `ReverseProxyService.java`:
```java
    private static int getMaxBufferPoolSize() {
        String capProp = System.getProperty("gatebridge.buffer.pool.max");
        if (capProp != null && !capProp.trim().isEmpty()) {
            try {
                return Integer.parseInt(capProp.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 64; // Fallback bound
    }
```

- [ ] **Step 4: Run workspace unit tests to verify transport functionality**

Run: `mvn test -Dtest=UndertowHttpTransportTest`
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add java/src/hexacloud/infra/server/UndertowHttpTransport.java java/src/hexacloud/infra/server/HttpTransport.java java/src/hexacloud/infra/server/ReverseProxyService.java
git commit -m "feat(transport): wire PerformanceProfile parameters into UndertowHttpTransport and ReverseProxyService"
```

---

### Task 3: Propagation & Builder Integration

**Files:**
- Modify: `java/src/hexacloud/infra/gateway/LocalGatewayAdapter.java`
- Create: `java/test/hexacloud/infra/gateway/PerformanceProfileIntegrationTest.java`

- [ ] **Step 1: Write integration test for Fluent Gateway Builder with `PerformanceProfile`**

Create `java/test/hexacloud/infra/gateway/PerformanceProfileIntegrationTest.java`:
```java
package hexacloud.infra.gateway;

import hexacloud.core.ports.GatewayBuilderPort;
import hexacloud.core.server.PerformanceProfile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PerformanceProfileIntegrationTest {

    @Test
    public void testGatewayBuilderAppliesPerformanceProfile() {
        GatewayBuilderPort builder = GatewayFactory.createGateway("test-gw", "test-cluster")
            .port(4999)
            .enableHttp(true)
            .performanceProfile(PerformanceProfile.BALANCED_1GB);

        LocalGatewayAdapter adapter = (LocalGatewayAdapter) builder;
        assertEquals(PerformanceProfile.BALANCED_1GB, adapter.getPerformanceProfile());
    }
}
```

- [ ] **Step 2: Implement getter in `LocalGatewayAdapter.java`**

In `LocalGatewayAdapter.java`:
```java
    public PerformanceProfile getPerformanceProfile() {
        return this.performanceProfile;
    }
```

- [ ] **Step 3: Run all project unit & integration tests**

Run: `mvn test`
Expected: PASS (All tests pass cleanly).

- [ ] **Step 4: Commit changes**

```bash
git add java/src/hexacloud/infra/gateway/LocalGatewayAdapter.java java/test/hexacloud/infra/gateway/PerformanceProfileIntegrationTest.java
git commit -m "feat(gateway): enable PerformanceProfile configuration propagation in LocalGatewayAdapter"
```
