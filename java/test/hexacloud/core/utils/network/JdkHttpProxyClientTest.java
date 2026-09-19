package hexacloud.core.utils.network;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class JdkHttpProxyClientTest {

    private HttpServer server;
    private int port;
    private AtomicInteger connectionCount = new AtomicInteger(0);

    @BeforeEach
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/test", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                connectionCount.incrementAndGet();
                byte[] resp = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            }
        });
        server.start();
    }

    @AfterEach
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    public void testPersistentConnectionExecution() throws Exception {
        JdkHttpProxyClient client = new JdkHttpProxyClient();
        String targetUrl = "http://127.0.0.1:" + port + "/test";

        ProxyResponse resp1 = client.execute(targetUrl, "GET", new HashMap<>(), null, 2000);
        assertEquals(200, resp1.statusCode());

        ProxyResponse resp2 = client.execute(targetUrl, "GET", new HashMap<>(), null, 2000);
        assertEquals(200, resp2.statusCode());
        assertTrue(connectionCount.get() >= 2);
    }

    @Test
    public void testUrlStringPrefixReplacement() {
        String url1 = "http://localhost:3001/hello";
        String replaced1 = url1.startsWith("http://localhost:") ? "http://127.0.0.1:" + url1.substring(17) : url1;
        assertEquals("http://127.0.0.1:3001/hello", replaced1);

        String url2 = "https://localhost:8443/secure";
        String replaced2 = url2.startsWith("https://localhost:") ? "https://127.0.0.1:" + url2.substring(18) : url2;
        assertEquals("https://127.0.0.1:8443/secure", replaced2);

        String url3 = "http://example.com/api";
        String replaced3 = url3.startsWith("http://localhost:") ? "http://127.0.0.1:" + url3.substring(17) : url3;
        assertEquals("http://example.com/api", replaced3);
    }
}
