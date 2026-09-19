package hexacloud.core.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.config.ClusterConfig;
import hexacloud.core.contracts.ServerOperations;
import hexacloud.core.server.connection.ConnectionRegistry;
import hexacloud.core.server.route.RouteRule;
import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.server.route.ClusterController;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.core.utils.concurrent.ThreadManager;
import hexacloud.infra.server.HttpTransport;
import hexacloud.infra.server.UndertowHttpTransport;
import hexacloud.infra.server.TcpProxyTransport;
import hexacloud.infra.server.TelnetTransport;
import hexacloud.infra.server.WsTransport;

public class ServerManager implements ServerOperations {

    private final List<Cluster> clusters;
    protected final ClusterEventBusManager eventManager;
    private final RouteRegistry routeRegistry;
    private final List<ServerTransport> activeTransports = new ArrayList<>();
    private final List<hexacloud.core.server.filter.HttpFilter> customFilters = new CopyOnWriteArrayList<>();
    private final List<RouteRule> routeRules = new CopyOnWriteArrayList<>();
    private final ConnectionRegistry connectionRegistry = new ConnectionRegistry();
    private ScheduledExecutorService sweeper;
    
    private boolean telnetEnabled = false;
    private boolean httpEnabled = false;
    private boolean wsEnabled = false;
    private boolean tcpProxyEnabled = false;
    private int port = ClusterConfig.DEFAULT_SERVER_PORT;
    private hexacloud.core.server.HttpEngine httpEngine = hexacloud.core.server.HttpEngine.JDK_DEFAULT;
    private hexacloud.core.server.PerformanceProfile performanceProfile = hexacloud.core.server.PerformanceProfile.STANDARD;
    private hexacloud.core.ports.SslContextPort sslContextPort;
    private int tcpSoTimeout = 30000;
    private boolean tcpKeepAlive = true;
    private boolean adminEnabled = Boolean.parseBoolean(System.getProperty("gatebridge.admin.enabled", "true"));
    private String adminHost = System.getProperty("gatebridge.admin.host", "127.0.0.1");
    private int adminPort = Integer.getInteger("gatebridge.admin.port", 9090);
    private hexacloud.infra.server.UndertowManagementTransport managementTransport;

    /**
     * Primary constructor accepting all clusters. Used by LocalGatewayAdapter.
     */
    public ServerManager(List<Cluster> clusters, ClusterEventBusManager eventManager) {
        this.clusters = clusters != null ? clusters : new ArrayList<>();
        this.eventManager = eventManager;
        this.routeRegistry = new RouteRegistry();
        if (this.clusters.isEmpty()) {
            hexacloud.core.cluster.Cluster defaultCluster = hexacloud.core.cluster.ClusterRegistry.getInstance().getCluster(ClusterConfig.DEFAULT_CLUSTER_NAME);
            if (defaultCluster == null) {
                defaultCluster = new hexacloud.core.cluster.Cluster(ClusterConfig.DEFAULT_CLUSTER_NAME, eventManager);
            }
            this.routeRegistry.registerController(new ClusterController(defaultCluster));
        } else {
            for (Cluster cluster : this.clusters) {
                this.routeRegistry.registerController(new ClusterController(cluster));
            }
        }
    }

    /**
     * Convenience constructor for single-cluster usage (backward compatible).
     */
    public ServerManager(Cluster cluster, ClusterEventBusManager eventManager) {
        this(cluster != null ? List.of(cluster) : List.of(), eventManager);
    }

    public ServerManager(int port, Cluster cluster, ClusterEventBusManager eventManager) {
        this(cluster != null ? List.of(cluster) : List.of(), eventManager);
        this.port = port;
    }

