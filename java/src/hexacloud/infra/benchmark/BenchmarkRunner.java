package hexacloud.infra.benchmark;

import hexacloud.core.utils.concurrent.ThreadManager;
import hexacloud.infra.benchmark.protocol.HttpBenchmarkClient;
import hexacloud.infra.benchmark.protocol.TcpBenchmarkClient;
import hexacloud.infra.benchmark.protocol.TelnetBenchmarkClient;
import hexacloud.infra.benchmark.protocol.WsBenchmarkClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class BenchmarkRunner {

    public static final int[] STRESS_RAMP_UP_CLIENTS = {100, 500, 1000, 2500, 5000, 10000, 25000, 50000};

    public static class Config {
        private String mode = "quick";
        private String protocol = "http";
        private String target = null;
        private boolean helpRequested = false;
        private int quickDurationSeconds = 5;
        private int stressStepDurationSeconds = 10;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getTarget() {
            if (target != null && !target.isBlank()) {
                return target;
            }
            return getDefaultTargetForProtocol(protocol);
        }

        public String getRawTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public boolean isHelpRequested() {
            return helpRequested;
        }

        public void setHelpRequested(boolean helpRequested) {
            this.helpRequested = helpRequested;
        }

        public int getQuickDurationSeconds() {
            return quickDurationSeconds;
        }

        public void setQuickDurationSeconds(int quickDurationSeconds) {
            this.quickDurationSeconds = quickDurationSeconds;
        }

        public int getStressStepDurationSeconds() {
            return stressStepDurationSeconds;
        }

        public void setStressStepDurationSeconds(int stressStepDurationSeconds) {
            this.stressStepDurationSeconds = stressStepDurationSeconds;
        }

        public static String getDefaultTargetForProtocol(String protocol) {
            if (protocol == null) return "http://127.0.0.1:8080";
            switch (protocol.toLowerCase()) {
                case "tcp":
                case "telnet":
                    return "127.0.0.1:8080";
                case "ws":
                    return "ws://127.0.0.1:8080";
                case "http":
                case "all":
                default:
                    return "http://127.0.0.1:8080";
            }
        }
    }

    public static class StepResult {
        private final int stepIndex;
        private final int concurrency;
        private final long totalRequests;
        private final long errorRequests;
        private final double throughputRps;
        private final long p50LatencyMs;
        private final long p90LatencyMs;
        private final long p99LatencyMs;
        private final double errorPercentage;
        private final boolean stopped;
        private final String status;

        public StepResult(int stepIndex, int concurrency, long totalRequests, long errorRequests,
                          double throughputRps, long p50LatencyMs, long p90LatencyMs, long p99LatencyMs,
                          double errorPercentage, boolean stopped, String status) {
            this.stepIndex = stepIndex;
            this.concurrency = concurrency;
            this.totalRequests = totalRequests;
            this.errorRequests = errorRequests;
            this.throughputRps = throughputRps;
            this.p50LatencyMs = p50LatencyMs;
            this.p90LatencyMs = p90LatencyMs;
            this.p99LatencyMs = p99LatencyMs;
            this.errorPercentage = errorPercentage;
            this.stopped = stopped;
            this.status = status;
        }

        public int getStepIndex() { return stepIndex; }
        public int getConcurrency() { return concurrency; }
        public long getTotalRequests() { return totalRequests; }
        public long getErrorRequests() { return errorRequests; }
        public double getThroughputRps() { return throughputRps; }
        public long getP50LatencyMs() { return p50LatencyMs; }
        public long getP90LatencyMs() { return p90LatencyMs; }
        public long getP99LatencyMs() { return p99LatencyMs; }
        public double getErrorPercentage() { return errorPercentage; }
        public boolean isStopped() { return stopped; }
        public String getStatus() { return status; }
    }

    public static class BenchmarkResult {
        private final String mode;
        private final String protocol;
        private final String target;
        private final List<StepResult> steps;
        private final int maxStableConcurrency;
        private final double maxStableRps;
        private final String bottleneckInfo;

        public BenchmarkResult(String mode, String protocol, String target, List<StepResult> steps,
                               int maxStableConcurrency, double maxStableRps, String bottleneckInfo) {
            this.mode = mode;
            this.protocol = protocol;
            this.target = target;
            this.steps = steps;
            this.maxStableConcurrency = maxStableConcurrency;
            this.maxStableRps = maxStableRps;
            this.bottleneckInfo = bottleneckInfo;
        }

        public String getMode() { return mode; }
        public String getProtocol() { return protocol; }
        public String getTarget() { return target; }
        public List<StepResult> getSteps() { return steps; }
        public int getMaxStableConcurrency() { return maxStableConcurrency; }
        public double getMaxStableRps() { return maxStableRps; }
        public String getBottleneckInfo() { return bottleneckInfo; }
    }

    public static Config parseArgs(String[] args) {
        Config config = new Config();
        if (args == null) return config;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.equalsIgnoreCase("--help") || arg.equalsIgnoreCase("-h")) {
                config.setHelpRequested(true);
            } else if (arg.startsWith("--mode=")) {
                config.setMode(arg.substring("--mode=".length()));
            } else if (arg.equalsIgnoreCase("--mode") && i + 1 < args.length) {
                config.setMode(args[++i]);
            } else if (arg.startsWith("--protocol=")) {
                config.setProtocol(arg.substring("--protocol=".length()));
            } else if (arg.equalsIgnoreCase("--protocol") && i + 1 < args.length) {
                config.setProtocol(args[++i]);
            } else if (arg.startsWith("--target=")) {
                config.setTarget(arg.substring("--target=".length()));
            } else if (arg.equalsIgnoreCase("--target") && i + 1 < args.length) {
                config.setTarget(args[++i]);
            }
        }
        return config;
    }

    public static boolean isStoppingCondition(double errorPercentage, long p99LatencyMs) {
        return errorPercentage > 1.0 || p99LatencyMs > 2000;
    }

    public static String determineStatus(double errorPercentage, long p99LatencyMs) {
        boolean errStop = errorPercentage > 1.0;
        boolean latStop = p99LatencyMs > 2000;
        if (errStop && latStop) {
            return "STOPPED (error > 1.0%, p99 > 2000ms)";
        } else if (errStop) {
            return "STOPPED (error > 1.0%)";
        } else if (latStop) {
            return "STOPPED (p99 > 2000ms)";
        }
        return "OK";
    }

    public BenchmarkResult runBenchmark(Config config) {
        String mode = config.getMode().toLowerCase();
        String protocol = config.getProtocol().toLowerCase();
        String target = config.getTarget();
        List<StepResult> steps = new ArrayList<>();

        int maxStableConcurrency = 0;
        double maxStableRps = 0.0;
        String bottleneckInfo = "None (Completed maximum saturation tier)";

        if ("quick".equals(mode)) {
            StepResult step = runStep(1, protocol, target, 100, config.getQuickDurationSeconds());
            steps.add(step);
            if (step.isStopped()) {
                maxStableConcurrency = 0;
                maxStableRps = 0.0;
                bottleneckInfo = String.format("Step 1 (100 clients - %s)", step.getStatus());
            } else {
                maxStableConcurrency = 100;
                maxStableRps = step.getThroughputRps();
            }
        } else if ("stress".equals(mode)) {
            int stepIndex = 1;
            for (int concurrency : STRESS_RAMP_UP_CLIENTS) {
                StepResult step = runStep(stepIndex, protocol, target, concurrency, config.getStressStepDurationSeconds());
                steps.add(step);

                if (step.isStopped()) {
                    bottleneckInfo = String.format("Step %d (%,d clients - %s)", stepIndex, concurrency, step.getStatus());
                    break;
                } else {
                    maxStableConcurrency = concurrency;
                    maxStableRps = step.getThroughputRps();
                }
                stepIndex++;
            }
        }

        return new BenchmarkResult(mode, protocol, target, steps, maxStableConcurrency, maxStableRps, bottleneckInfo);
    }

    public StepResult runStep(int stepIndex, String protocol, String target, int concurrency, int durationSeconds) {
        MetricsCollector metrics = new MetricsCollector();
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch startLatch = new CountDownLatch(1);

        HttpBenchmarkClient httpClient = "http".equalsIgnoreCase(protocol) ? new HttpBenchmarkClient(metrics) : null;
        TcpBenchmarkClient tcpClient = "tcp".equalsIgnoreCase(protocol) ? new TcpBenchmarkClient(metrics) : null;
        WsBenchmarkClient wsClient = "ws".equalsIgnoreCase(protocol) ? new WsBenchmarkClient(metrics) : null;
        TelnetBenchmarkClient telnetClient = "telnet".equalsIgnoreCase(protocol) ? new TelnetBenchmarkClient(metrics) : null;

        long startTime = System.currentTimeMillis();

        try (ExecutorService executor = ThreadManager.newVirtualThreadPool()) {
            for (int i = 0; i < concurrency; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        String p = protocol.toLowerCase();
                        switch (p) {
                            case "tcp":
                                (tcpClient != null ? tcpClient : new TcpBenchmarkClient(metrics)).runClientLoop(target, running);
                                break;
                            case "telnet":
                                (telnetClient != null ? telnetClient : new TelnetBenchmarkClient(metrics)).runClientLoop(target, running);
                                break;
                            case "ws":
                                (wsClient != null ? wsClient : new WsBenchmarkClient(metrics)).runClientLoop(target, running);
                                break;
                            case "http":
                            default:
                                (httpClient != null ? httpClient : new HttpBenchmarkClient(metrics)).runClientLoop(target, running);
                                break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            startLatch.countDown();

            try {
                Thread.sleep(durationSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            running.set(false);
            executor.shutdownNow();
        }

        long actualDurationMs = Math.max(1, System.currentTimeMillis() - startTime);
        double durationSec = actualDurationMs / 1000.0;

        long totalReqs = metrics.getTotalRequests();
        long errorReqs = metrics.getErrorRequests();
        double errorPct = metrics.getErrorPercentage();
        long p50 = metrics.getPercentileLatency(50.0);
        long p90 = metrics.getPercentileLatency(90.0);
        long p99 = metrics.getPercentileLatency(99.0);
        double rps = totalReqs / durationSec;

        boolean stopped = isStoppingCondition(errorPct, p99);
        String status = determineStatus(errorPct, p99);

        return new StepResult(stepIndex, concurrency, totalReqs, errorReqs, rps, p50, p90, p99, errorPct, stopped, status);
    }

    public static String formatReport(BenchmarkResult result) {
        StringBuilder sb = new StringBuilder();
        String modeHeader = "quick".equalsIgnoreCase(result.getMode()) ? "QUICK SMOKE" : "STRESS RAMP-UP";
        sb.append("================================================================================\n");
        sb.append(String.format("           GATEBRIDGE BENCHMARK REPORT (Mode: %s)\n", modeHeader));
        sb.append("================================================================================\n");
        sb.append(String.format("Protocol: %s | Fast-Path: DISABLED | Target: %s\n\n",
                result.getProtocol().toUpperCase(), result.getTarget()));

        sb.append(String.format("%-4s %12s %19s %14s %14s %13s    %s\n",
                "STEP", "CLIENTS", "THROUGHPUT (RPS)", "LATENCY p50", "LATENCY p99", "ERROR RATE", "STATUS"));
        sb.append("--------------------------------------------------------------------------------\n");

        for (StepResult step : result.getSteps()) {
            String clientsStr = String.format("%,d", step.getConcurrency());
            String rpsStr = String.format("%,.0f req/s", step.getThroughputRps());
            String p50Str = String.format("%d ms", step.getP50LatencyMs());
            String p99Str = String.format("%d ms", step.getP99LatencyMs());
            String errStr = String.format("%.2f%%", step.getErrorPercentage());

            sb.append(String.format("%-4d %12s %19s %14s %14s %13s    %s\n",
                    step.getStepIndex(), clientsStr, rpsStr, p50Str, p99Str, errStr, step.getStatus()));
        }

        sb.append("\n--------------------------------------------------------------------------------\n");
        sb.append("SUMMARY:\n");
        if (result.getMaxStableConcurrency() > 0) {
            sb.append(String.format("- Maximum Stable Capacity: %,d concurrent clients @ %,.0f RPS\n",
                    result.getMaxStableConcurrency(), result.getMaxStableRps()));
        } else {
            sb.append("- Maximum Stable Capacity: 0 concurrent clients @ 0 RPS\n");
        }
        sb.append(String.format("- Bottleneck Reached: %s\n", result.getBottleneckInfo()));
        sb.append("================================================================================\n");
        return sb.toString();
    }

    public static void printHelp() {
        System.out.println("GateBridge BenchmarkRunner CLI");
        System.out.println("Usage: java hexacloud.infra.benchmark.BenchmarkRunner [options]");
        System.out.println("Options:");
        System.out.println("  --mode=quick|stress     Execution mode: quick (5s @ 100 clients) or stress (ramp-up loop). Default: quick");
        System.out.println("  --protocol=http|tcp|ws|telnet|all Target protocol. Default: http");
        System.out.println("  --target=<url|host:port> Target URL or endpoint to benchmark. Default: protocol dependent");
        System.out.println("  --help, -h             Print this help message");
    }

    public static void main(String[] args) {
        Config config = parseArgs(args);
        if (config.isHelpRequested()) {
            printHelp();
            return;
        }

        BenchmarkRunner runner = new BenchmarkRunner();
        if ("all".equalsIgnoreCase(config.getProtocol())) {
            String[] protocols = {"http", "tcp", "ws", "telnet"};
            for (String p : protocols) {
                Config singleConfig = new Config();
                singleConfig.setMode(config.getMode());
                singleConfig.setProtocol(p);
                singleConfig.setTarget(config.getRawTarget());
                singleConfig.setQuickDurationSeconds(config.getQuickDurationSeconds());
                singleConfig.setStressStepDurationSeconds(config.getStressStepDurationSeconds());
                BenchmarkResult res = runner.runBenchmark(singleConfig);
                System.out.println(formatReport(res));
            }
        } else {
            BenchmarkResult res = runner.runBenchmark(config);
            System.out.println(formatReport(res));
        }
    }
}
