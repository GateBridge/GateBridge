# Dual-Listener Management Plane Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple administrative routes (`/v1/*`) from the primary HTTP proxy data listener into a dedicated, security-hardened `UndertowManagementTransport` running on a separate management port (`9090`).

**Architecture:** Introduce `UndertowManagementTransport` to host administrative and cluster control-plane endpoints on loopback/private interfaces (`127.0.0.1:9090`). Strip local route evaluation and administrative lookup overhead from `UndertowHttpTransport`, hardening the public Data Plane port and streamlining proxy hotpath execution.

**Tech Stack:** Java 17, Undertow HTTP Server, JUnit 5, GateBridge Core Architecture.

## Global Constraints

- Package placement: `hexacloud.infra.server` for management transport, `hexacloud.core.server` for core manager interfaces.
- System properties:
  - `gatebridge.admin.enabled`: Default `true`
  - `gatebridge.admin.host`: Default `127.0.0.1`
  - `gatebridge.admin.port`: Default `9090`
- Backward Compatibility: `UndertowHttpTransport` rejects `/v1/*` admin routes on the Data Plane port with `404 Not Found` unless `gatebridge.admin.legacy.singleport=true` is set.
- Quality Gate: All 164 existing tests plus new unit/integration tests must pass cleanly (`mvn test`).

---

### Task 1: Create `UndertowManagementTransport` for Dedicated Admin Endpoints

**Files:**
- Create: `java/src/hexacloud/infra/server/UndertowManagementTransport.java`
- Create: `java/test/hexacloud/infra/server/UndertowManagementTransportTest.java`

**Interfaces:**
- Consumes: `hexacloud.core.server.route.RouteRegistry`
- Produces: `UndertowManagementTransport` class with `start()`, `stop()`, `getPort()`, `getHost()`, `isListening()` methods.

- [ ] **Step 1: Write the failing unit test for `UndertowManagementTransport`**

```java
package hexacloud.infra.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.server.route.ClusterController;
import hexacloud.core.server.route.RouteRegistry;

public class UndertowManagementTransportTest {

    private UndertowManagementTransport managementTransport;
    private RouteRegistry routeRegistry;
    private Cluster testCluster;

    @BeforeEach
    void setUp() throws Exception {
        routeRegistry = new RouteRegistry();
        testCluster = new Cluster("test-mgmt-cluster");
        routeRegistry.registerController(new ClusterController(testCluster));

        managementTransport = new UndertowManagementTransport("127.0.0.1", 9099, routeRegistry);
        managementTransport.start();
    }

    @AfterEach
    void tearDown() {
        if (managementTransport != null) {
            managementTransport.stop();
        }
    }

    @Test
    void testManagementEndpointRespondsOnDedicatedPort() throws Exception {
        URL url = new URL("http://127.0.0.1:9099/v1/get_nodes_json");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String response = reader.readLine();
            assertNotNull(response);
            assertTrue(response.startsWith("["));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=UndertowManagementTransportTest`
Expected: Compilation failure due to missing `UndertowManagementTransport`.

- [ ] **Step 3: Implement `UndertowManagementTransport`**

