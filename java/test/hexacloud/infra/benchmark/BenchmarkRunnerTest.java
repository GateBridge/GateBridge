package hexacloud.infra.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BenchmarkRunnerTest {

    @Test
    public void testParseArgsDefaults() {
        BenchmarkRunner.Config config = BenchmarkRunner.parseArgs(new String[]{});
        assertEquals("quick", config.getMode());
        assertEquals("http", config.getProtocol());
        assertEquals("http://127.0.0.1:8080", config.getTarget());
        assertEquals(3, config.getWarmupSeconds());
        assertEquals(10, config.getDurationSeconds());
        assertEquals(3, config.getRuns());
        assertEquals(1500, config.getCap());
        assertFalse(config.isHelpRequested());
    }

    @Test
    public void testParseArgsExplicitNewFlags() {
        String[] args = new String[]{
                "--mode=stress", "--protocol=tcp", "--target=127.0.0.1:9090",
                "--warmup=5s", "--duration=15s", "--runs=5", "--cap=3000"
        };
        BenchmarkRunner.Config config = BenchmarkRunner.parseArgs(args);
        assertEquals("stress", config.getMode());
        assertEquals("tcp", config.getProtocol());
        assertEquals("127.0.0.1:9090", config.getTarget());
        assertEquals(5, config.getWarmupSeconds());
        assertEquals(15, config.getDurationSeconds());
        assertEquals(5, config.getRuns());
        assertEquals(3000, config.getCap());
        assertFalse(config.isHelpRequested());
    }

    @Test
    public void testParseArgsUnlimitedCap() {
        BenchmarkRunner.Config config1 = BenchmarkRunner.parseArgs(new String[]{"--cap=0"});
        assertEquals(0, config1.getCap());

        BenchmarkRunner.Config config2 = BenchmarkRunner.parseArgs(new String[]{"--cap=unlimited"});
        assertEquals(0, config2.getCap());
    }

    @Test
    public void testParseArgsHelp() {
        BenchmarkRunner.Config config1 = BenchmarkRunner.parseArgs(new String[]{"--help"});
        assertTrue(config1.isHelpRequested());

        BenchmarkRunner.Config config2 = BenchmarkRunner.parseArgs(new String[]{"-h"});
        assertTrue(config2.isHelpRequested());
    }

    @Test
    public void testParseArgsInvalidMode() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            BenchmarkRunner.parseArgs(new String[]{"--mode=invalid"});
        });
        assertTrue(exception.getMessage().contains("Invalid mode"));
    }

    @Test
    public void testParseArgsInvalidProtocol() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            BenchmarkRunner.parseArgs(new String[]{"--protocol=invalid"});
        });
        assertTrue(exception.getMessage().contains("Invalid protocol"));
    }

    @Test
    public void test13GranularRampTiers() {
        int[] expectedTiers = {100, 500, 1000, 2500, 5000, 7500, 10000, 12500, 15000, 17500, 20000, 22500, 25000};
        assertArrayEquals(expectedTiers, BenchmarkRunner.STRESS_RAMP_UP_CLIENTS);
        assertEquals(13, BenchmarkRunner.STRESS_RAMP_UP_CLIENTS.length);
    }

    @Test
    public void testStatusClassificationLogic() {
        assertEquals("STABLE", BenchmarkRunner.determinePointHealthStatus(0.5, 100));
        assertEquals("DEGRADED", BenchmarkRunner.determinePointHealthStatus(0.5, 2050));
        assertEquals("FAILED", BenchmarkRunner.determinePointHealthStatus(1.5, 100));
        assertEquals("FAILED", BenchmarkRunner.determinePointHealthStatus(1.5, 2500));

        assertFalse(BenchmarkRunner.isStoppingCondition(0.5, 100));
        assertTrue(BenchmarkRunner.isStoppingCondition(1.2, 100));
        assertTrue(BenchmarkRunner.isStoppingCondition(0.5, 2050));
    }

    @Test
    public void testMedianCalculationsAcrossRuns() {
        SystemMetricsCollector.MetricsDelta delta = new SystemMetricsCollector.MetricsDelta(10.0, 100, 1, 10, 5);

        BenchmarkRunner.RunMetrics run1 = new BenchmarkRunner.RunMetrics(
                1000, 950, 20, 30, 50, 100.0, 95.0, 10, 20, 25, 30, 40, 5.0, delta);
        BenchmarkRunner.RunMetrics run2 = new BenchmarkRunner.RunMetrics(
                1200, 1150, 20, 30, 50, 120.0, 115.0, 12, 22, 27, 35, 45, 4.16, delta);
        BenchmarkRunner.RunMetrics run3 = new BenchmarkRunner.RunMetrics(
                1100, 1050, 20, 30, 50, 110.0, 105.0, 11, 21, 26, 32, 42, 4.54, delta);

        BenchmarkRunner.StepResult step = BenchmarkRunner.aggregateMedianStepResult(1, 100, List.of(run1, run2, run3));

        assertEquals(1100, step.getTotalRequests());
        assertEquals(1050, step.getGoodputRequests());
        assertEquals(110.0, step.getThroughputRps(), 0.001);
        assertEquals(105.0, step.getGoodputRps(), 0.001);
        assertEquals(11, step.getP50LatencyMs());
        assertEquals(26, step.getP95LatencyMs());
        assertEquals(32, step.getP99LatencyMs());
        assertEquals(4.54, step.getErrorPercentage(), 0.001);
        assertEquals("FAILED", step.getStatus());
    }

    @Test
    public void testExecutionInitializationQuickMode() {
        BenchmarkRunner runner = new BenchmarkRunner();
        BenchmarkRunner.Config config = new BenchmarkRunner.Config();
        config.setMode("quick");
        config.setProtocol("http");
        config.setTarget("http://127.0.0.1:65534/ping");
        config.setWarmupSeconds(0);
        config.setDurationSeconds(1);
        config.setRuns(1);

        BenchmarkRunner.BenchmarkResult result = runner.runBenchmark(config);

        assertNotNull(result);
        assertEquals("quick", result.getMode());
        assertEquals("http", result.getProtocol());
        assertEquals("http://127.0.0.1:65534/ping", result.getTarget());
        assertEquals(1, result.getSteps().size());

        BenchmarkRunner.StepResult step = result.getSteps().get(0);
        assertEquals(1, step.getStepIndex());
        assertEquals(100, step.getConcurrency());
        assertEquals("FAILED", step.getStatus());
        assertTrue(step.getErrorPercentage() > 0.0);

        String report = BenchmarkRunner.formatReport(result);
        assertNotNull(report);
        assertTrue(report.contains("GATEBRIDGE SCIENTIFIC BENCHMARK REPORT"));
        assertTrue(report.contains("QUICK SMOKE"));
    }

    @Test
    public void testStressModeContinuesThroughAll13Tiers() {
        BenchmarkRunner runner = new BenchmarkRunner();
        BenchmarkRunner.Config config = new BenchmarkRunner.Config();
        config.setMode("stress");
        config.setProtocol("http");
        config.setTarget("http://127.0.0.1:65534/ping");
        config.setWarmupSeconds(0);
        config.setDurationSeconds(1);
        config.setRuns(1);

        BenchmarkRunner.BenchmarkResult result = runner.runBenchmark(config);

        assertNotNull(result);
        assertEquals("stress", result.getMode());
        assertEquals(13, result.getSteps().size());
        assertEquals(0, result.getMaxStableConcurrency());

        String report = BenchmarkRunner.formatReport(result);
        assertNotNull(report);
        assertTrue(report.contains("STRESS RAMP-UP (13 TIERS)"));
        assertTrue(report.contains("ASCII VISUALIZATION CHARTS:"));
    }

    @Test
    public void testCompareFastPathMode() {
        BenchmarkRunner.Config config = BenchmarkRunner.parseArgs(new String[]{"--mode=compare-fastpath"});
        assertEquals("compare-fastpath", config.getMode());

        BenchmarkRunner runner = new BenchmarkRunner();
        config.setTarget("http://127.0.0.1:65534/ping");
        config.setWarmupSeconds(0);
        config.setDurationSeconds(1);
        config.setRuns(1);

        BenchmarkRunner.BenchmarkResult result = runner.runBenchmark(config);
        assertNotNull(result);
        assertEquals("compare-fastpath", result.getMode());
        assertNotNull(result.getSteps());
        assertNotNull(result.getComparisonSteps());
        assertEquals(13, result.getSteps().size());
        assertEquals(13, result.getComparisonSteps().size());

        String report = BenchmarkRunner.formatReport(result);
        assertTrue(report.contains("FAST-PATH DUAL COMPARISON"));
        assertTrue(report.contains("ASCII VISUALIZATION CHARTS:"));
    }

    @Test
    public void testCompareCapMode() {
        BenchmarkRunner.Config config = BenchmarkRunner.parseArgs(new String[]{"--mode=compare-cap"});
        assertEquals("compare-cap", config.getMode());

        BenchmarkRunner runner = new BenchmarkRunner();
        config.setTarget("http://127.0.0.1:65534/ping");
        config.setWarmupSeconds(0);
        config.setDurationSeconds(1);
        config.setRuns(1);

        BenchmarkRunner.BenchmarkResult result = runner.runBenchmark(config);
        assertNotNull(result);
        assertEquals("compare-cap", result.getMode());
        assertNotNull(result.getSteps());
        assertNotNull(result.getComparisonSteps());
        assertEquals(4, result.getSteps().size());
        assertEquals(4, result.getComparisonSteps().size());

        String report = BenchmarkRunner.formatReport(result);
        assertTrue(report.contains("CAP EXPERIMENT DUAL COMPARISON"));
        assertTrue(report.contains("ASCII VISUALIZATION CHARTS:"));
    }
}
