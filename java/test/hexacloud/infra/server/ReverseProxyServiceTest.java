package hexacloud.infra.server;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.server.filter.HttpRequest;
import hexacloud.core.server.filter.HttpResponse;
import hexacloud.core.utils.network.HttpProxyClient;
import hexacloud.core.utils.network.ProxyResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ReverseProxyServiceTest {

    @Test
    public void testAppendHeaderImmutableInputProtectionAndCaseInsensitivity() throws Exception {
        HttpProxyClient mockClient = mock(HttpProxyClient.class);
        HttpErrorHandler mockHandler = mock(HttpErrorHandler.class);
        ReverseProxyService service = new ReverseProxyService(mockClient, mockHandler);

        Cluster cluster = new Cluster("test-cluster");
        cluster.setRoutingMode(Cluster.RoutingMode.HYBRID);
        ServerNode node = new ServerNode("node1", "http://localhost", 8080, NodeStatus.ONLINE, false);
        cluster.registerServer(node);

        HttpRequest req = mock(HttpRequest.class);
        HttpResponse res = mock(HttpResponse.class);

        // Immutable map and immutable list in req.getHeaders()
        List<String> immutableXff = List.of("10.0.0.1");
        Map<String, List<String>> originalHeaders = Map.of(
                "x-forwarded-for", immutableXff,
                "Host", List.of("example.com")
        );

        when(req.getHeaders()).thenReturn(originalHeaders);
        when(req.getClientIp()).thenReturn("192.168.1.1");
        when(req.getHeader("Host")).thenReturn("example.com");
        when(req.getMethod()).thenReturn("GET");
        when(req.getBody()).thenReturn(new ByteArrayInputStream(new byte[0]));

        ProxyResponse mockResponse = mock(ProxyResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.headers()).thenReturn(Collections.emptyMap());
        when(mockResponse.bodyStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(mockClient.execute(anyString(), anyString(), anyMap(), any(), anyInt())).thenReturn(mockResponse);

        service.proxyRequest(req, res, cluster, "/api", 5000);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockClient).execute(contains("http://127.0.0.1:8080/api"), eq("GET"), headersCaptor.capture(), any(), anyInt());

        Map<String, List<String>> sentHeaders = headersCaptor.getValue();
        // Check case-insensitive match on "x-forwarded-for"
        assertTrue(sentHeaders.containsKey("x-forwarded-for"));
        List<String> xffValues = sentHeaders.get("x-forwarded-for");
        assertEquals(List.of("10.0.0.1", "192.168.1.1"), xffValues);

        // Verify original immutable list was NOT mutated
        assertEquals(List.of("10.0.0.1"), immutableXff);

        // Check new header addition (X-Forwarded-Proto)
        assertTrue(sentHeaders.containsKey("X-Forwarded-Proto"));
        assertEquals(List.of("http"), sentHeaders.get("X-Forwarded-Proto"));
    }

    @Test
    public void testUrlSlicingLocalhostPrefixes() throws Exception {
        HttpProxyClient mockClient = mock(HttpProxyClient.class);
        HttpErrorHandler mockHandler = mock(HttpErrorHandler.class);
        ReverseProxyService service = new ReverseProxyService(mockClient, mockHandler);

        Cluster cluster = new Cluster("test-cluster");
        cluster.setRoutingMode(Cluster.RoutingMode.HYBRID);
        ServerNode node = new ServerNode("node1", "https://localhost", 8443, NodeStatus.ONLINE, false);
        cluster.registerServer(node);

        HttpRequest req = mock(HttpRequest.class);
        HttpResponse res = mock(HttpResponse.class);
        when(req.getHeaders()).thenReturn(Collections.emptyMap());
        when(req.getMethod()).thenReturn("GET");
        when(req.getBody()).thenReturn(new ByteArrayInputStream(new byte[0]));

        ProxyResponse mockResponse = mock(ProxyResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.headers()).thenReturn(Collections.emptyMap());
        when(mockResponse.bodyStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(mockClient.execute(anyString(), anyString(), anyMap(), any(), anyInt())).thenReturn(mockResponse);

        service.proxyRequest(req, res, cluster, "/secure", 5000);

        verify(mockClient).execute(eq("https://127.0.0.1:8443/secure"), eq("GET"), anyMap(), any(), anyInt());
    }
}
