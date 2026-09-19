package hexacloud.infra.server;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.model.ServerNode;
import hexacloud.core.server.filter.HttpRequest;
import hexacloud.core.server.filter.HttpResponse;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.core.utils.network.HttpProxyClient;
import hexacloud.core.utils.network.JdkHttpProxyClient;
import hexacloud.core.utils.network.ProxyResponse;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import hexacloud.core.server.PerformanceProfile;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ReverseProxyService {
    private static final int HTTP_LOCALHOST_PREFIX_LEN = "http://localhost:".length();
    private static final int HTTPS_LOCALHOST_PREFIX_LEN = "https://localhost:".length();
    private static final boolean IS_CONNECTION_POOL_EXPLICIT = System.getProperty("jdk.httpclient.connectionPoolSize") != null;
    private final HttpProxyClient proxyClient;
    private final HttpErrorHandler errorHandler;
    private static final java.util.concurrent.ConcurrentLinkedQueue<byte[]> BUFFER_POOL = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final AtomicInteger bufferCount = new AtomicInteger(0);
    private PerformanceProfile performanceProfile = PerformanceProfile.STANDARD;

    public ReverseProxyService(HttpProxyClient proxyClient, HttpErrorHandler errorHandler) {
        setPerformanceProfile(this.performanceProfile);
        this.proxyClient = proxyClient != null ? proxyClient : new JdkHttpProxyClient();
        this.errorHandler = errorHandler != null ? errorHandler : new DefaultHttpErrorHandler();
    }

    public void setPerformanceProfile(PerformanceProfile profile) {
        if (profile != null) {
            this.performanceProfile = profile;
            if (!IS_CONNECTION_POOL_EXPLICIT) {
                System.setProperty("jdk.httpclient.connectionPoolSize", String.valueOf(profile.getConnectionPoolSize()));
            }
        }
    }

    private int getMaxBufferPoolSize() {
        String capProp = System.getProperty("gatebridge.buffer.pool.max");
        if (capProp != null && !capProp.trim().isEmpty()) {
            try {
                return Integer.parseInt(capProp.trim());
            } catch (NumberFormatException ignored) {}
        }
        return performanceProfile != null ? performanceProfile.getMaxBufferPoolSize() : 64;
    }

    public void proxyRequest(HttpRequest req, HttpResponse res, Cluster targetCluster, String subpath, int timeoutMs) {
        proxyRequest(req, res, targetCluster, subpath, timeoutMs, false);
    }

    public void proxyRequest(HttpRequest req, HttpResponse res, Cluster targetCluster, String subpath, int timeoutMs, boolean matchedRouteRule) {
        if (!matchedRouteRule && targetCluster.getRoutingMode() == Cluster.RoutingMode.TELEMETRY_ONLY) {
            errorHandler.handleStatus(res, 403, "Forbidden - Load balancing is disabled for cluster: " + targetCluster.getClusterName());
            return;
        }

        ServerNode targetNode = targetCluster.selectNode();
        if (targetNode == null) {
            errorHandler.handleStatus(res, 503, "No active nodes in cluster: " + targetCluster.getClusterName());
            return;
        }

        long startTime = System.currentTimeMillis();

        String targetUrl = targetNode.getFullHost() + (subpath.startsWith("/") ? subpath : "/" + subpath);
        if (targetUrl.startsWith("http://localhost:")) {
            targetUrl = "http://127.0.0.1:" + targetUrl.substring(HTTP_LOCALHOST_PREFIX_LEN);
        } else if (targetUrl.startsWith("https://localhost:")) {
            targetUrl = "https://127.0.0.1:" + targetUrl.substring(HTTPS_LOCALHOST_PREFIX_LEN);
        }
        String query = req.getQuery();
        if (query != null && !query.isEmpty()) {
            targetUrl += "?" + query;
        }

        Map<String, List<String>> headers = new java.util.LinkedHashMap<>();
        if (req.getHeaders() != null) {
            for (Map.Entry<String, List<String>> entry : req.getHeaders().entrySet()) {
                headers.put(entry.getKey(), entry.getValue());
            }
        }
        
        // Add traceability headers
        String clientIp = req.getClientIp();
        if (clientIp != null) {
            appendHeader(headers, "X-Forwarded-For", clientIp);
        }
        String host = req.getHeader("Host");
        if (host != null) {
            appendHeader(headers, "X-Forwarded-Host", host);
        }
        appendHeader(headers, "X-Forwarded-Proto", "http");

        try (InputStream bodyIn = req.getBody()) {
            ProxyResponse response = proxyClient.execute(targetUrl, req.getMethod(), headers, bodyIn, timeoutMs);
            
            long startTimeForTelemetry = System.currentTimeMillis();
            long latencyMs = startTimeForTelemetry - startTime;
            
            // Passive Telemetry extraction
            Double cpuVal = parseHeaderDouble(response.headers(), "X-Telemetry-CPU", "X-Node-CPU");
            Double ramVal = parseHeaderDouble(response.headers(), "X-Telemetry-RAM", "X-Node-RAM");
            
            targetCluster.updateTelemetryServer(targetNode.host(), targetNode.port(), cpuVal, ramVal, null, (int) latencyMs, null);
            if (cpuVal != null) targetNode.setCpuUsage(cpuVal);
            if (ramVal != null) targetNode.setRamUsage(ramVal);
            targetNode.setLatencyMs((int) latencyMs);

            res.setStatus(response.statusCode());
            
            // Forward headers
            for (Map.Entry<String, List<String>> entry : response.headers().entrySet()) {
                String key = entry.getKey();
                if (key == null || key.equalsIgnoreCase("Transfer-Encoding") 
                        || key.equalsIgnoreCase("Connection") 
                        || key.equalsIgnoreCase("Keep-Alive") 
                        || key.equalsIgnoreCase("Upgrade")
                        || key.equalsIgnoreCase("Proxy-Connection")) {
                    continue;
                }
                for (String val : entry.getValue()) {
                    res.setHeader(key, val);
                }
            }

            try (InputStream in = response.bodyStream(); OutputStream out = res.getOutputStream()) {
                byte[] buffer = BUFFER_POOL.poll();
                if (buffer != null) {
                    bufferCount.decrementAndGet();
                } else {
                    buffer = new byte[8192];
                }
                try {
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();
                } finally {
                    if (bufferCount.get() < getMaxBufferPoolSize()) {
                        BUFFER_POOL.offer(buffer);
                        bufferCount.incrementAndGet();
                    }
                }
            }
        } catch (Exception e) {
            DebugUtils.error("ReverseProxyService: Proxy request failed to " + targetUrl, e);
            errorHandler.handleException(res, e);
        }
    }

    private Double parseHeaderDouble(Map<String, List<String>> headers, String... headerNames) {
        if (headers == null) return null;
        for (String hName : headerNames) {
            List<String> vals = headers.get(hName);
            if (vals == null || vals.isEmpty()) {
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    if (hName.equalsIgnoreCase(entry.getKey())) {
                        vals = entry.getValue();
                        break;
                    }
                }
            }
            if (vals != null && !vals.isEmpty()) {
                String val = vals.get(0);
                if (val != null && !val.trim().isEmpty()) {
                    try {
                        return Double.parseDouble(val.replace("%", "").trim());
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }

    private void appendHeader(Map<String, List<String>> headers, String key, String value) {
        if (value == null) return;
        String matchedKey = null;
        List<String> existingList = null;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                matchedKey = entry.getKey();
                existingList = entry.getValue();
                break;
            }
        }
        if (matchedKey != null) {
            List<String> newList = new ArrayList<>(existingList != null ? existingList : List.of());
            newList.add(value);
            headers.put(matchedKey, newList);
        } else {
            headers.put(key, new ArrayList<>(List.of(value)));
        }
    }
}
