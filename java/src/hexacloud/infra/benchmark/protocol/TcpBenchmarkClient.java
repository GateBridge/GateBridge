package hexacloud.infra.benchmark.protocol;

import hexacloud.infra.benchmark.MetricsCollector;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

public class TcpBenchmarkClient {
    private final MetricsCollector metricsCollector;
    private final int timeoutMs;

    public TcpBenchmarkClient(MetricsCollector metricsCollector) {
        this(metricsCollector, 5000);
    }

    public TcpBenchmarkClient(MetricsCollector metricsCollector, int timeoutMs) {
        this.metricsCollector = metricsCollector;
        this.timeoutMs = timeoutMs;
    }

    public void executeRequest(String host, int port) {
        executeRequest(host, port, null);
    }

    public void executeRequest(String host, int port, byte[] payload) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            if (payload != null && payload.length > 0) {
                OutputStream os = socket.getOutputStream();
                os.write(payload);
                os.flush();
                InputStream is = socket.getInputStream();
                byte[] buf = new byte[1024];
                int read = is.read(buf);
                success = (read >= 0);
            } else {
                success = socket.isConnected();
            }
        } catch (Exception e) {
            success = false;
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            metricsCollector.recordRequest(latencyMs, success);
        }
    }

    public void sendRequest(String target) {
        String host = target;
        int port = 80;
        if (target.startsWith("http://") || target.startsWith("https://") || target.contains("://")) {
            try {
                URI uri = URI.create(target);
                host = uri.getHost();
                port = uri.getPort() != -1 ? uri.getPort() : (target.startsWith("https://") ? 443 : 80);
            } catch (Exception ignored) {
            }
        } else if (target.contains(":")) {
            String[] parts = target.split(":");
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        }
        executeRequest(host, port);
    }

    public void sendRequest(String host, int port) {
        executeRequest(host, port);
    }
}
