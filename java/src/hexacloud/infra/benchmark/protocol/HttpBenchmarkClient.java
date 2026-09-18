package hexacloud.infra.benchmark.protocol;

import hexacloud.infra.benchmark.MetricsCollector;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
        long startTime = System.currentTimeMillis();
        boolean success = false;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            success = (response.statusCode() >= 200 && response.statusCode() < 400);
        } catch (Exception e) {
            success = false;
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            metricsCollector.recordRequest(latencyMs, success);
        }
    }
}