```java
package hexacloud.infra.server;

import java.io.PrintWriter;
import java.util.function.BiConsumer;
import org.iojournal.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

import hexacloud.core.server.route.RouteRegistry;

public class UndertowManagementTransport {

    private final String host;
    private final int port;
    private final RouteRegistry routeRegistry;
    private io.undertow.Undertow server;
    private volatile boolean listening = false;

    public UndertowManagementTransport(String host, int port, RouteRegistry routeRegistry) {
        this.host = host != null ? host : "127.0.0.1";
        this.port = port;
        this.routeRegistry = routeRegistry;
    }

    public synchronized void start() {
        if (listening) return;

        this.server = io.undertow.Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(new ManagementHttpHandler(routeRegistry))
                .build();

        this.server.start();
        this.listening = true;
    }

    public synchronized void stop() {
        if (!listening || server == null) return;
        server.stop();
        listening = false;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public boolean isListening() { return listening; }

    private static class ManagementHttpHandler implements HttpHandler {
        private final RouteRegistry routeRegistry;

        public ManagementHttpHandler(RouteRegistry routeRegistry) {
            this.routeRegistry = routeRegistry;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            if (exchange.isInIoThread()) {
                exchange.dispatch(this);
                return;
            }

            String path = exchange.getRequestPath();
            BiConsumer<String, PrintWriter> handler = routeRegistry != null ? routeRegistry.getRoutes().get(path) : null;

            if (handler == null) {
                exchange.setStatusCode(StatusCodes.NOT_FOUND);
                exchange.getResponseSender().send("Management Route Not Found: " + path);
                return;
            }

            if ("/v1/get_nodes_json".equalsIgnoreCase(path)) {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            } else {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            }

            java.io.StringWriter sw = new java.io.StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            String query = exchange.getQueryString();
            handler.accept(query != null ? query : "", pw);
            pw.flush();

            exchange.setStatusCode(StatusCodes.OK);
            exchange.getResponseSender().send(sw.toString());
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=UndertowManagementTransportTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add java/src/hexacloud/infra/server/UndertowManagementTransport.java java/test/hexacloud/infra/server/UndertowManagementTransportTest.java
git commit -m "feat(server): introduce UndertowManagementTransport for dedicated management plane listener"
```

---

### Task 2: Strip Local Admin Route Evaluation from `UndertowHttpTransport` (Data Plane Hardening)

**Files:**
- Modify: `java/src/hexacloud/infra/server/UndertowHttpTransport.java:342-376`
- Modify: `java/test/hexacloud/infra/server/UndertowHttpTransportTest.java`

**Interfaces:**
- Consumes: `gatebridge.admin.legacy.singleport` system property.
- Produces: Hardened `executeRoute()` method in `UndertowHttpTransport` that forwards proxy requests without local administrative branching.

- [ ] **Step 1: Write unit test verifying Data Plane rejects admin routes**

Add test to `UndertowHttpTransportTest.java`:

```java
@Test
void testDataPlaneRejectsAdminRoutesInDualListenerMode() throws Exception {
    URL url = new URL("http://127.0.0.1:" + testPort + "/v1/get_nodes_json");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    assertEquals(404, conn.getResponseCode());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=UndertowHttpTransportTest#testDataPlaneRejectsAdminRoutesInDualListenerMode`
Expected: FAIL (returns 200 currently because of legacy local routing).

- [ ] **Step 3: Modify `UndertowHttpTransport.java` to bypass local admin routes in Data Plane**

In `UndertowHttpTransport.java`, update `executeRoute`:

```java
    private void executeRoute(HttpRequest r, HttpResponse s, RouteResolution resolution, RouteRegistry registry) throws Exception {
        boolean allowLegacySinglePortAdmin = Boolean.getBoolean("gatebridge.admin.legacy.singleport");

        if (resolution.isProxy()) {
            Cluster targetCluster = ClusterRegistry.getInstance().getCluster(resolution.targetClusterName());
            if (targetCluster == null) {
                errorHandler.handleStatus(s, 404, "Unknown Cluster: " + resolution.targetClusterName());
                return;
            }

            if (allowLegacySinglePortAdmin) {
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
            }

            reverseProxyService.proxyRequest(r, s, targetCluster, resolution.targetSubpath(), targetCluster.getTimeoutMs(), resolution.matchedRouteRule());

        } else if (resolution.isLocal()) {
            if (allowLegacySinglePortAdmin) {
                BiConsumer<String, PrintWriter> handler = registry.getRoutes().get(resolution.localRouteName());
                if (handler != null) {
                    if (resolution.localRouteName().equals("/V1/GET_NODES_JSON")) {
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
            errorHandler.handleStatus(s, 404, "Management Endpoints Disabled on Data Port");
        } else {
            errorHandler.handleStatus(s, 404, "Route Not Found");
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=UndertowHttpTransportTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add java/src/hexacloud/infra/server/UndertowHttpTransport.java java/test/hexacloud/infra/server/UndertowHttpTransportTest.java
git commit -m "refactor(transport): harden Data Plane by disabling local admin route branching on public proxy port"
```

