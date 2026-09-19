package hexacloud.infra.server;

import java.io.PrintWriter;
import java.util.function.BiConsumer;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

import hexacloud.core.server.route.RouteRegistry;

public class UndertowManagementTransport {

    private final String host;
    private final int port;
    private final RouteRegistry routeRegistry;
    private Undertow server;
    private volatile boolean listening = false;

    public UndertowManagementTransport(String host, int port, RouteRegistry routeRegistry) {
        this.host = host != null ? host : "127.0.0.1";
        this.port = port;
        this.routeRegistry = routeRegistry;
    }

    public synchronized void start() {
        if (listening) return;

        this.server = Undertow.builder()
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
