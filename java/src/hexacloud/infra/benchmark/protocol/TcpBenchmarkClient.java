package hexacloud.infra.benchmark.protocol;

import hexacloud.infra.benchmark.MetricsCollector;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class TcpBenchmarkClient {
    private final MetricsCollector metricsCollector;
    private final int timeoutMs;
    private final boolean persistentMode;

    public TcpBenchmarkClient(MetricsCollector metricsCollector) {
        this(metricsCollector, 5000, false);
    }

    public TcpBenchmarkClient(MetricsCollector metricsCollector, int timeoutMs) {
        this(metricsCollector, timeoutMs, false);
    }

    public TcpBenchmarkClient(MetricsCollector metricsCollector, int timeoutMs, boolean persistentMode) {
        this.metricsCollector = metricsCollector;
        this.timeoutMs = timeoutMs;
        this.persistentMode = persistentMode;
    }

    public void executeRequest(String host, int port) {
        executeRequest(host, port, null);
    }

    public void executeRequest(String host, int port, byte[] payload) {
        executeRequest(host, port, payload, null);
    }

    public void executeRequest(String host, int port, byte[] payload, AtomicBoolean running) {
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
            if (success || ((running == null || running.get()) && !Thread.currentThread().isInterrupted())) {
                metricsCollector.recordRequest(latencyMs, success);
            }
        }
    }

    public void sendRequest(String target) {
        String host = parseHost(target);
        int port = parsePort(target);
        executeRequest(host, port);
    }

    public void sendRequest(String host, int port) {
        executeRequest(host, port);
    }

    public void runClientLoop(String target, AtomicBoolean running) {
        String host = parseHost(target);
        int port = parsePort(target);
        runClientLoop(host, port, running);
    }

    public void runClientLoop(String host, int port, AtomicBoolean running) {
        byte[] pingPayload = "PING\n".getBytes(StandardCharsets.UTF_8);
        if (!persistentMode) {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                executeRequest(host, port, pingPayload, running);
            }
            return;
        }

        Socket socket = null;
        OutputStream os = null;
        InputStream is = null;
        byte[] buf = new byte[1024];

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            long startTime = System.currentTimeMillis();
            boolean success = false;
            try {
                if (socket == null || socket.isClosed() || !socket.isConnected()) {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(host, port), timeoutMs);
                    socket.setSoTimeout(timeoutMs);
                    os = socket.getOutputStream();
                    is = socket.getInputStream();
                }
                os.write(pingPayload);
                os.flush();
                int read = is.read(buf);
                success = (read >= 0);
                if (!success) {
                    closeQuietly(socket);
                    socket = null;
                    os = null;
                    is = null;
                }
            } catch (Exception e) {
                success = false;
                closeQuietly(socket);
                socket = null;
                os = null;
                is = null;
                try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            } finally {
                long latencyMs = System.currentTimeMillis() - startTime;
                if (success || (running.get() && !Thread.currentThread().isInterrupted())) {
                    metricsCollector.recordRequest(latencyMs, success);
                }
            }
        }
        closeQuietly(socket);
    }

    private static String parseHost(String target) {
        if (target == null || target.isBlank()) return "127.0.0.1";
        if (target.contains("://")) {
            try {
                return URI.create(target).getHost();
            } catch (Exception ignored) {}
        }
        if (target.contains(":")) {
            return target.split(":")[0];
        }
        return target;
    }

    private static int parsePort(String target) {
        if (target == null || target.isBlank()) return 8080;
        if (target.contains("://")) {
            try {
                URI uri = URI.create(target);
                if (uri.getPort() != -1) return uri.getPort();
                return target.startsWith("https") || target.startsWith("wss") ? 443 : 80;
            } catch (Exception ignored) {}
        }
        if (target.contains(":")) {
            try {
                return Integer.parseInt(target.split(":")[1]);
            } catch (Exception ignored) {}
        }
        return 8080;
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }
}
