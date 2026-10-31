package hexacloud.application;

import hexacloud.core.event.Event;
import hexacloud.core.event.EventController;
import hexacloud.core.event.EventFormat;
import hexacloud.core.event.Subscribe;
import hexacloud.core.cluster.event.ClusterEvent.NodeRegistered;
import hexacloud.core.cluster.event.ClusterEvent.NodeEventSubmitted;
import hexacloud.core.cluster.event.ClusterEvent.NodeStatusChanged;
import hexacloud.core.cluster.event.ClusterEvent.NodeTelemetryUpdated;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.PingProtocol;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.GatewayBuilderPort;
import hexacloud.core.ports.RunningGatewayPort;
import hexacloud.core.server.route.RouteController;
import hexacloud.core.server.route.RouteMapping;
import hexacloud.core.tui.TerminalUiFactory;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.core.utils.concurrent.ThreadManager;
import hexacloud.infra.gateway.GatewayFactory;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Collections;

public class Main {
    
    public static void main(String[] args) {
        new Main().start();
    }

    public void start() {
        DebugUtils.setDebugEnabled(true);
        
        GatewayBuilderPort builder = GatewayFactory.createGateway("gateway-1")
            .createCluster("watashi-cluster")
            .port(3000)
            .pingInterval(5)
            .enableTelnet(true)
            .enableHttp(true)
            .enableWs(true)
            .enableTcpProxy(true) // Enables L4 TCP Proxy load-balancer on port 3003
            .registerController(new CustomAppController())
            .registerFilter(new CustomAppLoggingFilter())
            .requireToken(true, "watashi_secretKey")
            .rateLimit(200, 60)
            .allowedIps("127.0.0.1")
            .timeout(4500);

        // Configure routing mode on cluster (HYBRID enables telemetry + proxy routing)
        builder.getCluster().setRoutingMode(hexacloud.core.cluster.Cluster.RoutingMode.HYBRID);

        // Register servers with explicit names and protocol metadata
        builder.registerServer(new ServerNode(
                "node-http-1", "http://localhost", 3006, NodeStatus.OFFLINE, false,
                PingProtocol.HTTP, "/health", "X-Cluster-Token", "watashi_secretKey"
            ))
            .registerServer(new ServerNode(
                "node-grpc-1", "http://localhost", 3007, NodeStatus.OFFLINE, false,
                PingProtocol.GRPC, "/grpc-health", null, null
            ))
            .registerServer(new ServerNode(
                "node-ws-1", "http://localhost", 3008, NodeStatus.OFFLINE, false,
                PingProtocol.WEBSOCKET, "/ws", null, null
            ))
            .registerServer(new ServerNode(
                "node-tcp-1", "http://localhost", 3009, NodeStatus.OFFLINE, false,
                PingProtocol.TCP, "/", null, null
            ))
            .registerServer(new ServerNode(
                "node-udp-1", "http://localhost", 3010, NodeStatus.OFFLINE, false,
                PingProtocol.UDP, "/", null, null
            ));

        RunningGatewayPort runningGateway = builder.listen()
            .listClusterNodes()
            .startPingScheduler();

        runningGateway.eventManager().dispatch(new UserCustomEvent("Hello EventController scanning system!"));
        runningGateway.eventManager().dispatch(new NodeEventSubmitted(
            "http://localhost:3006",
            3006,
            PingProtocol.HTTP,
            EventFormat.JSON,
            "demo.boot",
            Collections.singletonMap("source", "Main")
        ));

        // Background monitoring loop to log system resources and thread breakdown
        ThreadManager.startVirtual("SystemMonitor", () -> {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            Runtime runtime = Runtime.getRuntime();
            while (true) {
                try {
                    Thread.sleep(5000);
                    int threadCount = threadMXBean.getThreadCount();
                    long freeMem = runtime.freeMemory() / (1024 * 1024);
                    long totalMem = runtime.totalMemory() / (1024 * 1024);
                    long usedMem = totalMem - freeMem;
                    DebugUtils.info(String.format("[MONITOR] OS Threads: %d | Heap: %d MB / %d MB", 
                        threadCount, usedMem, totalMem));
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        // Launch the DevOps Panel in non-blocking toggle mode (detach/reattach with ENTER)
        TerminalUiFactory.createTui("MyCompany - GateBridge DevOps Panel")
            .seedGateway(runningGateway)
            .redirectSystemOut(true)
            .startToggleMode();
    }

    // Custom event verification
    public static class UserCustomEvent implements Event {
        String message;

        public UserCustomEvent(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    //Custom controller listener - automatically discovered by PathUtils scanner
    public static class CustomEventListener implements EventController {
        @Subscribe
        public void onCustomEvent(UserCustomEvent event) {
            DebugUtils.info("UserCustomEvent handler method invoked: " + event.message());
        }

        @Subscribe
        public void onNodeRegistered(NodeRegistered event) {
            DebugUtils.info("Event received: Node successfully registered at " + event.node().getFullHost());
        }

        @Subscribe
        public void onNodeStatusChanged(NodeStatusChanged event) {
            DebugUtils.info("Event received: Node status changed -> Host: " + event.host() + " | Status: " + event.status());
        }

        @Subscribe
        public void onNodeTelemetryUpdated(NodeTelemetryUpdated event) {
            DebugUtils.info("Event received: Telemetry updated for node: " + event.host());
        }

        @Subscribe
        public void onNodeEventSubmitted(NodeEventSubmitted event) {
            DebugUtils.info(
                "Event received: " + event.event() + " from " + event.host() +
                " [" + event.protocol() + "/" + event.format() + "] " + event.attributes()
            );
        }
    }

    // Custom developer endpoint controller - automatically discovered by PathUtils scanner
    public static class CustomAppController implements RouteController {
        @RouteMapping("/hello")
        public void sayHello(String args, PrintWriter out) {
            out.println("HELLO FROM DEVELOPER ROUTE! Args: " + args);
        }

        @RouteMapping("/system_info")
        public void handleSystemInfo(String args, PrintWriter out) {
            out.println("GateBridge Framework Status: ACTIVE");
            out.println("Available Processors: " + Runtime.getRuntime().availableProcessors());
        }
    }

    // Custom HTTP logging filter implementing HttpFilter contract
    @hexacloud.core.server.filter.Order(50)
    public static class CustomAppLoggingFilter implements hexacloud.core.server.filter.HttpFilter {
        @Override
        public void doFilter(
            hexacloud.core.server.filter.HttpRequest req,
            hexacloud.core.server.filter.HttpResponse res,
            hexacloud.core.server.filter.HttpFilterChain chain
        ) throws Exception {
            DebugUtils.info("[CustomAppLoggingFilter] Intercepted HTTP Request: " + req.getMethod() + " " + req.getPath());
            chain.doFilter(req, res);
        }
    }
}
