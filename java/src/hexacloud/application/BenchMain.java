package hexacloud.application;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.config.ClusterStatePersistence;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.PingProtocol;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.ClusterPersistencePort;
import hexacloud.core.ports.GatewayBuilderPort;
import hexacloud.core.ports.RunningGatewayPort;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.infra.gateway.GatewayFactory;

import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Headless entry point for reproducible benchmarking, meant to run inside containers.
 *
 * Two roles, selected by GB_ROLE (or the first argument):
 *   gateway  (default) GateBridge L7 reverse proxy in front of GB_UPSTREAMS.
 *   upstream           Undertow backend with fixed service time and payload size.
 *
 * Everything is configured through environment variables so the same image can be reused
 * for every container. No TUI, no token, no rate limit, no health pings and no state
 * persistence, so the measurement covers only the proxy path.
 *
 * Gateway:
 *   GB_BASE_PORT    base port, HTTP listens on base + 1          (default 4000)
 *   GB_UPSTREAMS    comma separated host:port list               (default 127.0.0.1:3001)
 *   GB_ROUTE        ingress path pattern                         (default /proxy/**)
 *   GB_TARGET_PATH  path requested on the upstream               (default /hello)
 *   GB_CAP          active requests cap, 0 = unlimited           (default 0)
 *   GB_FASTPATH     true/false, forwards gatebridge.fastpath.enabled (unset = framework default)
 *   GB_TIMEOUT_MS   upstream timeout                             (default 5000)
 *
 * Upstream:
 *   GB_UP_PORT          listen port                              (default 3001)
 *   GB_UP_DELAY_MS      simulated service time per request       (default 0)
 *   GB_UP_PAYLOAD_BYTES response body size                       (default 64)
 *   GB_UP_IO_THREADS    Undertow IO threads                      (default = available processors)
 */
public class BenchMain {

    public static void main(String[] args) throws Exception {
        String role = args.length > 0 ? args[0] : env("GB_ROLE", "gateway");
        switch (role.toLowerCase()) {
            case "gateway":
                runGateway();
                break;
            case "upstream":
                runUpstream();
                break;
            default:
                System.err.println("Unknown role '" + role + "'. Use gateway or upstream.");
                System.exit(2);
        }
        Thread.currentThread().join();
    }

    // ------------------------------------------------------------------
    // Gateway
    // ------------------------------------------------------------------

    private static void runGateway() {
        DebugUtils.setDebugEnabled(false);

        // Never touch disk during a run: state persistence would add I/O noise.
        ClusterStatePersistence.setPersistenceAdapter(new ClusterPersistencePort() {
            @Override public void saveState() {}
            @Override public void loadState() {}
            @Override public boolean isStateLoaded() { return false; }
        });

        int basePort = envInt("GB_BASE_PORT", 4000);
        String route = env("GB_ROUTE", "/proxy/**");
        String targetPath = env("GB_TARGET_PATH", "/hello");
        int timeoutMs = envInt("GB_TIMEOUT_MS", 5000);
        String cluster = "bench-cluster";

        // System properties read by the transport at request time.
        System.setProperty("gatebridge.active.requests.cap", String.valueOf(envInt("GB_CAP", 0)));
        String fastPath = System.getenv("GB_FASTPATH");
        if (fastPath != null && !fastPath.isBlank()) {
            System.setProperty("gatebridge.fastpath.enabled", fastPath.trim());
        }

        GatewayBuilderPort builder = GatewayFactory.createGateway("bench-gw", cluster)
                .port(basePort)
                .enableHttp(true)
                .httpEngine(hexacloud.core.server.HttpEngine.UNDERTOW)
                .requireToken(false, null)
                .rateLimit(0, 0)
                .timeout(timeoutMs);

        builder.getCluster().setRoutingMode(Cluster.RoutingMode.HYBRID);

        List<ServerNode> nodes = new ArrayList<>();
        int index = 1;
        for (String entry : env("GB_UPSTREAMS", "127.0.0.1:3001").split(",")) {
            String[] hp = entry.trim().split(":");
            if (hp.length != 2) {
                throw new IllegalArgumentException("Invalid GB_UPSTREAMS entry: '" + entry + "', expected host:port");
            }
            ServerNode node = new ServerNode(
                    "bench-node-" + index++, "http://" + hp[0], Integer.parseInt(hp[1]),
                    NodeStatus.ONLINE, false, PingProtocol.NONE, targetPath, null, null);
            builder.registerServer(node);
            nodes.add(node);
        }

        builder.routeHost("*", route, cluster, targetPath);

        RunningGatewayPort gateway = builder.listen();
        for (ServerNode node : nodes) {
            gateway.registerServer(node);
        }

        System.out.println("[BenchMain] gateway ready: http://0.0.0.0:" + (basePort + 1) + route
                + " -> " + Arrays.toString(nodes.stream().map(ServerNode::getFullHost).toArray())
                + targetPath + " | cap=" + System.getProperty("gatebridge.active.requests.cap")
                + " | cpus=" + Runtime.getRuntime().availableProcessors()
                + " | maxHeap=" + Runtime.getRuntime().maxMemory() / (1024 * 1024) + "MB");
    }

    // ------------------------------------------------------------------
    // Upstream
    // ------------------------------------------------------------------

    private static void runUpstream() {
        int port = envInt("GB_UP_PORT", 3001);
        int delayMs = envInt("GB_UP_DELAY_MS", 0);
        int payloadBytes = envInt("GB_UP_PAYLOAD_BYTES", 64);
        int ioThreads = envInt("GB_UP_IO_THREADS", Runtime.getRuntime().availableProcessors());

        byte[] body = new byte[payloadBytes];
        Arrays.fill(body, (byte) 'x');
        if (payloadBytes >= 2) {
            body[0] = '{';
            body[payloadBytes - 1] = '}';
        }

        // Virtual threads carry the simulated service time, so the delay never blocks an IO thread.
        ExecutorService delayed = Executors.newVirtualThreadPerTaskExecutor();

        Undertow server = Undertow.builder()
                .addHttpListener(port, "0.0.0.0")
                .setIoThreads(ioThreads)
                .setHandler(exchange -> {
                    if (delayMs > 0) {
                        exchange.dispatch(delayed, () -> {
                            try {
                                Thread.sleep(delayMs);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            respond(exchange, body);
                        });
                    } else {
                        respond(exchange, body);
                    }
                })
                .build();
        server.start();

        System.out.println("[BenchMain] upstream ready: http://0.0.0.0:" + port
                + " | delay=" + delayMs + "ms | payload=" + payloadBytes + "B | ioThreads=" + ioThreads);
    }

    private static void respond(HttpServerExchange exchange, byte[] body) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(ByteBuffer.wrap(body));
    }

    // ------------------------------------------------------------------
    // Env helpers
    // ------------------------------------------------------------------

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static int envInt(String key, int def) {
        try {
            return Integer.parseInt(env(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
