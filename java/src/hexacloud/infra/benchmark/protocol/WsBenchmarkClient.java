package hexacloud.infra.benchmark.protocol;

import hexacloud.infra.benchmark.MetricsCollector;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class WsBenchmarkClient {
    private final HttpClient httpClient;
    private final MetricsCollector metricsCollector;
    private final Duration timeout;

    public WsBenchmarkClient(MetricsCollector metricsCollector) {
        this(metricsCollector, Duration.ofSeconds(5));
    }

    public WsBenchmarkClient(MetricsCollector metricsCollector, Duration timeout) {
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
            String wsUrl = normalizeWsUrl(targetUrl);
            CompletableFuture<Boolean> frameReceived = new CompletableFuture<>();
            CompletableFuture<WebSocket> wsFuture = httpClient.newWebSocketBuilder()
                    .connectTimeout(timeout)
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            frameReceived.complete(true);
                            webSocket.request(1);
                            return CompletableFuture.completedFuture(null);
                        }

                        @Override
                        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                            frameReceived.complete(true);
                            webSocket.request(1);
                            return CompletableFuture.completedFuture(null);
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            frameReceived.completeExceptionally(error);
                        }
                    });

            WebSocket ws = wsFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            try {
                frameReceived.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                success = true;
            } catch (Exception e) {
                success = (ws != null && !ws.isOutputClosed());
            } finally {
                if (ws != null) {
                    try {
                        ws.sendClose(WebSocket.NORMAL_CLOSURE, "close");
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            success = false;
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            metricsCollector.recordRequest(latencyMs, success);
        }
    }

    public void runClientLoop(String targetUrl, AtomicBoolean running) {
        String wsUrl = normalizeWsUrl(targetUrl);
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            sendRequest(wsUrl);
        }
    }

    public static String normalizeWsUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) return "ws://127.0.0.1:8080";
        if (targetUrl.startsWith("http://")) {
            return "ws://" + targetUrl.substring(7);
        } else if (targetUrl.startsWith("https://")) {
            return "wss://" + targetUrl.substring(8);
        } else if (!targetUrl.startsWith("ws://") && !targetUrl.startsWith("wss://")) {
            return "ws://" + targetUrl;
        }
        return targetUrl;
    }
}
