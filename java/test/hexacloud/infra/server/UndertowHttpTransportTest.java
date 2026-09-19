package hexacloud.infra.server;

import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import hexacloud.core.server.connection.ConnectionContext;
import hexacloud.core.server.connection.ConnectionLifecycleListener;
import hexacloud.core.server.connection.ConnectionRegistry;
import hexacloud.core.server.route.RouteRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class UndertowHttpTransportTest {

    private String originalGeneratorProp;
    private String originalLifecycleProp;
    private String originalSinglePortProp;

    @BeforeEach
    public void setUp() {
        originalGeneratorProp = System.getProperty("gatebridge.connection.id.generator");
        originalLifecycleProp = System.getProperty("gatebridge.socket.lifecycle.enabled");
        originalSinglePortProp = System.getProperty("gatebridge.admin.legacy.singleport");
    }

    @AfterEach
    public void tearDown() {
        if (originalGeneratorProp != null) {
            System.setProperty("gatebridge.connection.id.generator", originalGeneratorProp);
        } else {
            System.clearProperty("gatebridge.connection.id.generator");
        }

        if (originalLifecycleProp != null) {
            System.setProperty("gatebridge.socket.lifecycle.enabled", originalLifecycleProp);
        } else {
            System.clearProperty("gatebridge.socket.lifecycle.enabled");
        }

        if (originalSinglePortProp != null) {
            System.setProperty("gatebridge.admin.legacy.singleport", originalSinglePortProp);
        } else {
            System.clearProperty("gatebridge.admin.legacy.singleport");
        }
    }

    private int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    @Test
    public void testDefaultConnectionIdGeneratorIsAtomic() {
        System.clearProperty("gatebridge.connection.id.generator");
        UndertowHttpTransport transport = new UndertowHttpTransport();
        String id1 = transport.generateConnectionId();
        String id2 = transport.generateConnectionId();
        
        assertNotNull(id1);
        assertNotNull(id2);
        // Atomic counter returns numeric strings
        assertTrue(id1.matches("\\d+"), "Default connection ID should be numeric atomic counter, got: " + id1);
        assertTrue(id2.matches("\\d+"), "Default connection ID should be numeric atomic counter, got: " + id2);
        assertNotEquals(id1, id2, "Connection IDs should increment");
    }

    @Test
    public void testExplicitAtomicConnectionIdGenerator() {
        System.setProperty("gatebridge.connection.id.generator", "atomic");
        UndertowHttpTransport transport = new UndertowHttpTransport();
        String id = transport.generateConnectionId();
        
        assertNotNull(id);
        assertTrue(id.matches("\\d+"), "Connection ID when 'atomic' should be numeric, got: " + id);
    }

    @Test
    public void testExplicitUuidConnectionIdGenerator() {
        System.setProperty("gatebridge.connection.id.generator", "uuid");
        UndertowHttpTransport transport = new UndertowHttpTransport();
        String id = transport.generateConnectionId();
        
        assertNotNull(id);
        // UUID regex pattern
        assertTrue(id.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"),
                "Connection ID when 'uuid' should be UUID format, got: " + id);
    }

    @Test
    public void testByteBufferPoolConfiguration() {
        UndertowHttpTransport transport = new UndertowHttpTransport();
        ByteBufferPool pool = transport.createByteBufferPool();
        
        assertNotNull(pool);
        assertTrue(pool instanceof DefaultByteBufferPool, "Pool should be an instance of DefaultByteBufferPool");
        DefaultByteBufferPool defaultPool = (DefaultByteBufferPool) pool;
        assertEquals(4096, defaultPool.getBufferSize(), "ByteBufferPool slice size should be 4096 bytes");
        assertFalse(defaultPool.isDirect(), "ByteBufferPool should use heap buffers (direct=false)");
    }

    @Test
    public void testSocketLevelConnectionLifecycle() throws Exception {
        System.setProperty("gatebridge.socket.lifecycle.enabled", "true");
        System.setProperty("gatebridge.admin.legacy.singleport", "true");

        ConnectionRegistry registry = new ConnectionRegistry();
        AtomicInteger connectCount = new AtomicInteger(0);
        AtomicInteger heartbeatCount = new AtomicInteger(0);
        AtomicInteger disconnectCount = new AtomicInteger(0);

        registry.addLifecycleListener(new ConnectionLifecycleListener() {
            @Override
            public void onConnect(ConnectionContext context) {
                connectCount.incrementAndGet();
            }

            @Override
            public void onHeartbeat(ConnectionContext context) {
                heartbeatCount.incrementAndGet();
            }

            @Override
            public void onDisconnect(ConnectionContext context) {
                disconnectCount.incrementAndGet();
            }

            @Override
            public void onError(ConnectionContext context, Throwable cause) {}
        });

        UndertowHttpTransport transport = new UndertowHttpTransport();
        transport.setConnectionRegistry(registry);

        RouteRegistry routeRegistry = new RouteRegistry();
        routeRegistry.getRoutes().put("/test-socket", (args, writer) -> writer.print("ok"));
        routeRegistry.getRoutes().put("/TEST-SOCKET", (args, writer) -> writer.print("ok"));

        int port = findFreePort();
        transport.listen(port, routeRegistry, Collections.emptyList(), Collections.emptyList());

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/test-socket"))
                    .GET()
                    .build();

            // Send first request over TCP connection
            HttpResponse<String> resp1 = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp1.statusCode());
            assertEquals("ok", resp1.body());

            assertEquals(1, connectCount.get(), "onConnect should be called once on initial TCP connection");
            assertEquals(0, heartbeatCount.get(), "onHeartbeat should not be called on first request");
            assertEquals(0, disconnectCount.get(), "onDisconnect should NOT be called while TCP connection is active");
            assertEquals(1, registry.getActiveConnectionCount(), "Registry should hold active connection context");

            // Send second request over the same connection
            HttpResponse<String> resp2 = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp2.statusCode());

            assertEquals(1, connectCount.get(), "onConnect should STILL be 1 for reused TCP socket");
            assertTrue(heartbeatCount.get() >= 1, "onHeartbeat should be called for subsequent requests on same connection");
            assertEquals(0, disconnectCount.get(), "onDisconnect should STILL be 0 while socket is active");

        } finally {
            transport.stop();
            Thread.sleep(200);
            assertTrue(disconnectCount.get() >= 1, "onDisconnect should be called when connection closes");
        }
    }

    @Test
    public void testPerRequestFallbackWhenSocketLifecycleDisabled() throws Exception {
        System.setProperty("gatebridge.socket.lifecycle.enabled", "false");
        System.setProperty("gatebridge.admin.legacy.singleport", "true");

        ConnectionRegistry registry = new ConnectionRegistry();
        AtomicInteger connectCount = new AtomicInteger(0);
        AtomicInteger disconnectCount = new AtomicInteger(0);

        registry.addLifecycleListener(new ConnectionLifecycleListener() {
            @Override
            public void onConnect(ConnectionContext context) {
                connectCount.incrementAndGet();
            }

            @Override
            public void onHeartbeat(ConnectionContext context) {}

            @Override
            public void onDisconnect(ConnectionContext context) {
                disconnectCount.incrementAndGet();
            }

            @Override
            public void onError(ConnectionContext context, Throwable cause) {}
        });

        UndertowHttpTransport transport = new UndertowHttpTransport();
        transport.setConnectionRegistry(registry);

        RouteRegistry routeRegistry = new RouteRegistry();
        routeRegistry.getRoutes().put("/test-fallback", (args, writer) -> writer.print("ok"));
        routeRegistry.getRoutes().put("/TEST-FALLBACK", (args, writer) -> writer.print("ok"));

        int port = findFreePort();
        transport.listen(port, routeRegistry, Collections.emptyList(), Collections.emptyList());

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/test-fallback"))
                    .GET()
                    .build();

            HttpResponse<String> resp1 = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp1.statusCode());

            assertEquals(1, connectCount.get());
            assertEquals(1, disconnectCount.get());
            assertEquals(0, registry.getActiveConnectionCount());

            HttpResponse<String> resp2 = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp2.statusCode());

            assertEquals(2, connectCount.get());
            assertEquals(2, disconnectCount.get());
            assertEquals(0, registry.getActiveConnectionCount());

        } finally {
            transport.stop();
        }
    }

    @Test
    public void testSetPerformanceProfile() {
        UndertowHttpTransport transport = new UndertowHttpTransport();
        assertDoesNotThrow(() -> transport.setPerformanceProfile(hexacloud.core.server.PerformanceProfile.BALANCED_1GB));
        assertDoesNotThrow(() -> transport.setPerformanceProfile(hexacloud.core.server.PerformanceProfile.RESILIENT));
        assertDoesNotThrow(() -> transport.setPerformanceProfile(hexacloud.core.server.PerformanceProfile.MAX_PERFORMANCE));
        assertDoesNotThrow(() -> transport.setPerformanceProfile(hexacloud.core.server.PerformanceProfile.STANDARD));
    }

    @Test
    public void testDataPlaneRejectsAdminRoutesInDualListenerMode() throws Exception {
        System.clearProperty("gatebridge.admin.legacy.singleport");

        UndertowHttpTransport transport = new UndertowHttpTransport();
        RouteRegistry routeRegistry = new RouteRegistry();
        routeRegistry.registerController(new hexacloud.core.server.route.ClusterController(new hexacloud.core.cluster.Cluster("test-cluster")));

        int port = findFreePort();
        transport.listen(port, routeRegistry, Collections.emptyList(), Collections.emptyList());

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/v1/get_nodes_json"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
        } finally {
            transport.stop();
        }
    }
}