    public void autoRegisterControllers(List<String> scanPackages) {
        List<String> packages = new ArrayList<>();
        if (scanPackages != null) {
            packages.addAll(scanPackages);
        }
        if (packages.isEmpty()) {
            String basePkg = hexacloud.core.utils.common.PathUtils.getAppBasePackage();
            if (!basePkg.isEmpty()) {
                packages.add(basePkg);
            }
            packages.add("hexacloud");
        }

        for (String pkg : packages) {
            try {
                List<Class<? extends hexacloud.core.server.route.RouteController>> controllers =
                        hexacloud.core.utils.reflection.ClassScanner.scanPackage(pkg, hexacloud.core.server.route.RouteController.class);
                for (Class<? extends hexacloud.core.server.route.RouteController> clazz : controllers) {
                    if (clazz.getName().equals(ClusterController.class.getName())) {
                        continue;
                    }
                    
                    try {
                        hexacloud.core.server.route.RouteController controller = null;
                        Cluster firstCluster = clusters.isEmpty() ? null : clusters.get(0);
                        try {
                            if (firstCluster != null) {
                                java.lang.reflect.Constructor<? extends hexacloud.core.server.route.RouteController> ctor = clazz.getDeclaredConstructor(Cluster.class);
                                ctor.setAccessible(true);
                                controller = ctor.newInstance(firstCluster);
                            }
                        } catch (NoSuchMethodException e) {
                            java.lang.reflect.Constructor<? extends hexacloud.core.server.route.RouteController> ctor = clazz.getDeclaredConstructor();
                            ctor.setAccessible(true);
                            controller = ctor.newInstance();
                        }

                        if (controller != null) {
                            this.routeRegistry.registerController(controller);
                            for (Cluster c : this.clusters) {
                                c.getRouteRegistry().registerController(controller);
                            }
                            DebugUtils.info("RouteScanner: Auto-discovered and registered controller: " + clazz.getName());
                        }
                    } catch (Exception e) {
                        DebugUtils.error("RouteScanner: Failed to auto-instantiate controller " + clazz.getName(), e);
                    }
                }
            } catch (Exception e) {
                DebugUtils.error("RouteScanner: Failed to scan package " + pkg + " for RouteControllers", e);
            }
        }
    }

    public ServerManager enableTelnet(boolean enabled) {
        this.telnetEnabled = enabled;
        DebugUtils.info("ServerManager: Telnet transport " + (enabled ? "AUTHORIZED" : "DISABLED"));
        return this;
    }

    public ServerManager enableHttp(boolean enabled) {
        this.httpEnabled = enabled;
        DebugUtils.info("ServerManager: HTTP transport " + (enabled ? "AUTHORIZED" : "DISABLED"));
        return this;
    }

    public ServerManager enableWs(boolean enabled) {
        this.wsEnabled = enabled;
        DebugUtils.info("ServerManager: WebSocket transport " + (enabled ? "AUTHORIZED" : "DISABLED"));
        return this;
    }

    public ServerManager enableTcpProxy(boolean enabled) {
        this.tcpProxyEnabled = enabled;
        DebugUtils.info("ServerManager: TCP Proxy transport " + (enabled ? "AUTHORIZED" : "DISABLED"));
        return this;
    }

    public ServerManager tcpSoTimeout(int timeoutMs) {
        this.tcpSoTimeout = timeoutMs;
        return this;
    }

    public ServerManager tcpKeepAlive(boolean enabled) {
        this.tcpKeepAlive = enabled;
        return this;
    }

    public boolean isTelnetEnabled() {
        return telnetEnabled;
    }

    public boolean isHttpEnabled() {
        return httpEnabled;
    }

    public boolean isWsEnabled() {
        return wsEnabled;
    }

    public boolean isTcpProxyEnabled() {
        return tcpProxyEnabled;
    }

    public hexacloud.core.server.HttpEngine getHttpEngine() {
        return httpEngine;
    }

    public void setHttpEngine(hexacloud.core.server.HttpEngine httpEngine) {
        if (httpEngine != null) {
            this.httpEngine = httpEngine;
        }
    }

    public hexacloud.core.server.PerformanceProfile getPerformanceProfile() {
        return performanceProfile;
    }

    public void setPerformanceProfile(hexacloud.core.server.PerformanceProfile performanceProfile) {
        if (performanceProfile != null) {
            this.performanceProfile = performanceProfile;
            for (ServerTransport transport : activeTransports) {
                transport.setPerformanceProfile(performanceProfile);
            }
        }
    }

    public hexacloud.core.ports.SslContextPort getSslContext() {
        return sslContextPort;
    }

    public void setSslContext(hexacloud.core.ports.SslContextPort sslContextPort) {
        this.sslContextPort = sslContextPort;
    }

    public ServerManager registerFilter(hexacloud.core.server.filter.HttpFilter filter) {
        this.customFilters.add(filter);
        return this;
    }

    public List<hexacloud.core.server.filter.HttpFilter> getCustomFilters() {
        return customFilters;
    }

    public ConnectionRegistry getConnectionRegistry() {
        return connectionRegistry;
    }

    public ServerManager setAdminEnabled(boolean enabled) {
        this.adminEnabled = enabled;
        return this;
    }

    public boolean isAdminEnabled() {
        return adminEnabled;
    }

    public ServerManager setAdminHost(String host) {
        if (host != null && !host.trim().isEmpty()) {
            this.adminHost = host.trim();
        }
        return this;
    }

