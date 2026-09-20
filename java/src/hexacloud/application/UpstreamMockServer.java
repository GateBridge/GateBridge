package hexacloud.application;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Lightweight Standalone Upstream Mock Server on port 3001.
 * Runs as an isolated process to decouple upstream heap usage from GateBridge Gateway JVM.
 */
public class UpstreamMockServer {

    public static void main(String[] args) throws Exception {
        int port = 3001;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        byte[] response = "{\"status\":\"ok\",\"source\":\"upstream-node-3001\"}".getBytes(StandardCharsets.UTF_8);

        HttpHandler handler = new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            }
        };

        server.createContext("/hello", handler);
        server.createContext("/health", handler);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        System.out.println("=== Upstream Mock Backend running on http://127.0.0.1:" + port + "/hello ===");
        Thread.currentThread().join();
    }
}
