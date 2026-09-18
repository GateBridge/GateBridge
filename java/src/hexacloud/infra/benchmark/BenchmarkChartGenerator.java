package hexacloud.infra.benchmark;

import hexacloud.infra.benchmark.BenchmarkRunner.StepResult;

import java.util.List;

public class BenchmarkChartGenerator {

    @FunctionalInterface
    private interface ValueExtractor {
        double getValue(StepResult step);
    }

    @FunctionalInterface
    private interface ValueFormatter {
        String format(double val, StepResult step);
    }

    public static String generateGoodputChart(List<StepResult> steps) {
        return generateChart("Clients vs Goodput (RPS)", steps,
                StepResult::getGoodputRps,
                (val, step) -> String.format("%,.0f RPS", val), 0.0);
    }

    public static String generateP50LatencyChart(List<StepResult> steps) {
        return generateChart("Clients vs p50 Latency (ms)", steps,
                step -> (double) step.getP50LatencyMs(),
                (val, step) -> String.format("%d ms", (long) val), 0.0);
    }

    public static String generateP99LatencyChart(List<StepResult> steps) {
        return generateChart("Clients vs p99 Latency (ms)", steps,
                step -> (double) step.getP99LatencyMs(),
                (val, step) -> String.format("%d ms", (long) val), 0.0);
    }

    public static String generateErrorRateChart(List<StepResult> steps) {
        return generateChart("Clients vs Error Rate (%)", steps,
                StepResult::getErrorPercentage,
                (val, step) -> String.format("%.2f%%", val), 0.0);
    }

    public static String generateCpuChart(List<StepResult> steps) {
        return generateChart("Clients vs CPU (%)", steps,
                StepResult::getCpuPercent,
                (val, step) -> String.format("%.1f%%", val), 0.0);
    }

    public static String generateRamChart(List<StepResult> steps) {
        return generateChart("Clients vs RAM (MB)", steps,
                step -> (double) step.getHeapUsedMb(),
                (val, step) -> String.format("%d MB", (long) val), 0.0);
    }

    public static String generateAllCharts(List<StepResult> steps) {
        if (steps == null || steps.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(generateGoodputChart(steps)).append("\n");
        sb.append(generateP50LatencyChart(steps)).append("\n");
        sb.append(generateP99LatencyChart(steps)).append("\n");
        sb.append(generateErrorRateChart(steps)).append("\n");
        sb.append(generateCpuChart(steps)).append("\n");
        sb.append(generateRamChart(steps)).append("\n");
        return sb.toString();
    }

    private static String generateChart(String title, List<StepResult> steps,
                                       ValueExtractor extractor, ValueFormatter formatter,
                                       double scaleMaxOverride) {
        if (steps == null || steps.isEmpty()) {
            return "### " + title + "\nNo data available.\n";
        }

        double maxVal = scaleMaxOverride > 0 ? scaleMaxOverride : 0.0;
        if (scaleMaxOverride <= 0) {
            for (StepResult step : steps) {
                double v = extractor.getValue(step);
                if (v > maxVal) {
                    maxVal = v;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(title).append("\n");

        int barWidth = 40;
        for (StepResult step : steps) {
            double val = extractor.getValue(step);
            int fill = maxVal > 0 ? (int) Math.round((val / maxVal) * barWidth) : 0;
            if (fill > barWidth) fill = barWidth;
            if (fill < 0) fill = 0;

            String bar = "#".repeat(fill) + " ".repeat(barWidth - fill);
            String clientsStr = String.format("%,d clients", step.getConcurrency());
            String valStr = formatter.format(val, step);

            sb.append(String.format("  %12s | [%s] %s\n", clientsStr, bar, valStr));
        }

        return sb.toString();
    }
}
