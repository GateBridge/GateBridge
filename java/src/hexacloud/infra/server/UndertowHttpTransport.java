package hexacloud.infra.server;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.server.ServerTransport;
import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.server.route.RouteResolution;
import hexacloud.core.server.route.PathResolver;
import hexacloud.core.server.filter.HttpFilter;
import hexacloud.core.server.filter.HttpRequest;
import hexacloud.core.server.filter.HttpResponse;
import hexacloud.core.server.filter.Order;
import hexacloud.core.server.filter.builtin.IpRestrictionFilter;
import hexacloud.core.server.filter.builtin.RateLimitFilter;
import hexacloud.core.server.filter.builtin.TokenAuthFilter;
import hexacloud.core.server.filter.builtin.CorsFilter;
import hexacloud.core.server.filter.HttpFilterChainImpl;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.core.utils.concurrent.ThreadManager;

import java.io.PrintWriter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import hexacloud.core.server.connection.ConnectionContext;
import hexacloud.core.server.connection.ConnectionContextImpl;
import hexacloud.core.server.connection.ConnectionRegistry;

public class UndertowHttpTransport implements ServerTransport {

    private Undertow server;
    private boolean running = false;
    private ConnectionRegistry connectionRegistry;
    private java.util.concurrent.ExecutorService virtualExecutor;
    private final HttpErrorHandler errorHandler = new DefaultHttpErrorHandler();
    private final java.util.concurrent.atomic.AtomicInteger activeRequests = new java.util.concurrent.atomic.AtomicInteger(0);
    private final ReverseProxyService reverseProxyService = new ReverseProxyService(new hexacloud.core.utils.network.JdkHttpProxyClient(), errorHandler);

    private hexacloud.core.server.PerformanceProfile performanceProfile = hexacloud.core.server.PerformanceProfile.STANDARD;
    private final List<HttpFilter> activeFilters = new CopyOnWriteArrayList<>();
    private hexacloud.core.ports.SslContextPort sslContextPort;

    @Override
    public void setConnectionRegistry(ConnectionRegistry registry) {
        this.connectionRegistry = registry;
    }

    private void rebuildFilters(List<Cluster> clusters, List<HttpFilter> customFilters) {
        activeFilters.clear();
        
        // CORS filter is always the first filter in the chain
        activeFilters.add(new CorsFilter());

        if (clusters != null) {
            for (Cluster cluster : clusters) {
                String allowedIps = cluster.getAllowedIps();
                if (allowedIps != null && !allowedIps.trim().isEmpty()) {
                    activeFilters.add(new IpRestrictionFilter(cluster));
                }
                if (cluster.getRateLimitRequests() > 0 && cluster.getRateLimitDurationSeconds() > 0) {
                    activeFilters.add(new RateLimitFilter(cluster));
                }
                if (cluster.isRequireToken()) {
                    activeFilters.add(new TokenAuthFilter(cluster));
                }
            }
        }
        activeFilters.addAll(customFilters);

        // Sort custom filters by @Order annotation value (if present)
        activeFilters.sort((f1, f2) -> {
            int o1 = f1.getClass().isAnnotationPresent(Order.class) ? f1.getClass().getAnnotation(Order.class).value() : 100;
            int o2 = f2.getClass().isAnnotationPresent(Order.class) ? f2.getClass().getAnnotation(Order.class).value() : 100;
            return Integer.compare(o1, o2);
        });
    }

    @Override
    public void setPerformanceProfile(hexacloud.core.server.PerformanceProfile profile) {
        if (profile != null) {
            this.performanceProfile = profile;
            this.reverseProxyService.setPerformanceProfile(profile);
        }
    }

    public hexacloud.core.server.PerformanceProfile getPerformanceProfile() {
        return performanceProfile;
    }

    public void setSslContext(hexacloud.core.ports.SslContextPort sslContextPort) {
        this.sslContextPort = sslContextPort;
    }

