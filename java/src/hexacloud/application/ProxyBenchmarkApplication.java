package hexacloud.application;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import hexacloud.core.cluster.Cluster;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.PingProtocol;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.GatewayBuilderPort;
import hexacloud.core.ports.RunningGatewayPort;
import hexacloud.core.server.route.RouteRule;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.infra.gateway.GatewayFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Dedicated benchmark application for measuring GateBridge Reverse Proxy / Load Balancer performance.
 * Launches a lightweight upstream mock backend on port 3001 and routes traffic through GateBridge gateway (port 4001).
 */
public class ProxyBenchmarkApplication {

    private HttpServer mockUpstreamServer;

    public static void main(String[] args) throws Exception {
        new ProxyBenchmarkApplication().run();
    }

    public void run() throws Exception {
        DebugUtils.setDebugEnabled(false);
        hexacloud.core.config.ClusterStatePersistence.setPersistenceAdapter(new hexacloud.core.ports.ClusterPersistencePort() {
            @Override public void saveState() {}
            @Override public void loadState() {}
            @Override public boolean isStateLoaded() { return false; }
        });
        System.out.println("=== Starting GateBridge Reverse Proxy Benchmark Application ===");

        // 1. Check/Start Upstream Mock Backend Server on port 3001
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 3001), 200);
            System.out.println("Using standalone Upstream Mock Backend running on http://127.0.0.1:3001/hello");
        } catch (IOException e) {
            mockUpstreamServer = HttpServer.create(new InetSocketAddress(3001), 0);
            HttpHandler helloHandler = new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    byte[] response = "{\"status\":\"ok\",\"source\":\"upstream-node-3001\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response);
                    }
                }
            };
            mockUpstreamServer.createContext("/hello", helloHandler);
            mockUpstreamServer.createContext("/health", helloHandler);
            mockUpstreamServer.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
            mockUpstreamServer.start();
            System.out.println("Started internal Upstream Mock Backend running on http://127.0.0.1:3001/hello");
        }

        // 2. Configure GateBridge Gateway with Reverse Proxy Cluster & Routing Rule
        GatewayBuilderPort builder = GatewayFactory.createGateway("proxy-gw", "backend-cluster")
            .port(4000)
            .enableHttp(true)
            .requireToken(false, null)
            .rateLimit(0, 0)
            .timeout(5000);

        Cluster cluster = builder.getCluster();
        cluster.setRoutingMode(Cluster.RoutingMode.HYBRID);

        ServerNode node = new ServerNode(
            "backend-node-1", "http://127.0.0.1", 3001, NodeStatus.ONLINE, false,
            PingProtocol.NONE, "/hello", null, null
        );
        builder.registerServer(node);

        // Add Ingress RouteRule: /proxy/** -> proxy to backend-cluster /hello
        builder.routeHost("*", "/proxy/**", "backend-cluster", "/hello");

        RunningGatewayPort runningGateway = builder.listen();
        runningGateway.registerServer(node);

        System.out.println("GateBridge Proxy Gateway listening on:");
        System.out.println(" - Reverse Proxy Route: http://127.0.0.1:4001/proxy/hello");
        System.out.println("Target Cluster: backend-cluster -> Upstream Node http://127.0.0.1:3001/hello");
        System.out.println("Press Ctrl+C to stop.");
        Thread.currentThread().join();
    }
}
