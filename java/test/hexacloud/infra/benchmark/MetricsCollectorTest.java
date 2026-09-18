package hexacloud.infra.benchmark;

import hexacloud.infra.benchmark.protocol.HttpBenchmarkClient;
import hexacloud.infra.benchmark.protocol.TcpBenchmarkClient;
import hexacloud.infra.benchmark.protocol.TelnetBenchmarkClient;
import hexacloud.infra.benchmark.protocol.WsBenchmarkClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MetricsCollectorTest {

    @Test
    public void testRecordRequestAndCounts() {
        MetricsCollector collector = new MetricsCollector();
        assertEquals(0, collector.getTotalRequests());
        assertEquals(0, collector.getErrorRequests());
        assertEquals(0.0, collector.getErrorPercentage(), 0.001);

        for (int i = 0; i < 10; i++) {
            collector.recordRequest(10 + i, true);
        }
        collector.recordRequest(50, false);
        collector.recordRequest(60, false);

        assertEquals(12, collector.getTotalRequests());
        assertEquals(2, collector.getErrorRequests());
        assertEquals((2.0 / 12.0) * 100.0, collector.getErrorPercentage(), 0.001);
    }

    @Test
    public void testGetPercentileLatency() {
        MetricsCollector collector = new MetricsCollector();
        assertEquals(0, collector.getPercentileLatency(50.0));

        for (int i = 1; i <= 100; i++) {
            collector.recordRequest(i, true);
        }

        assertEquals(100, collector.getPercentileLatency(50.0));
        assertEquals(100, collector.getPercentileLatency(90.0));
        assertEquals(100, collector.getPercentileLatency(99.0));
        assertEquals(250, collector.getPercentileLatency(100.0));
        assertEquals(5, collector.getPercentileLatency(0.0));
    }

    @Test
    public void testReset() {
        MetricsCollector collector = new MetricsCollector();
        collector.recordRequest(100, true);
        collector.recordRequest(200, false);

        assertEquals(2, collector.getTotalRequests());
        assertEquals(1, collector.getErrorRequests());

        collector.reset();

        assertEquals(0, collector.getTotalRequests());
        assertEquals(0, collector.getErrorRequests());
        assertEquals(0.0, collector.getErrorPercentage(), 0.001);
        assertEquals(0, collector.getPercentileLatency(50.0));
    }

    @Test
    public void testBoundedLatencies() {
        MetricsCollector collector = new MetricsCollector();
        for (int i = 0; i < 100_005; i++) {
            collector.recordRequest(i % 100, true);
        }
        assertEquals(100_005, collector.getTotalRequests());
        assertTrue(collector.getPercentileLatency(50.0) >= 0);
    }

    @Test
    public void testAllProtocolBenchmarkClients() {
        MetricsCollector collector = new MetricsCollector();
        HttpBenchmarkClient httpClient = new HttpBenchmarkClient(collector);
        TcpBenchmarkClient tcpClient = new TcpBenchmarkClient(collector);
        WsBenchmarkClient wsClient = new WsBenchmarkClient(collector);
        TelnetBenchmarkClient telnetClient = new TelnetBenchmarkClient(collector);

        // Requesting unroutable / closed local port should fail gracefully and record error
        httpClient.executeRequest("http://127.0.0.1:65534/nonexistent");
        tcpClient.executeRequest("127.0.0.1", 65534);
        wsClient.executeRequest("ws://127.0.0.1:65534/ws");
        telnetClient.executeRequest("127.0.0.1", 65534);

        assertEquals(4, collector.getTotalRequests());
        assertEquals(4, collector.getErrorRequests());
        assertEquals(100.0, collector.getErrorPercentage(), 0.001);
    }
}