---

### Task 3: Integrate Management Plane Lifecycle into `ServerManager` and `GatewayBuilderPort`

**Files:**
- Modify: `java/src/hexacloud/core/server/ServerManager.java`
- Modify: `java/src/hexacloud/core/ports/GatewayBuilderPort.java`
- Modify: `java/src/hexacloud/infra/gateway/LocalGatewayAdapter.java`
- Create: `java/test/hexacloud/infra/gateway/DualListenerIntegrationTest.java`

**Interfaces:**
- Consumes: System properties `gatebridge.admin.enabled`, `gatebridge.admin.host`, `gatebridge.admin.port`.
- Produces: `.adminPort(int port)` and `.adminHost(String host)` builder methods and lifecycle integration in `ServerManager`.

- [ ] **Step 1: Write integration test verifying Dual-Listener operation**

```java
package hexacloud.infra.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DualListenerIntegrationTest {

    private LocalGatewayAdapter gateway;
    private final int dataPort = 4091;
    private final int adminPort = 9091;

    @BeforeEach
    void setUp() {
        gateway = LocalGatewayAdapter.builder()
                .port(dataPort)
                .adminPort(adminPort)
                .adminHost("127.0.0.1")
                .build();
        gateway.start();
    }

    @AfterEach
    void tearDown() {
        if (gateway != null) {
            gateway.stop();
        }
    }

    @Test
    void testAdminEndpointAccessibleOnAdminPortAndBlockedOnDataPort() throws Exception {
        // Admin Port check -> 200 OK
        URL adminUrl = new URL("http://127.0.0.1:" + adminPort + "/v1/get_nodes_json");
        HttpURLConnection adminConn = (HttpURLConnection) adminUrl.openConnection();
        adminConn.setRequestMethod("GET");
        assertEquals(200, adminConn.getResponseCode());

        // Data Port check -> 404 Not Found
        URL dataUrl = new URL("http://127.0.0.1:" + dataPort + "/v1/get_nodes_json");
        HttpURLConnection dataConn = (HttpURLConnection) dataUrl.openConnection();
        dataConn.setRequestMethod("GET");
        assertEquals(404, dataConn.getResponseCode());
    }
}
```

- [ ] **Step 2: Run integration test to verify it fails**

Run: `mvn test -Dtest=DualListenerIntegrationTest`
Expected: Compilation failure due to missing `adminPort()` builder methods.

- [ ] **Step 3: Update `GatewayBuilderPort`, `LocalGatewayAdapter`, and `ServerManager`**

Update `GatewayBuilderPort.java` interface:
```java
GatewayBuilderPort adminPort(int port);
GatewayBuilderPort adminHost(String host);
```

Update `LocalGatewayAdapter.java`:
Add `adminPort` and `adminHost` fields and builder setters. Forward `adminPort` and `adminHost` to `ServerManager`.

Update `ServerManager.java`:
Initialize and manage `UndertowManagementTransport` lifecycle alongside `UndertowHttpTransport`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DualListenerIntegrationTest`
Expected: PASS.

- [ ] **Step 5: Run full regression test suite**

Run: `mvn test`
Expected: 100% tests PASS across all suites.

- [ ] **Step 6: Commit**

```bash
git add java/src/hexacloud/core/server/ServerManager.java java/src/hexacloud/core/ports/GatewayBuilderPort.java java/src/hexacloud/infra/gateway/LocalGatewayAdapter.java java/test/hexacloud/infra/gateway/DualListenerIntegrationTest.java
git commit -m "feat(gateway): integrate Dual-Listener architecture in ServerManager and LocalGatewayAdapter"
```
