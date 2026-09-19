package hexacloud.core.server.connection;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.config.ClusterConfig;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.RoutingProtocol;
import hexacloud.core.model.ServerNode;
import hexacloud.core.server.ServerManager;
import hexacloud.core.server.route.RouteController;
import hexacloud.core.server.route.RouteMapping;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionLifecycleIntegrationTest {

    private Cluster testCluster;
    private ServerManager serverManager;

    private ServerSocket echoBackendSocket;
    private int echoBackendPort;
    private final AtomicBoolean backendRunning = new AtomicBoolean(true);
    private ExecutorService backendExecutor;

    @BeforeEach
    public void setUp() throws Exception {
        testCluster = new Cluster("lifecycle-test-cluster");
        testCluster.setRoutingMode(Cluster.RoutingMode.HYBRID);
        testCluster.setRequireToken(false);

        echoBackendPort = findFreePort();
        backendRunning.set(true);
        backendExecutor = Executors.newCachedThreadPool();

        echoBackendSocket = new ServerSocket(echoBackendPort);
        backendExecutor.execute(() -> {
            try {
                while (backendRunning.get() && !echoBackendSocket.isClosed()) {
                    Socket socket = echoBackendSocket.accept();
                    backendExecutor.execute(() -> {
                        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                            String line;
                            while ((line = in.readLine()) != null) {
                                out.println("ECHO:" + line);
                            }
                        } catch (IOException ignored) {
                        } finally {
                            try {
                                socket.close();
                            } catch (IOException ignored) {}
                        }
                    });
                }
            } catch (IOException ignored) {}
        });

        ServerNode tcpNode = new ServerNode("tcp-backend-1", "http://127.0.0.1", echoBackendPort, NodeStatus.ONLINE, false)
                .withRoutingProtocol(RoutingProtocol.TCP);
        testCluster.registerServer(tcpNode);

        serverManager = new ServerManager(testCluster, null);
        serverManager.setAdminPort(findFreePort());
    }

    @AfterEach
    public void tearDown() {
        backendRunning.set(false);
        if (serverManager != null) {
            serverManager.stop();
        }
        if (echoBackendSocket != null && !echoBackendSocket.isClosed()) {
            try {
                echoBackendSocket.close();
            } catch (IOException ignored) {}
        }
        if (backendExecutor != null) {
            backendExecutor.shutdownNow();
        }
    }

    public static class TestPingController implements RouteController {
        @RouteMapping("PING")
        public void handlePing(String args, PrintWriter out) {
            out.println("PONG " + args);
        }
    }

    @Test
    public void testTelnetConnectionLifecycleIntegration() throws Exception {
        serverManager.enableTelnet(true);
        serverManager.registerRouteController(new TestPingController());

        List<ConnectionContext> connectedList = new CopyOnWriteArrayList<>();
        List<ConnectionContext> disconnectedList = new CopyOnWriteArrayList<>();
        CountDownLatch connectLatch = new CountDownLatch(1);
        CountDownLatch disconnectLatch = new CountDownLatch(1);

        ConnectionRegistry registry = serverManager.getConnectionRegistry();
        assertNotNull(registry, "ConnectionRegistry must be non-null in ServerManager");
        assertEquals(0, registry.getActiveConnectionCount());

        registry.addLifecycleListener(new ConnectionLifecycleListener() {
            @Override
            public void onConnect(ConnectionContext context) {
                connectedList.add(context);
                connectLatch.countDown();
            }

            @Override
            public void onHeartbeat(ConnectionContext context) {}

            @Override
            public void onDisconnect(ConnectionContext context) {
                disconnectedList.add(context);
                disconnectLatch.countDown();
            }

            @Override
            public void onError(ConnectionContext context, Throwable cause) {}
        });

        int port = findFreePort();
        serverManager.listen(port);
        Thread.sleep(100);

        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("PING hello");
            String response = in.readLine();
            assertEquals("PONG hello", response);

            assertTrue(connectLatch.await(3, TimeUnit.SECONDS), "onConnect callback should fire when socket connects");
            assertEquals(1, connectedList.size());
            ConnectionContext connectedCtx = connectedList.get(0);
            assertEquals("TELNET", connectedCtx.getProtocol());
            assertTrue(connectedCtx.getRemoteAddress().contains("127.0.0.1"));
        }

        assertTrue(disconnectLatch.await(3, TimeUnit.SECONDS), "onDisconnect callback should fire when socket closes");
        assertEquals(1, disconnectedList.size());
        ConnectionContext disconnectedCtx = disconnectedList.get(0);
        assertEquals("TELNET", disconnectedCtx.getProtocol());
        assertEquals(connectedList.get(0).getConnectionId(), disconnectedCtx.getConnectionId());
        assertEquals(0, registry.getActiveConnectionCount(), "Active connection count should return to 0 after disconnect");
    }

    @Test
    public void testTcpProxyConnectionLifecycleIntegration() throws Exception {
        serverManager.enableTcpProxy(true);

        List<ConnectionContext> connectedList = new CopyOnWriteArrayList<>();
        List<ConnectionContext> disconnectedList = new CopyOnWriteArrayList<>();
        CountDownLatch connectLatch = new CountDownLatch(1);
        CountDownLatch disconnectLatch = new CountDownLatch(1);

        ConnectionRegistry registry = serverManager.getConnectionRegistry();
        registry.addLifecycleListener(new ConnectionLifecycleListener() {
            @Override
            public void onConnect(ConnectionContext context) {
                connectedList.add(context);
                connectLatch.countDown();
            }

            @Override
            public void onHeartbeat(ConnectionContext context) {}

            @Override
            public void onDisconnect(ConnectionContext context) {
                disconnectedList.add(context);
                disconnectLatch.countDown();
            }

            @Override
            public void onError(ConnectionContext context, Throwable cause) {}
        });

        int basePort = findFreePort();
        serverManager.listen(basePort);
        Thread.sleep(100);

        int proxyPort = basePort + ClusterConfig.TCP_PORT_OFFSET;

        try (Socket socket = new Socket("127.0.0.1", proxyPort)) {
            socket.setSoTimeout(3000);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("hello-tcp");
            String resp = in.readLine();
            assertEquals("ECHO:hello-tcp", resp);

            assertTrue(connectLatch.await(3, TimeUnit.SECONDS), "onConnect callback should fire for TCP proxy");
            assertEquals(1, connectedList.size());
            assertEquals("TCP", connectedList.get(0).getProtocol());
        }

        assertTrue(disconnectLatch.await(3, TimeUnit.SECONDS), "onDisconnect callback should fire for TCP proxy when closed");
        assertEquals(1, disconnectedList.size());
        assertEquals("TCP", disconnectedList.get(0).getProtocol());
        assertEquals(0, registry.getActiveConnectionCount());
    }

    @Test
    public void testHttpConnectionLifecycleIntegration() throws Exception {
        serverManager.enableHttp(true);
        serverManager.registerRouteController(new TestPingController());

        List<ConnectionContext> connectedList = new CopyOnWriteArrayList<>();
        List<ConnectionContext> disconnectedList = new CopyOnWriteArrayList<>();
        CountDownLatch connectLatch = new CountDownLatch(1);
        CountDownLatch disconnectLatch = new CountDownLatch(1);

        ConnectionRegistry registry = serverManager.getConnectionRegistry();
        registry.addLifecycleListener(new ConnectionLifecycleListener() {
            @Override
            public void onConnect(ConnectionContext context) {
                connectedList.add(context);
                connectLatch.countDown();
            }

            @Override
            public void onHeartbeat(ConnectionContext context) {}

            @Override
            public void onDisconnect(ConnectionContext context) {
                disconnectedList.add(context);
                disconnectLatch.countDown();
            }

            @Override
            public void onError(ConnectionContext context, Throwable cause) {}
        });

        int basePort = findFreePort();
        serverManager.listen(basePort);
        Thread.sleep(100);

        int httpPort = basePort + ClusterConfig.HTTP_PORT_OFFSET;
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://127.0.0.1:" + httpPort + "/V1/GET_NODES_JSON"))
                .GET()
                .build();

        java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        assertTrue(connectLatch.await(3, TimeUnit.SECONDS), "onConnect callback should fire for HTTP");
        assertTrue(disconnectLatch.await(3, TimeUnit.SECONDS), "onDisconnect callback should fire for HTTP when request completes");
        assertEquals(1, connectedList.size());
        assertEquals("HTTP", connectedList.get(0).getProtocol());
        assertEquals(1, disconnectedList.size());
        assertEquals("HTTP", disconnectedList.get(0).getProtocol());
        assertEquals(0, registry.getActiveConnectionCount());
    }

    @Test
    public void testServerManagerStopClosesActiveConnections() {
        ConnectionRegistry registry = serverManager.getConnectionRegistry();
        ConnectionContext mockContext = new ConnectionContextImpl("test-id-1", "TCP", "127.0.0.1:12345");
        registry.registerConnection(mockContext);
        assertEquals(1, registry.getActiveConnectionCount());

        serverManager.stop();
        assertEquals(0, registry.getActiveConnectionCount(), "ServerManager.stop() should invoke closeAll() on ConnectionRegistry");
        assertFalse(mockContext.isAlive(), "Registered connection should be closed after ServerManager.stop()");
    }

    private int findFreePort() throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            int port;
            try (ServerSocket s0 = new ServerSocket(0)) {
                port = s0.getLocalPort();
            }
            boolean allFree = true;
            for (int i = 0; i <= 3; i++) {
                try (ServerSocket check = new ServerSocket(port + i)) {
                    // Port is free
                } catch (Exception e) {
                    allFree = false;
                    break;
                }
            }
            if (allFree) {
                return port;
            }
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