    @Override
    public void listen(int port, RouteRegistry registry, List<Cluster> clusters, List<HttpFilter> customFilters) {
        try {
            rebuildFilters(clusters, customFilters);
            io.undertow.connector.ByteBufferPool bufferPool = createByteBufferPool();
            Undertow.Builder builder = Undertow.builder()
                    .addHttpListener(port, "0.0.0.0")
                    .setByteBufferPool(bufferPool);
            
            if (sslContextPort != null && sslContextPort.isSslEnabled()) {
                builder.addHttpsListener(sslContextPort.getSslPort(), "0.0.0.0", sslContextPort.getSslContext());
            }
 
            if (performanceProfile == hexacloud.core.server.PerformanceProfile.MAX_PERFORMANCE) {
                builder.setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, true)
                        .setServerOption(UndertowOptions.BUFFER_PIPELINED_DATA, false)
                        .setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, false)
                        .setServerOption(UndertowOptions.ENABLE_STATISTICS, false)
                        .setSocketOption(org.xnio.Options.BACKLOG, 16384)
                        .setSocketOption(org.xnio.Options.TCP_NODELAY, true)
                        .setSocketOption(org.xnio.Options.REUSE_ADDRESSES, true)
                        .setIoThreads(Math.max(Runtime.getRuntime().availableProcessors(), 2))
                        .setWorkerThreads(Runtime.getRuntime().availableProcessors() * 8);
            } else {
                builder.setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, true)
                        .setServerOption(UndertowOptions.BUFFER_PIPELINED_DATA, false)
                        .setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, false)
                        .setServerOption(UndertowOptions.ENABLE_STATISTICS, false)
                        .setSocketOption(org.xnio.Options.BACKLOG, 8192)
                        .setSocketOption(org.xnio.Options.TCP_NODELAY, true)
                        .setSocketOption(org.xnio.Options.REUSE_ADDRESSES, true)
                        .setIoThreads(Math.max(Runtime.getRuntime().availableProcessors() / 2, 2))
                        .setWorkerThreads(Runtime.getRuntime().availableProcessors() * 2);
            }

            virtualExecutor = ThreadManager.newVirtualThreadPool();

            builder.setHandler(new HttpHandler() {
                @Override
                public void handleRequest(HttpServerExchange exchange) throws Exception {
                    String path = exchange.getRequestPath();
                    RouteResolution resolution = PathResolver.resolve(path, exchange.getRequestHeaders().getFirst(io.undertow.util.Headers.HOST), registry);
                    boolean allowLegacySinglePortAdmin = Boolean.getBoolean("gatebridge.admin.legacy.singleport");
                    boolean canUseFastPath = allowLegacySinglePortAdmin && isFastPathEnabled() && resolution.isLocal() 
                            && registry.isRouteFastPath(resolution.localRouteName())
                            && (activeFilters.isEmpty() || (activeFilters.size() == 1 && activeFilters.get(0) instanceof CorsFilter));

                    if (canUseFastPath) {
                        processRequest(exchange, registry, resolution, true);
                        return;
                    }

                    int cap = getActiveRequestsCap();
                    if (exchange.isInIoThread()) {
                        if (cap <= 0 || activeRequests.incrementAndGet() <= cap) {
                            exchange.dispatch(virtualExecutor, () -> {
                                try {
                                    processRequest(exchange, registry, resolution, false);
                                } catch (Exception e) {
                                    handleError(exchange, e);
                                } finally {
                                    if (cap > 0) activeRequests.decrementAndGet();
                                }
                            });
                        } else {
                            if (cap > 0) activeRequests.decrementAndGet();
                            exchange.setStatusCode(503);
                            exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("503 Service Unavailable - Gateway Overloaded");
                        }
                        return;
                    }
                    processRequest(exchange, registry, resolution, false);
                }
            });

            server = builder.build();
            server.start();
            running = true;
            DebugUtils.info("HTTP Transport (Undertow) successfully bound and listening on port " + port);
        } catch (Exception e) {
            DebugUtils.error("HTTP Transport (Undertow) failed to start on port " + port, e);
        }
    }

    private static final io.undertow.util.HttpString HEADER_CORS_ORIGIN = io.undertow.util.HttpString.tryFromString("Access-Control-Allow-Origin");
    private static final io.undertow.util.HttpString HEADER_CORS_METHODS = io.undertow.util.HttpString.tryFromString("Access-Control-Allow-Methods");
    private static final io.undertow.util.HttpString HEADER_CORS_HEADERS = io.undertow.util.HttpString.tryFromString("Access-Control-Allow-Headers");
    private static final java.util.concurrent.atomic.AtomicLong ATOMIC_ID_COUNTER = new java.util.concurrent.atomic.AtomicLong(0);
    private static final io.undertow.util.AttachmentKey<ConnectionContext> CONNECTION_CONTEXT_KEY = io.undertow.util.AttachmentKey.create(ConnectionContext.class);

    io.undertow.connector.ByteBufferPool createByteBufferPool() {
        return new io.undertow.server.DefaultByteBufferPool(
                false, 
                4096, 
                512, 
                2, 
                0
        );
    }

    String generateConnectionId() {
        String mode = System.getProperty("gatebridge.connection.id.generator", "atomic");
        if ("uuid".equalsIgnoreCase(mode)) {
            return UUID.randomUUID().toString();
        }
        return String.valueOf(ATOMIC_ID_COUNTER.incrementAndGet());
    }

    private void processRequest(HttpServerExchange exchange, RouteRegistry registry, RouteResolution resolution, boolean canUseFastPath) {
        ConnectionContext legacyCtx = null;
        boolean registryEnabled = Boolean.parseBoolean(System.getProperty("gatebridge.connection.registry.enabled", "true"));
        boolean socketLifecycleEnabled = Boolean.parseBoolean(System.getProperty("gatebridge.socket.lifecycle.enabled", "true"));

        if (registryEnabled && connectionRegistry != null) {
            io.undertow.server.ServerConnection connection = exchange.getConnection();
            if (socketLifecycleEnabled && connection != null) {
                ConnectionContext ctx = connection.getAttachment(CONNECTION_CONTEXT_KEY);
                if (ctx == null) {
                    synchronized (connection) {
                        ctx = connection.getAttachment(CONNECTION_CONTEXT_KEY);
                        if (ctx == null) {
                            String remoteAddr = exchange.getSourceAddress() != null
                                    ? exchange.getSourceAddress().toString()
                                    : "unknown";
                            ctx = new ConnectionContextImpl(generateConnectionId(), "HTTP", remoteAddr);
                            connection.putAttachment(CONNECTION_CONTEXT_KEY, ctx);
                            connectionRegistry.registerConnection(ctx);
                            ConnectionContext finalCtx = ctx;
                            connection.addCloseListener(conn -> {
                                connectionRegistry.unregisterConnection(finalCtx);
                            });
                        } else {
                            connectionRegistry.touchConnection(ctx);
                        }
                    }
                } else {
                    connectionRegistry.touchConnection(ctx);
                }
            } else {
                String remoteAddr = exchange.getSourceAddress() != null
                        ? exchange.getSourceAddress().toString()
                        : "unknown";
                legacyCtx = new ConnectionContextImpl(generateConnectionId(), "HTTP", remoteAddr);
                connectionRegistry.registerConnection(legacyCtx);
            }
        }
        try {
            try {
                UndertowHttpRequestImpl req = new UndertowHttpRequestImpl(exchange);

                if (canUseFastPath) {
                    // Set CORS headers directly
                    exchange.getResponseHeaders().put(HEADER_CORS_ORIGIN, "*");
                    exchange.getResponseHeaders().put(HEADER_CORS_METHODS, "GET, POST, OPTIONS, PUT, DELETE");
                    exchange.getResponseHeaders().put(HEADER_CORS_HEADERS, "X-Cluster-Token, Content-Type, Authorization");

                    if (io.undertow.util.Methods.OPTIONS.equals(exchange.getRequestMethod())) {
                        exchange.setStatusCode(204);
                        exchange.endExchange();
                        return;
                    }

                    BiConsumer<String, PrintWriter> handler = registry.getRoutes().get(resolution.localRouteName());
                    if (handler != null) {
                        if (resolution.localRouteName().equals("/V1/GET_NODES_JSON")) {
                            exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_TYPE, "application/json");
                        } else {
                            exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_TYPE, "text/plain");
                        }
                        exchange.setStatusCode(200);

                        FastPrintWriter out = FAST_WRITER.get();
                        out.reset();
                        String query = req.getQuery();
                        String args = query != null ? query : "";
                        handler.accept(args, out);

                        byte[] responseBytes = out.toBytes();
                        exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_LENGTH, String.valueOf(responseBytes.length));
                        exchange.getResponseSender().send(java.nio.ByteBuffer.wrap(responseBytes));
                        return;
                    }
                }

                UndertowHttpResponseImpl res = new UndertowHttpResponseImpl(exchange);

                // Inline default CorsFilter optimization
                if (activeFilters.size() == 1 && activeFilters.get(0) instanceof CorsFilter) {
                    res.setHeader("Access-Control-Allow-Origin", "*");
                    res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
                    res.setHeader("Access-Control-Allow-Headers", "X-Cluster-Token, Content-Type, Authorization");

                    if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
                        res.setStatus(204);
                        res.flushBuffer();
                        exchange.endExchange();
                        return;
                    }

                    executeRoute(req, res, resolution, registry);
                    sendResponse(res, exchange);
                    return;
                }

                BiConsumer<HttpRequest, HttpResponse> routeHandler = (r, s) -> {
                    try {
                        executeRoute(r, s, resolution, registry);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };

                HttpFilterChainImpl chain = new HttpFilterChainImpl(activeFilters, routeHandler);
                chain.doFilter(req, res);
                sendResponse(res, exchange);

            } catch (Exception e) {
                DebugUtils.error("UndertowHttpTransport: Exception caught in filter chain pipeline: " + e.getMessage(), e);
                try {
                    UndertowHttpResponseImpl res = new UndertowHttpResponseImpl(exchange);
                    errorHandler.handleException(res, e);
                    sendResponse(res, exchange);
                } catch (Exception ignored) {}
            }
        } finally {
            if (connectionRegistry != null && legacyCtx != null) {
                connectionRegistry.unregisterConnection(legacyCtx);
            }
        }
    }

    private void sendResponse(UndertowHttpResponseImpl res, HttpServerExchange exchange) {
        res.flushBuffer();
        if (res.hasBody()) {
            byte[] bytes = res.getBodyBytes();
            exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_LENGTH, String.valueOf(bytes.length));
            exchange.getResponseSender().send(java.nio.ByteBuffer.wrap(bytes));
        } else {
            exchange.endExchange();
        }
    }

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
            errorHandler.handleStatus(s, 404, "Unknown Route: " + r.getPath());
        }
    }

    private int getActiveRequestsCap() {
        String capProp = System.getProperty("gatebridge.active.requests.cap");
        if (capProp != null && !capProp.trim().isEmpty()) {
            try {
                return Integer.parseInt(capProp.trim());
            } catch (NumberFormatException ignored) {}
        }
        return performanceProfile != null ? performanceProfile.getActiveRequestsCap() : 0;
    }

    private boolean isFastPathEnabled() {
        String fastPathProp = System.getProperty("gatebridge.fastpath.enabled");
        if (fastPathProp != null && !fastPathProp.trim().isEmpty()) {
            return Boolean.parseBoolean(fastPathProp.trim());
        }
        return performanceProfile != null && performanceProfile.isFastPathEnabled();
    }

    private void handleError(HttpServerExchange exchange, Exception e) {
        DebugUtils.error("UndertowHttpTransport: Exception caught in pipeline: " + e.getMessage(), e);
        try {
            UndertowHttpResponseImpl res = new UndertowHttpResponseImpl(exchange);
            errorHandler.handleException(res, e);
            res.flushBuffer();
            exchange.endExchange();
        } catch (Exception ignored) {}
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
            running = false;
            if (virtualExecutor != null) {
                virtualExecutor.shutdown();
            }
            DebugUtils.info("HTTP Transport (Undertow) stopped.");
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private static final ThreadLocal<FastPrintWriter> FAST_WRITER = ThreadLocal.withInitial(FastPrintWriter::new);

    private static class FastPrintWriter extends java.io.PrintWriter {
        private static class StringBuilderWriter extends java.io.Writer {
            final StringBuilder sb = new StringBuilder(512);

            @Override
            public void write(char[] cbuf, int off, int len) {
                sb.append(cbuf, off, len);
            }

            @Override
            public void write(String str, int off, int len) {
                sb.append(str, off, off + len);
            }

            @Override
            public void write(int c) {
                sb.append((char)c);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}

            void reset() {
                sb.setLength(0);
            }

            byte[] toBytes() {
                return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        }

        private final StringBuilderWriter sbw;

        public FastPrintWriter() {
            this(new StringBuilderWriter());
        }

        private FastPrintWriter(StringBuilderWriter sbw) {
            super(sbw);
            this.sbw = sbw;
        }

        public void reset() {
            sbw.reset();
            clearError();
        }

        public byte[] toBytes() {
            return sbw.toBytes();
        }
    }
}
