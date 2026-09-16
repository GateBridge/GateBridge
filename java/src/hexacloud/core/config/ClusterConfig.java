package hexacloud.core.config;

import java.time.Duration;

import hexacloud.core.utils.network.HttpVersion;

/**
 * Global configuration defaults and system constants for the GateBridge framework.
 */
public class ClusterConfig {

    // Base Network & Port Defaults
    public static final int DEFAULT_SERVER_PORT = 3000;
    public static final int HTTP_PORT_OFFSET = 1;
    public static final int WS_PORT_OFFSET = 2;
    public static final int TCP_PORT_OFFSET = 3;

    // Cluster & Worker Defaults
    public static final int MAX_CLUSTER_SIZE = 10;
    public static final int MAX_WORKERS = 20;
    public static final String DEFAULT_CLUSTER_URI = "http://localhost";
    public static final String DEFAULT_CLUSTER_NAME = "DefaultCluster";

    // Scheduler & Network Defaults
    public static final int DEFAULT_PING_INTERVAL_SECONDS = 5;
    public static final int SCHEDULER_THREAD_POOL_SIZE = 1;
    public static final long AWAIT_TERMINATION_TIMEOUT_MS = 800;

    // Timeouts
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    public static final int DEFAULT_HTTP_TIMEOUT_MS = 10000;
    public static final int DEFAULT_AUTH_TIMEOUT_MS = 3000;
    public static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(2);
    public static final HttpVersion HTTP_VERSION = HttpVersion.requestVersion(HttpVersion.HTTP_1_1);

    // Buffer & I/O Allocations
    public static final int DEFAULT_BUFFER_SIZE = 8192;
    public static final int WS_BUFFER_SIZE = 4096;
    public static final int DEFAULT_SOCKET_BACKLOG = 8192;
    public static final int HTTP_SOCKET_BACKLOG = 1024;

    // Units
    public static final long BYTES_PER_MB = 1024L * 1024L;

    private ClusterConfig() {}
}
