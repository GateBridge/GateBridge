package hexacloud.infra.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BenchmarkRunnerTest {

    @Test
    public void testParseArgsDefaults() {
        BenchmarkRunner.Config config = BenchmarkRunner.parseArgs(new String[]{});
        assertEquals("quick", config.getMode());
        assertEquals("http", config.getProtocol());
        assertEquals("http://127.0.0.1:8080", config.getTarget());
        assertFalse(config.isHelpRequested());
    }

    @Test
    public void testParseArgsExplicit() {
        String[] args = new String[]{"--mode=stress", "--protocol=tcp", "--target=127.0.0.1:9090"};
        BenchmarkRunner.Config config = BenchmarkRunner.parseArgs(args);
        assertEquals("stress", config.getMode());
        assertEquals("tcp", config.getProtocol());
        assertEquals("127.0.0.1:9090", config.getTarget());
        assertFalse(config.isHelpRequested());
    }

    @Test
    public void testParseArgsHelp() {
        BenchmarkRunner.Config config1 = BenchmarkRunner.parseArgs(new String[]{"--help"});
        assertTrue(config1.isHelpRequested());

        BenchmarkRunner.Config config2 = BenchmarkRunner.parseArgs(new String[]{"-h"});
        assertTrue(config2.isHelpRequested());
    }

    @Test
    public void testProtocolAllDefaultTargetResolution() {
        BenchmarkRunner.Config config = BenchmarkRunner.parseArgs(new String[]{"--protocol=all"});
        assertNull(config.getRawTarget());

        BenchmarkRunner.Config httpCfg = new BenchmarkRunner.Config();
        httpCfg.setProtocol("http");
        httpCfg.setTarget(config.getRawTarget());
        assertEquals("http://127.0.0.1:8080", httpCfg.getTarget());

        BenchmarkRunner.Config tcpCfg = new BenchmarkRunner.Config();
        tcpCfg.setProtocol("tcp");
        tcpCfg.setTarget(config.getRawTarget());
        assertEquals("127.0.0.1:8080", tcpCfg.getTarget());

        BenchmarkRunner.Config wsCfg = new BenchmarkRunner.Config();
        wsCfg.setProtocol("ws");
        wsCfg.setTarget(config.getRawTarget());
        assertEquals("ws://127.0.0.1:8080", wsCfg.getTarget());

        BenchmarkRunner.Config telnetCfg = new BenchmarkRunner.Config();
        telnetCfg.setProtocol("telnet");
        telnetCfg.setTarget(config.getRawTarget());
        assertEquals("127.0.0.1:8080", telnetCfg.getTarget());
    }

    @Test
    public void testStoppingConditionEvaluation() {
        assertFalse(BenchmarkRunner.isStoppingCondition(0.5, 100));
        assertTrue(BenchmarkRunner.isStoppingCondition(1.2, 100));
        assertTrue(BenchmarkRunner.isStoppingCondition(0.5, 2050));
        assertTrue(BenchmarkRunner.isStoppingCondition(1.5, 2100));

        assertEquals("OK", BenchmarkRunner.determineStatus(0.5, 100));
        assertTrue(BenchmarkRunner.determineStatus(1.5, 100).contains("error > 1.0%"));
        assertTrue(BenchmarkRunner.determineStatus(0.5, 2050).contains("p99 > 2000ms"));
    }

    @Test
    public void testExecutionInitializationQuickMode() {
        BenchmarkRunner runner = new BenchmarkRunner();
        BenchmarkRunner.Config config = new BenchmarkRunner.Config();
        config.setMode("quick");
        config.setProtocol("http");
        config.setTarget("http://127.0.0.1:65534/ping");
        config.setQuickDurationSeconds(1);

        BenchmarkRunner.BenchmarkResult result = runner.runBenchmark(config);

        assertNotNull(result);
        assertEquals("quick", result.getMode());
        assertEquals("http", result.getProtocol());
        assertEquals("http://127.0.0.1:65534/ping", result.getTarget());
        assertEquals(1, result.getSteps().size());

        BenchmarkRunner.StepResult step = result.getSteps().get(0);
        assertEquals(1, step.getStepIndex());
        assertEquals(100, step.getConcurrency());
        assertTrue(step.isStopped());
        assertTrue(step.getErrorPercentage() > 0.0);

        String report = BenchmarkRunner.formatReport(result);
        assertNotNull(report);
        assertTrue(report.contains("GATEBRIDGE BENCHMARK REPORT"));
        assertTrue(report.contains("QUICK SMOKE"));
    }

    @Test
    public void testStressModeInitializationAndStopping() {
        BenchmarkRunner runner = new BenchmarkRunner();
        BenchmarkRunner.Config config = new BenchmarkRunner.Config();
        config.setMode("stress");
        config.setProtocol("http");
        config.setTarget("http://127.0.0.1:65534/ping");
        config.setStressStepDurationSeconds(1);

        BenchmarkRunner.BenchmarkResult result = runner.runBenchmark(config);

        assertNotNull(result);
        assertEquals("stress", result.getMode());
        // Since requests to 65534 fail with 100% error rate, stress mode should stop after step 1
        assertEquals(1, result.getSteps().size());
        assertEquals(0, result.getMaxStableConcurrency());
        assertTrue(result.getBottleneckInfo().contains("Step 1"));

        String report = BenchmarkRunner.formatReport(result);
        assertNotNull(report);
        assertTrue(report.contains("STRESS RAMP-UP"));
    }
}