    public String getAdminHost() {
        return adminHost;
    }

    public ServerManager setAdminPort(int port) {
        if (port > 0) {
            this.adminPort = port;
        }
        return this;
    }

    public int getAdminPort() {
        return adminPort;
    }

    @Override
    public ServerManager listen(int port) {
        DebugUtils.info("ServerManager: Starting authorized protocol listeners on base port " + port + "...");
        
        // Stop any running transports before starting new ones
        stopTransports();

        if (adminEnabled) {
            managementTransport = new hexacloud.infra.server.UndertowManagementTransport(adminHost, adminPort, routeRegistry);
            managementTransport.start();
            DebugUtils.info("Management Transport (Undertow) listening on " + adminHost + ":" + adminPort);
        }

        if (sweeper == null || sweeper.isShutdown()) {
            sweeper = ThreadManager.newScheduledThreadPool(1, "ConnectionCleaner");
            sweeper.scheduleAtFixedRate(() -> connectionRegistry.reclaimIdleConnections(tcpSoTimeout), 10, 10, TimeUnit.SECONDS);
        }

        if(telnetEnabled) {
            ServerTransport telnet = new TelnetTransport();
            telnet.setConnectionRegistry(this.connectionRegistry);
            telnet.listen(port, routeRegistry, clusters, customFilters);
            activeTransports.add(telnet);
        }
        
        if(httpEnabled) {
            ServerTransport http;
            if (httpEngine == hexacloud.core.server.HttpEngine.UNDERTOW) {
                UndertowHttpTransport undertowHttp = new UndertowHttpTransport();
                undertowHttp.setSslContext(this.sslContextPort);
                http = undertowHttp;
            } else {
                HttpTransport jdkHttp = new HttpTransport();
                jdkHttp.setSslContext(this.sslContextPort);
                http = jdkHttp;
            }
            http.setPerformanceProfile(this.performanceProfile);
            http.setConnectionRegistry(this.connectionRegistry);
            // HTTP runs on port + HTTP_PORT_OFFSET
            http.listen(port + ClusterConfig.HTTP_PORT_OFFSET, routeRegistry, clusters, customFilters);
            activeTransports.add(http);
        }
        
        if(wsEnabled) {
            ServerTransport ws = new WsTransport();
            ws.setConnectionRegistry(this.connectionRegistry);
            // WS runs on port + WS_PORT_OFFSET
            ws.listen(port + ClusterConfig.WS_PORT_OFFSET, routeRegistry, clusters, customFilters);
            activeTransports.add(ws);
        }

        if(tcpProxyEnabled) {
            TcpProxyTransport tcpProxy = new TcpProxyTransport();
            tcpProxy.setSoTimeout(this.tcpSoTimeout);
            tcpProxy.setKeepAlive(this.tcpKeepAlive);
            tcpProxy.setConnectionRegistry(this.connectionRegistry);
            // TCP Proxy runs on port + TCP_PORT_OFFSET
            tcpProxy.listen(port + ClusterConfig.TCP_PORT_OFFSET, routeRegistry, clusters, customFilters);
            activeTransports.add(tcpProxy);
        }
        
        if(activeTransports.isEmpty() && !adminEnabled) {
            DebugUtils.error("ServerManager: Cannot listen. No protocols were authorized! All are disabled.");
        }
        return this;
    }

    @Override
    public ServerManager listen() {
        listen(this.port);
        return this;
    }

    @Override
    public ServerManager stop() {
        stopTransports();
        return this;
    }

    private void stopTransports() {
        if (managementTransport != null) {
            managementTransport.stop();
            managementTransport = null;
        }
        for(ServerTransport transport : activeTransports) {
            if(transport != null && transport.isRunning()) {
                transport.stop();
            }
        }
        activeTransports.clear();
        connectionRegistry.closeAll();
        if (sweeper != null && !sweeper.isShutdown()) {
            sweeper.shutdownNow();
            sweeper = null;
        }
    }

    /**
     * Register a custom route controller to expose additional business command endpoints.
     */
    public ServerManager registerRouteController(hexacloud.core.server.route.RouteController controller) {
        this.routeRegistry.registerController(controller);
        for (Cluster c : this.clusters) {
            c.getRouteRegistry().registerController(controller);
        }
        return this;
    }

    public void addRouteRule(RouteRule rule) {
        if (rule == null) {
            return;
        }
        this.routeRules.add(rule);
        this.routeRegistry.addRouteRule(rule);
    }

    public List<RouteRule> getRouteRules() {
        return routeRules;
    }
}
