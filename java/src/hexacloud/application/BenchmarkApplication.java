package hexacloud.application;

import hexacloud.core.ports.GatewayBuilderPort;
import hexacloud.core.ports.RunningGatewayPort;
import hexacloud.core.server.route.RouteController;
import hexacloud.core.server.route.RouteMapping;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.infra.gateway.GatewayFactory;

import java.io.PrintWriter;

/**
 * Dedicated unthrottled application launcher for GateBridge raw capacity benchmarking.
 * Runs without rate limiting (.rateLimit) or token requirements (.requireToken)
 * to measure full unthrottled throughput (RPS) and latency profiles across ports.
 */
public class BenchmarkApplication {

    public static void main(String[] args) {
        new BenchmarkApplication().run();
    }

    public void run() {
        DebugUtils.setDebugEnabled(false);
        System.out.println("=== Starting GateBridge Unthrottled Benchmark Application ===");

        GatewayBuilderPort builder = GatewayFactory.createGateway("benchmark-gw")
            .createCluster("benchmark-cluster")
            .port(4000)                   // Telnet: 4000, HTTP: 4001, WS: 4002
            .pingInterval(10)
            .enableHttp(true)
            .httpEngine(hexacloud.core.server.HttpEngine.UNDERTOW)
            .enableTelnet(true)
            .enableWs(true)
            .requireToken(false, null)
            .rateLimit(0, 0)
            .registerController(new BenchmarkRouteController())
            .timeout(5000);

        RunningGatewayPort runningGateway = builder.listen();

        System.out.println("GateBridge Benchmark Application active and listening:");
        System.out.println(" - HTTP:   http://127.0.0.1:4001/hello or http://127.0.0.1:4001/v1/ping");
        System.out.println(" - Telnet: 127.0.0.1:4000");
        System.out.println(" - WS:     ws://127.0.0.1:4002");
        System.out.println("Fast-Path: DISABLED (processing standard gateway routes & filters)");
        System.out.println("Press Ctrl+C to stop.");
    }

    public static class BenchmarkRouteController implements RouteController {

        @RouteMapping("/hello")
        public void handleHello(String args, PrintWriter out) {
            out.println("HELLO");
        }

        @RouteMapping("/v1/ping")
        public void handlePing(String args, PrintWriter out) {
            out.println("PONG");
        }

        @RouteMapping("PING")
        public void handleTelnetPing(String args, PrintWriter out) {
            out.println("PONG");
        }
    }
}
