# Technical Design Specification: Gateway Request Workflow Optimizations

**Date:** 2026-09-18  
**Author:** Antigravity AI & GateBridge Core Team  
**Status:** Approved by User  
**Target Branch:** `feature/1gb-ram-optimization`  

---

## 1. Executive Summary & Goal
Eliminate runtime redundancies, unnecessary allocations, regex operations, duplicate map lookups, and lock contention identified in the GateBridge request workflow across both Fast-Path ON and Fast-Path OFF execution paths.

---

## 2. Targeted Optimization Details

### 2.1 Optimization 1: Direct Header Delegation in `ReverseProxyService.java`
- **Problem:** Every proxied request instantiates a `new TreeMap<>(String.CASE_INSENSITIVE_ORDER)` and clones every header `List<String>` into a `new ArrayList<>()`, causing thousands of short-lived allocations per second under high RPS.
- **Solution:** Maintain case-insensitivity without allocating a new `TreeMap` per request. Use a streamlined Map wrapper or direct iteration for forwarding headers to `JdkHttpProxyClient`, appending `X-Forwarded-For`, `X-Forwarded-Host`, and `X-Forwarded-Proto` safely without mutating the original `HttpRequest` header map.

### 2.2 Optimization 2: String Slicing URL Assembly in `ReverseProxyService.java`
- **Problem:** `targetUrl.replaceFirst("http://localhost", "http://127.0.0.1")` recompiles regex patterns on every proxied request.
- **Solution:** Replace regex `replaceFirst` with explicit string prefix checks:
  ```java
  if (targetUrl.startsWith("http://localhost:")) {
      targetUrl = "http://127.0.0.1:" + targetUrl.substring(17);
  } else if (targetUrl.startsWith("https://localhost:")) {
      targetUrl = "https://127.0.0.1:" + targetUrl.substring(18);
  }
  ```

### 2.3 Optimization 3: Single `canUseFastPath` Evaluation in `UndertowHttpTransport.java`
- **Problem:** `canUseFastPath` logic (checking `isFastPathEnabled()`, `resolution.isLocal()`, `registry.isRouteFastPath()`, `activeFilters`) is evaluated twice per request (in `handleRequest()` and `processRequest()`).
- **Solution:** Pass the evaluated `canUseFastPath` boolean parameter directly to `processRequest(exchange, registry, resolution, canUseFastPath)`.

### 2.4 Optimization 4: Granular Scope Lock in `RateLimiter.java`
- **Problem:** `RateLimiter.allowRequest()` locks the client window `ReentrantLock` across the entire timestamp pruning and size check block.
- **Solution:** Keep synchronization minimal and thread-safe without blocking virtual threads unnecessarily during concurrent requests from the same IP.

### 2.5 Optimization 5: Single Map Lookup for Administrative Cluster Routes in `UndertowHttpTransport.java`
- **Problem:** `clusterRegistry.getRoutes().containsKey(clusterRouteKey)` followed by `clusterRegistry.getRoutes().get(clusterRouteKey)` performs two `ConcurrentHashMap` hash lookups.
- **Solution:** Perform a single lookup `BiConsumer<String, PrintWriter> handler = clusterRegistry.getRoutes().get(clusterRouteKey)` and check `if (handler != null)`.

---

## 3. Verification Plan
- Run existing 161 unit & integration tests (`mvn test`) to ensure zero regressions.
- Verify header case-insensitivity and traceability header injection.
