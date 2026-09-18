package hexacloud.infra.benchmark.protocol;

import hexacloud.infra.benchmark.MetricsCollector;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class HttpBenchmarkClient {
    private final HttpClient httpClient;
    private final MetricsCollector metricsCollector;
    private final Duration timeout;

    public HttpBenchmarkClient(MetricsCollector metricsCollector) {
        this(metricsCollector, Duration.ofSeconds(5));
    }

    public HttpBenchmarkClient(MetricsCollector metricsCollector, Duration timeout) {
        this.metricsCollector = metricsCollector;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    public void executeRequest(String targetUrl) {
        sendRequest(targetUrl);
    }

    public void sendRequest(String targetUrl) {
        sendRequest(targetUrl, null);
    }

    public void sendRequest(String targetUrl, AtomicBoolean running) {
        long startTime = System.currentTimeMillis();
        int statusCode = -1;
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(timeout)
                    .GET();
            
            // Extract token if present in target URL query string
            if (targetUrl.contains("token=")) {
                String token = targetUrl.substring(targetUrl.indexOf("token=") + 6);
                if (token.contains("&")) {
                    token = token.substring(0, token.indexOf("&"));
                }
                reqBuilder.header("X-Cluster-Token", token);
            }

            HttpRequest request = reqBuilder.build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            statusCode = response.statusCode();
        } catch (Exception e) {
            statusCode = -1;
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            if (statusCode > 0 || ((running == null || running.get()) && !Thread.currentThread().isInterrupted())) {
                if (statusCode > 0) {
                    metricsCollector.recordRequest(latencyMs, statusCode);
                } else {
                    metricsCollector.recordRequest(latencyMs, false);
                }
            }
        }
    }

    public void runClientLoop(String targetUrl, AtomicBoolean running) {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            sendRequest(targetUrl, running);
        }
    }
}
