package hexacloud.infra.benchmark.protocol;

import hexacloud.infra.benchmark.MetricsCollector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class TelnetBenchmarkClient {
    private final MetricsCollector metricsCollector;
    private final int timeoutMs;

    public TelnetBenchmarkClient(MetricsCollector metricsCollector) {
        this(metricsCollector, 5000);
    }

    public TelnetBenchmarkClient(MetricsCollector metricsCollector, int timeoutMs) {
        this.metricsCollector = metricsCollector;
        this.timeoutMs = timeoutMs;
    }

    public void executeRequest(String host, int port) {
        executeRequest(host, port, "PING\r\n");
    }

    public void executeRequest(String host, int port, String command) {
        executeRequest(host, port, command, null);
    }

    public void executeRequest(String host, int port, String command, AtomicBoolean running) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream os = socket.getOutputStream();
            String cmd = command.endsWith("\r\n") ? command : (command.endsWith("\n") ? command.replace("\n", "\r\n") : command + "\r\n");
            os.write(cmd.getBytes(StandardCharsets.UTF_8));
            os.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            success = (line != null);
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

    public void runClientLoop(String target, AtomicBoolean running) {
        String host = parseHost(target);
        int port = parsePort(target);
        runClientLoop(host, port, running);
    }

    public void runClientLoop(String host, int port, AtomicBoolean running) {
        Socket socket = null;
        OutputStream os = null;
        BufferedReader reader = null;
        byte[] cmdBytes = "PING\r\n".getBytes(StandardCharsets.UTF_8);

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            long startTime = System.currentTimeMillis();
            boolean success = false;
            try {
                if (socket == null || socket.isClosed() || !socket.isConnected()) {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(host, port), timeoutMs);
                    socket.setSoTimeout(timeoutMs);
                    os = socket.getOutputStream();
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                }
                os.write(cmdBytes);
                os.flush();
                String line = reader.readLine();
                success = (line != null);
                if (!success) {
                    closeQuietly(socket);
                    socket = null;
                    os = null;
                    reader = null;
                }
            } catch (Exception e) {
                success = false;
                closeQuietly(socket);
                socket = null;
                os = null;
                reader = null;
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
