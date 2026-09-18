package hexacloud.core.utils.network;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Executors;

public class JdkHttpProxyClient implements HttpProxyClient {
    static {
        System.setProperty("jdk.httpclient.connectionPoolSize", "500");
    }

    private final HttpClient client;

    public JdkHttpProxyClient() {
        System.setProperty("jdk.httpclient.connectionPoolSize", "500");
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(Executors.newCachedThreadPool())
                .build();
    }

    @Override
    public ProxyResponse execute(String targetUrl, String method, Map<String, List<String>> headers, InputStream body, int timeoutMs) throws Exception {
        boolean hasBody = false;
        if (headers != null) {
            List<String> contentLengths = headers.get("Content-Length");
            if (contentLengths == null || contentLengths.isEmpty()) {
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    if ("Content-Length".equalsIgnoreCase(entry.getKey())) {
                        contentLengths = entry.getValue();
                        break;
                    }
                }
            }
            if (contentLengths != null && !contentLengths.isEmpty()) {
                try {
                    long len = Long.parseLong(contentLengths.get(0).trim());
                    if (len > 0) {
                        hasBody = true;
                    }
                } catch (NumberFormatException ignored) {}
            }
            if (!hasBody) {
                List<String> encoding = headers.get("Transfer-Encoding");
                if (encoding == null || encoding.isEmpty()) {
                    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                        if ("Transfer-Encoding".equalsIgnoreCase(entry.getKey())) {
                            encoding = entry.getValue();
                            break;
                        }
                    }
                }
                if (encoding != null && !encoding.isEmpty() && encoding.get(0).toLowerCase().contains("chunked")) {
                    hasBody = true;
                }
            }
        }

        HttpRequest.BodyPublisher publisher;
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            publisher = HttpRequest.BodyPublishers.noBody();
        } else if (hasBody && body != null) {
            publisher = HttpRequest.BodyPublishers.ofInputStream(() -> body);
        } else {
            publisher = HttpRequest.BodyPublishers.noBody();
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .method(method, publisher)
                .timeout(Duration.ofMillis(timeoutMs > 0 ? timeoutMs : 10000));

        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                String name = entry.getKey();
                if (name == null || name.equalsIgnoreCase("Host") || name.equalsIgnoreCase("Content-Length")) {
                    continue;
                }
                for (String val : entry.getValue()) {
                    if (val != null) {
                        builder.header(name, val);
                    }
                }
            }
        }

        HttpResponse<InputStream> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());

        return new ProxyResponse(
                resp.statusCode(),
                resp.headers().map(),
                resp.body()
        );
    }
}
