package hexacloud.infra.benchmark;

import hexacloud.infra.benchmark.BenchmarkRunner.StepResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BenchmarkChartGeneratorTest {

    private StepResult createMockStepResult(int stepIndex, int concurrency, double goodputRps,
                                           long p50, long p99, double errorPct, double cpu, long ramMb) {
        SystemMetricsCollector.MetricsDelta delta = new SystemMetricsCollector.MetricsDelta(cpu, ramMb, 0, 0, 10);
        return new StepResult(stepIndex, concurrency, (long) (goodputRps * 10), (long) (goodputRps * 10), 0, 0, 0,
                goodputRps, goodputRps, p50, p50 + 5, p50 + 10, p99, p99 + 20, errorPct, false, "STABLE", delta);
    }

    @Test
    public void testEmptyStepsList() {
        assertEquals("### Clients vs Goodput (RPS)\nNo data available.\n", BenchmarkChartGenerator.generateGoodputChart(Collections.emptyList()));
        assertEquals("", BenchmarkChartGenerator.generateAllCharts(null));
    }

    @Test
    public void testGoodputChartGeneration() {
        List<StepResult> steps = List.of(
                createMockStepResult(1, 100, 10000.0, 5, 20, 0.0, 15.0, 128),
                createMockStepResult(2, 500, 20000.0, 10, 40, 0.5, 30.0, 256)
        );

        String chart = BenchmarkChartGenerator.generateGoodputChart(steps);
        assertNotNull(chart);
        assertTrue(chart.contains("Clients vs Goodput (RPS)"));
        assertTrue(chart.contains("100 clients"));
        assertTrue(chart.contains("500 clients"));
        assertTrue(chart.contains("10,000 RPS"));
        assertTrue(chart.contains("20,000 RPS"));
    }

    @Test
    public void testP50AndP99LatencyCharts() {
        List<StepResult> steps = List.of(
                createMockStepResult(1, 100, 10000.0, 5, 25, 0.0, 15.0, 128),
                createMockStepResult(2, 500, 20000.0, 15, 60, 0.0, 30.0, 256)
        );

        String p50Chart = BenchmarkChartGenerator.generateP50LatencyChart(steps);
        assertTrue(p50Chart.contains("Clients vs p50 Latency (ms)"));
        assertTrue(p50Chart.contains("5 ms"));
        assertTrue(p50Chart.contains("15 ms"));

        String p99Chart = BenchmarkChartGenerator.generateP99LatencyChart(steps);
        assertTrue(p99Chart.contains("Clients vs p99 Latency (ms)"));
        assertTrue(p99Chart.contains("25 ms"));
        assertTrue(p99Chart.contains("60 ms"));
    }

    @Test
    public void testErrorRateChartGeneration() {
        List<StepResult> steps = List.of(
                createMockStepResult(1, 100, 10000.0, 5, 20, 0.12, 15.0, 128),
                createMockStepResult(2, 500, 20000.0, 10, 40, 1.45, 30.0, 256)
        );

        String chart = BenchmarkChartGenerator.generateErrorRateChart(steps);
        assertTrue(chart.contains("Clients vs Error Rate (%)"));
        assertTrue(chart.contains("0.12%"));
        assertTrue(chart.contains("1.45%"));
    }

    @Test
    public void testCpuAndRamCharts() {
        List<StepResult> steps = List.of(
                createMockStepResult(1, 100, 10000.0, 5, 20, 0.0, 25.5, 128),
                createMockStepResult(2, 500, 20000.0, 10, 40, 0.0, 65.0, 512)
        );

        String cpuChart = BenchmarkChartGenerator.generateCpuChart(steps);
        assertTrue(cpuChart.contains("Clients vs CPU (%)"));
        assertTrue(cpuChart.contains("25.5%"));
        assertTrue(cpuChart.contains("65.0%"));

        String ramChart = BenchmarkChartGenerator.generateRamChart(steps);
        assertTrue(ramChart.contains("Clients vs RAM (MB)"));
        assertTrue(ramChart.contains("128 MB"));
        assertTrue(ramChart.contains("512 MB"));
    }

    @Test
    public void testGenerateAllCharts() {
        List<StepResult> steps = List.of(
                createMockStepResult(1, 100, 10000.0, 5, 20, 0.0, 25.5, 128)
        );

        String allCharts = BenchmarkChartGenerator.generateAllCharts(steps);
        assertTrue(allCharts.contains("Clients vs Goodput (RPS)"));
        assertTrue(allCharts.contains("Clients vs p50 Latency (ms)"));
        assertTrue(allCharts.contains("Clients vs p99 Latency (ms)"));
        assertTrue(allCharts.contains("Clients vs Error Rate (%)"));
        assertTrue(allCharts.contains("Clients vs CPU (%)"));
        assertTrue(allCharts.contains("Clients vs RAM (MB)"));
    }
}
