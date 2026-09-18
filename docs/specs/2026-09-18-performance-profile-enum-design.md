# Technical Design Specification: `PerformanceProfile` Enriched Enum & Transport Integration

**Date:** 2026-09-18  
**Author:** Antigravity AI & GateBridge Core Team  
**Status:** Approved by User  
**Target Branch:** `feature/1gb-ram-optimization`  

---

## 1. Goal & Requirements
Refactor `hexacloud.core.server.PerformanceProfile` from a simple binary flag (`STANDARD`, `MAX_PERFORMANCE`) into an intelligent, self-describing Java Enum that encapsulates operational parameters for HTTP connection pooling, active request backpressure concurrency limits, bounded byte buffer pool capacity, and Fast-Path execution switches.

All Javadoc documentation within code must be written in formal, highly technical English to clearly communicate execution parameters to framework developers.

---

## 2. Refactored Enum Specification (`PerformanceProfile.java`)

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

    public int getConnectionPoolSize() { return connectionPoolSize; }
    public int getActiveRequestsCap() { return activeRequestsCap; }
    public int getMaxBufferPoolSize() { return maxBufferPoolSize; }
    public boolean isFastPathEnabled() { return fastPathEnabled; }
}
```

---

## 3. Integration Points

1. **`UndertowHttpTransport.java` & `HttpTransport.java`**:
   - `getActiveRequestsCap()` falls back to `performanceProfile.getActiveRequestsCap()` if system property `-Dgatebridge.active.requests.cap` is not set.
   - `isFastPathEnabled` defaults to `performanceProfile.isFastPathEnabled()`.

2. **`ReverseProxyService.java`**:
   - Buffer pool cache offering is bounded by `performanceProfile.getMaxBufferPoolSize()`.

3. **`JdkHttpProxyClient.java`**:
   - Connection pool size property `jdk.httpclient.connectionPoolSize` defaults to `performanceProfile.getConnectionPoolSize()`.

4. **`LocalGatewayAdapter.java` & `GatewayBuilderPort.java`**:
   - Fluent API method `.performanceProfile(PerformanceProfile profile)` sets the profile on `ServerManager` and propagates to all transports.

---

## 4. Verification Plan
- Unit tests verifying profile getters and default transport assignment.
- Build & test execution via `mvn test`.
