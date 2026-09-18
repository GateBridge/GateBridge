package hexacloud.infra.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SystemMetricsCollectorTest {

    @Test
    public void testEnvironmentInfoCollection() {
        SystemMetricsCollector collector = new SystemMetricsCollector();
        SystemMetricsCollector.EnvironmentInfo env = collector.getEnvironmentInfo();

        assertNotNull(env);
        assertNotNull(env.getOsName());
        assertNotNull(env.getOsArch());
        assertNotNull(env.getOsVersion());
        assertTrue(env.getAvailableProcessors() > 0, "Available processors should be > 0");
        assertNotNull(env.getJavaVersion());
        assertNotNull(env.getJavaVendor());
        assertTrue(env.getMaxHeapMb() > 0, "Max heap MB should be > 0");
    }

    @Test
    public void testSnapshotSampling() {
        SystemMetricsCollector collector = new SystemMetricsCollector();
        SystemMetricsCollector.SystemSnapshot snap1 = collector.captureSnapshot();
        assertNotNull(snap1);
        assertTrue(snap1.getTimestampMs() > 0, "Timestamp should be positive");

        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {}

        SystemMetricsCollector.SystemSnapshot snap2 = collector.captureSnapshot();
        assertNotNull(snap2);

        assertTrue(snap2.getHeapUsedMb() >= 0, "Heap used MB should be >= 0");
        assertTrue(snap2.getActiveThreads() > 0, "Active threads should be > 0");
        assertTrue(snap2.getProcessCpuLoad() >= 0.0, "CPU load should be >= 0.0");
        assertTrue(snap2.getTotalGcCount() >= 0, "GC count should be >= 0");
        assertTrue(snap2.getTotalGcTimeMs() >= 0, "GC time should be >= 0");

        SystemMetricsCollector.MetricsDelta delta = collector.computeDelta(snap1, snap2);
        assertNotNull(delta);
        assertTrue(delta.getCpuPercent() >= 0.0, "CPU % in delta should be >= 0.0");
        assertTrue(delta.getHeapUsedMb() >= 0, "Heap used MB in delta should be >= 0");
        assertTrue(delta.getGcCountDelta() >= 0, "GC count delta should be >= 0");
        assertTrue(delta.getGcTimeMsDelta() >= 0, "GC time delta should be >= 0");
        assertTrue(delta.getActiveThreads() > 0, "Active threads in delta should be > 0");
    }
}
