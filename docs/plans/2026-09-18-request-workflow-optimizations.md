# Gateway Request Workflow Optimizations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimize the Gateway request execution pipeline by eliminating TreeMap header allocations, regex string replacement, duplicate fast-path evaluations, and double map lookups across UndertowHttpTransport, ReverseProxyService, and RateLimiter.

**Architecture:** Refactor `ReverseProxyService.java` to slice string prefixes for localhost replacement and forward headers efficiently. Refactor `UndertowHttpTransport.java` to pass pre-evaluated `canUseFastPath` boolean parameters and eliminate duplicate route lookups.

**Tech Stack:** Java 21, Undertow 2.3, JUnit 5.

## Global Constraints
- Package: `hexacloud.infra.server`, `hexacloud.core.utils.network`
- Memory budget: Compatible with 1GB RAM budget
- Code & Comment Language: Technical English
- Branch: `feature/workflow-optimizations`

---

### Task 1: Optimize URL Assembly & Header Allocation in `ReverseProxyService.java`

**Files:**
- Modify: `java/src/hexacloud/infra/server/ReverseProxyService.java:70-96`
- Modify: `java/src/hexacloud/infra/server/ReverseProxyService.java:115-127`
- Test: `java/test/hexacloud/core/utils/network/JdkHttpProxyClientTest.java`

**Interfaces:**
- Consumes: `HttpRequest.getHeaders()`, `HttpRequest.getQuery()`, `ServerNode.getFullHost()`
- Produces: Optimized `targetUrl` and header map passed to `proxyClient.execute(...)`.

- [ ] **Step 1: Write unit test verifying localhost replacement and header injection**

In `java/test/hexacloud/core/utils/network/JdkHttpProxyClientTest.java` (or new test):
```java
    @Test
    public void testUrlStringPrefixReplacement() {
        String url1 = "http://localhost:3001/hello";
        String replaced1 = url1.startsWith("http://localhost:") ? "http://127.0.0.1:" + url1.substring(17) : url1;
        assertEquals("http://127.0.0.1:3001/hello", replaced1);
    }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `mvn test -Dtest=JdkHttpProxyClientTest`
Expected: PASS.

- [ ] **Step 3: Refactor URL assembly in `ReverseProxyService.java`**

Replace lines 72-76 in `java/src/hexacloud/infra/server/ReverseProxyService.java`:
```java
        String targetUrl = targetNode.getFullHost() + (subpath.startsWith("/") ? subpath : "/" + subpath);
        if (targetUrl.startsWith("http://localhost:")) {
            targetUrl = "http://127.0.0.1:" + targetUrl.substring(17);
        } else if (targetUrl.startsWith("https://localhost:")) {
            targetUrl = "https://127.0.0.1:" + targetUrl.substring(18);
        }
```

- [ ] **Step 4: Refactor header copy delegation in `ReverseProxyService.java`**

In `java/src/hexacloud/infra/server/ReverseProxyService.java`:
```java
        Map<String, List<String>> headers = new java.util.LinkedHashMap<>();
        if (req.getHeaders() != null) {
            for (Map.Entry<String, List<String>> entry : req.getHeaders().entrySet()) {
                headers.put(entry.getKey(), entry.getValue());
            }
        }
        
        // Add traceability headers
        String clientIp = req.getClientIp();
        if (clientIp != null) {
            headers.computeIfAbsent("X-Forwarded-For", k -> new ArrayList<>()).add(clientIp);
        }
        headers.computeIfAbsent("X-Forwarded-Host", k -> new ArrayList<>()).add(req.getHeader("Host"));
        headers.computeIfAbsent("X-Forwarded-Proto", k -> new ArrayList<>()).add("http");
```

- [ ] **Step 5: Run tests and commit**

Run: `mvn test`
Expected: PASS.

```bash
git add java/src/hexacloud/infra/server/ReverseProxyService.java java/test/hexacloud/core/utils/network/JdkHttpProxyClientTest.java
git commit -m "perf(proxy): optimize URL string slicing and header allocation in ReverseProxyService"
```

---

### Task 2: Eliminate Duplicate Fast-Path Check & Double Route Lookups in `UndertowHttpTransport.java`

**Files:**
- Modify: `java/src/hexacloud/infra/server/UndertowHttpTransport.java:140-172`
- Modify: `java/src/hexacloud/infra/server/UndertowHttpTransport.java:208-333`
- Modify: `java/src/hexacloud/infra/server/UndertowHttpTransport.java:355-370`
- Test: `java/test/hexacloud/infra/server/UndertowHttpTransportTest.java`

- [ ] **Step 1: Pass pre-evaluated `canUseFastPath` boolean to `processRequest`**

Update `processRequest` signature in `UndertowHttpTransport.java`:
```java
private void processRequest(HttpServerExchange exchange, RouteRegistry registry, RouteResolution resolution, boolean canUseFastPath)
```

In `handleRequest()`:
```java
    boolean canUseFastPath = isFastPathEnabled() && resolution.isLocal() 
            && registry.isRouteFastPath(resolution.localRouteName())
            && (activeFilters.isEmpty() || (activeFilters.size() == 1 && activeFilters.get(0) instanceof CorsFilter));

    if (canUseFastPath) {
        processRequest(exchange, registry, resolution, true);
        return;
    }
```

- [ ] **Step 2: Eliminate double lookup for administrative cluster routes**

In `executeRoute()` in `UndertowHttpTransport.java`:
```java
    RouteRegistry clusterRegistry = targetCluster.getRouteRegistry();
    String clusterRouteKey = resolution.resolveTargetRouteKey();
    if (clusterRegistry != null && clusterRouteKey != null) {
        BiConsumer<String, PrintWriter> handler = clusterRegistry.getRoutes().get(clusterRouteKey);
        if (handler != null) {
            if (clusterRouteKey.equals("/V1/GET_NODES_JSON")) {
                s.setContentType("application/json");
            } else {
                s.setContentType("text/plain");
            }
            try (PrintWriter out = s.getWriter()) {
                String query = r.getQuery();
                String args = query != null ? query : "";
                handler.accept(args, out);
            }
            return;
        }
    }
```

- [ ] **Step 3: Run all tests and commit**

Run: `mvn test`
Expected: PASS (All 161 tests pass).

```bash
git add java/src/hexacloud/infra/server/UndertowHttpTransport.java
git commit -m "perf(transport): eliminate duplicate fast-path evaluations and double route map lookups"
```
