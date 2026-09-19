package hexacloud.infra.benchmark;

import hexacloud.core.utils.concurrent.ThreadManager;
import hexacloud.infra.benchmark.protocol.HttpBenchmarkClient;
import hexacloud.infra.benchmark.protocol.TcpBenchmarkClient;
import hexacloud.infra.benchmark.protocol.TelnetBenchmarkClient;
import hexacloud.infra.benchmark.protocol.WsBenchmarkClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class BenchmarkRunner {

    public static final int[] STRESS_RAMP_UP_CLIENTS = {
            100, 500, 2500, 10000
    };

    public static class Config {
        private String mode = "quick";
        private String protocol = "http";
        private String target = null;
        private boolean helpRequested = false;
        private int warmupSeconds = 3;
        private int durationSeconds = 10;
        private int runs = 3;
        private int cap = 1500;

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

        public int getWarmupSeconds() {
            return warmupSeconds;
        }

        public void setWarmupSeconds(int warmupSeconds) {
            this.warmupSeconds = warmupSeconds;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        public int getRuns() {
            return runs;
        }

        public void setRuns(int runs) {
            this.runs = runs;
        }

        public int getCap() {
            return cap;
        }

        public void setCap(int cap) {
            this.cap = cap;
        }

        public int getQuickDurationSeconds() {
            return durationSeconds;
        }

        public void setQuickDurationSeconds(int quickDurationSeconds) {
            this.durationSeconds = quickDurationSeconds;
        }

        public int getStressStepDurationSeconds() {
            return durationSeconds;
        }

        public void setStressStepDurationSeconds(int stressStepDurationSeconds) {
            this.durationSeconds = stressStepDurationSeconds;
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
        private final long goodputRequests;
        private final long cap503Requests;
        private final long otherErrorRequests;
        private final long errorRequests;
        private final double throughputRps;
        private final double goodputRps;
        private final long p50LatencyMs;
        private final long p90LatencyMs;
        private final long p95LatencyMs;
        private final long p99LatencyMs;
        private final long p999LatencyMs;
        private final double errorPercentage;
        private final boolean stopped;
        private final String status;
        private final SystemMetricsCollector.MetricsDelta systemMetrics;
        private final double cpuPercent;
        private final long heapUsedMb;

        public StepResult(int stepIndex, int concurrency, long totalRequests, long goodputRequests,
                          long cap503Requests, long otherErrorRequests, long errorRequests,
                          double throughputRps, double goodputRps, long p50LatencyMs, long p90LatencyMs,
                          long p95LatencyMs, long p99LatencyMs, long p999LatencyMs, double errorPercentage,
                          boolean stopped, String status, SystemMetricsCollector.MetricsDelta systemMetrics) {
            this.stepIndex = stepIndex;
            this.concurrency = concurrency;
            this.totalRequests = totalRequests;
            this.goodputRequests = goodputRequests;
            this.cap503Requests = cap503Requests;
            this.otherErrorRequests = otherErrorRequests;
            this.errorRequests = errorRequests;
            this.throughputRps = throughputRps;
            this.goodputRps = goodputRps;
            this.p50LatencyMs = p50LatencyMs;
            this.p90LatencyMs = p90LatencyMs;
            this.p95LatencyMs = p95LatencyMs;
            this.p99LatencyMs = p99LatencyMs;
            this.p999LatencyMs = p999LatencyMs;
            this.errorPercentage = errorPercentage;
            this.stopped = stopped;
            this.status = status;
            this.systemMetrics = systemMetrics;
            this.cpuPercent = systemMetrics != null ? systemMetrics.getCpuPercent() : 0.0;
            this.heapUsedMb = systemMetrics != null ? systemMetrics.getHeapUsedMb() : 0;
        }

        public StepResult(int stepIndex, int concurrency, long totalRequests, long errorRequests,
                          double throughputRps, long p50LatencyMs, long p90LatencyMs, long p99LatencyMs,
                          double errorPercentage, boolean stopped, String status) {
            this(stepIndex, concurrency, totalRequests, totalRequests - errorRequests, 0, errorRequests, errorRequests,
                 throughputRps, throughputRps, p50LatencyMs, p90LatencyMs, p50LatencyMs, p99LatencyMs, p99LatencyMs,
                 errorPercentage, stopped, status, new SystemMetricsCollector.MetricsDelta(0, 0, 0, 0, 0));
        }

        public int getStepIndex() { return stepIndex; }
        public int getConcurrency() { return concurrency; }
        public long getTotalRequests() { return totalRequests; }
        public long getGoodputRequests() { return goodputRequests; }
        public long getCap503Requests() { return cap503Requests; }
        public long getOtherErrorRequests() { return otherErrorRequests; }
        public long getErrorRequests() { return errorRequests; }
        public double getThroughputRps() { return throughputRps; }
        public double getGoodputRps() { return goodputRps; }
        public long getP50LatencyMs() { return p50LatencyMs; }
        public long getP90LatencyMs() { return p90LatencyMs; }
        public long getP95LatencyMs() { return p95LatencyMs; }
        public long getP99LatencyMs() { return p99LatencyMs; }
        public long getP999LatencyMs() { return p999LatencyMs; }
        public double getErrorPercentage() { return errorPercentage; }
        public boolean isStopped() { return stopped; }
        public String getStatus() { return status; }
        public SystemMetricsCollector.MetricsDelta getSystemMetrics() { return systemMetrics; }
        public double getCpuPercent() { return cpuPercent; }
        public long getHeapUsedMb() { return heapUsedMb; }
    }

    public static class BenchmarkResult {
        private final String mode;
        private final String protocol;
        private final String target;
        private final List<StepResult> steps;
        private final List<StepResult> comparisonSteps;
        private final int maxStableConcurrency;
        private final double maxStableRps;
        private final String bottleneckInfo;
        private final SystemMetricsCollector.EnvironmentInfo environmentInfo;
        private final String saturationAnalysis;

        public BenchmarkResult(String mode, String protocol, String target, List<StepResult> steps,
                               int maxStableConcurrency, double maxStableRps, String bottleneckInfo) {
            this(mode, protocol, target, steps, null, maxStableConcurrency, maxStableRps, bottleneckInfo, null, null);
        }

        public BenchmarkResult(String mode, String protocol, String target, List<StepResult> steps,
                               int maxStableConcurrency, double maxStableRps, String bottleneckInfo,
                               SystemMetricsCollector.EnvironmentInfo environmentInfo, String saturationAnalysis) {
            this(mode, protocol, target, steps, null, maxStableConcurrency, maxStableRps, bottleneckInfo, environmentInfo, saturationAnalysis);
        }

        public BenchmarkResult(String mode, String protocol, String target, List<StepResult> steps,
                               List<StepResult> comparisonSteps,
                               int maxStableConcurrency, double maxStableRps, String bottleneckInfo,
                               SystemMetricsCollector.EnvironmentInfo environmentInfo, String saturationAnalysis) {
            this.mode = mode;
            this.protocol = protocol;
            this.target = target;
            this.steps = steps;
            this.comparisonSteps = comparisonSteps;
            this.maxStableConcurrency = maxStableConcurrency;
            this.maxStableRps = maxStableRps;
            this.bottleneckInfo = bottleneckInfo;
            this.environmentInfo = environmentInfo;
            this.saturationAnalysis = saturationAnalysis;
        }

        public String getMode() { return mode; }
        public String getProtocol() { return protocol; }
        public String getTarget() { return target; }
        public List<StepResult> getSteps() { return steps; }
        public List<StepResult> getComparisonSteps() { return comparisonSteps; }
        public int getMaxStableConcurrency() { return maxStableConcurrency; }
        public double getMaxStableRps() { return maxStableRps; }
        public String getBottleneckInfo() { return bottleneckInfo; }
        public SystemMetricsCollector.EnvironmentInfo getEnvironmentInfo() { return environmentInfo; }
        public String getSaturationAnalysis() { return saturationAnalysis; }
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
            } else if (arg.startsWith("--warmup=")) {
                config.setWarmupSeconds(parseDurationSeconds(arg.substring("--warmup=".length())));
            } else if (arg.equalsIgnoreCase("--warmup") && i + 1 < args.length) {
                config.setWarmupSeconds(parseDurationSeconds(args[++i]));
            } else if (arg.startsWith("--duration=")) {
                config.setDurationSeconds(parseDurationSeconds(arg.substring("--duration=".length())));
            } else if (arg.equalsIgnoreCase("--duration") && i + 1 < args.length) {
                config.setDurationSeconds(parseDurationSeconds(args[++i]));
            } else if (arg.startsWith("--runs=")) {
                config.setRuns(Integer.parseInt(arg.substring("--runs=".length()).trim()));
            } else if (arg.equalsIgnoreCase("--runs") && i + 1 < args.length) {
                config.setRuns(Integer.parseInt(args[++i].trim()));
            } else if (arg.startsWith("--cap=")) {
                config.setCap(parseCapValue(arg.substring("--cap=".length())));
            } else if (arg.equalsIgnoreCase("--cap") && i + 1 < args.length) {
                config.setCap(parseCapValue(args[++i]));
            }
        }

        if (!config.isHelpRequested()) {
            String mode = config.getMode();
            if (mode == null || (!mode.equalsIgnoreCase("quick")
                    && !mode.equalsIgnoreCase("stress")
                    && !mode.equalsIgnoreCase("compare-fastpath")
                    && !mode.equalsIgnoreCase("compare-cap"))) {
                throw new IllegalArgumentException("Invalid mode: '" + mode + "'. Valid modes are: quick, stress, compare-fastpath, compare-cap");
            }

            String protocol = config.getProtocol();
            if (protocol == null || (!protocol.equalsIgnoreCase("http")
                    && !protocol.equalsIgnoreCase("tcp")
                    && !protocol.equalsIgnoreCase("ws")
                    && !protocol.equalsIgnoreCase("telnet")
                    && !protocol.equalsIgnoreCase("all"))) {
                throw new IllegalArgumentException("Invalid protocol: '" + protocol + "'. Valid protocols are: http, tcp, ws, telnet, all");
            }
        }

        return config;
    }

    private static int parseDurationSeconds(String val) {
        if (val == null) return 0;
        String clean = val.trim().toLowerCase();
        if (clean.endsWith("s")) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        return Integer.parseInt(clean);
    }

    private static int parseCapValue(String val) {
        if (val == null) return 1500;
        String clean = val.trim().toLowerCase();
        if ("unlimited".equals(clean)) {
            return 0;
        }
        return Integer.parseInt(clean);
    }

    public static final double DEFAULT_MAX_ERROR_RATE_PERCENT = 1.0;
    public static final long DEFAULT_MAX_P99_LATENCY_MS = 2000L;

    public static boolean isStoppingCondition(double errorPercentage, long p99LatencyMs) {
        return errorPercentage > DEFAULT_MAX_ERROR_RATE_PERCENT || p99LatencyMs > DEFAULT_MAX_P99_LATENCY_MS;
    }

    public static String determinePointHealthStatus(double errorPercentage, long p99LatencyMs) {
        if (errorPercentage > DEFAULT_MAX_ERROR_RATE_PERCENT) {
            return "FAILED";
        } else if (p99LatencyMs > DEFAULT_MAX_P99_LATENCY_MS) {
            return "DEGRADED";
        } else {
            return "STABLE";
        }
    }

    public static String determineStatus(double errorPercentage, long p99LatencyMs) {
        return determinePointHealthStatus(errorPercentage, p99LatencyMs);
    }

    public static boolean checkPreflightConnectivity(String protocol, String target) {
        try {
            if ("http".equalsIgnoreCase(protocol) || "ws".equalsIgnoreCase(protocol)) {
                String httpUrl = target.replaceFirst("(?i)^ws://", "http://").replaceFirst("(?i)^wss://", "https://");
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(1)).build();
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create(httpUrl)).timeout(java.time.Duration.ofSeconds(1)).GET().build();
                client.send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
                return true;
            } else {
                String host = target;
                int port = 8080;
                if (target.contains(":")) {
                    String[] parts = target.split(":");
                    host = parts[0];
                    port = Integer.parseInt(parts[1]);
                }
                try (java.net.Socket s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress(host, port), 1000);
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    public BenchmarkResult runBenchmark(Config config) {
        String mode = config.getMode().toLowerCase();
        String protocol = config.getProtocol().toLowerCase();
        String target = config.getTarget();

        System.setProperty("gatebridge.active.requests.cap", String.valueOf(config.getCap()));

        if (!checkPreflightConnectivity(protocol, target)) {
            System.err.println("[WARNING] Target endpoint '" + target + "' is unreachable!");
            System.err.println("[WARNING] Make sure the GateBridge server is running before executing benchmarks.");
            System.err.println("[WARNING] Quick start command: mvn compile exec:java -Dexec.mainClass=\"hexacloud.application.MinimalApplication\"\n");
        }

        SystemMetricsCollector sysCollector = new SystemMetricsCollector();
        SystemMetricsCollector.EnvironmentInfo envInfo = sysCollector.getEnvironmentInfo();

        if ("compare-fastpath".equalsIgnoreCase(mode)) {
            // Run Fast-Path OFF
            System.setProperty("gatebridge.fastpath.enabled", "false");
            List<StepResult> stepsOff = new ArrayList<>();
            int idxOff = 1;
            for (int concurrency : STRESS_RAMP_UP_CLIENTS) {
                stepsOff.add(runStep(idxOff++, protocol, target, concurrency, config));
            }

            // Run Fast-Path ON
            System.setProperty("gatebridge.fastpath.enabled", "true");
            List<StepResult> stepsOn = new ArrayList<>();
            int maxStableConcurrency = 0;
            double maxStableRps = 0.0;
            String bottleneckInfo = "None (Completed maximum saturation tier)";
            int idxOn = 1;
            for (int concurrency : STRESS_RAMP_UP_CLIENTS) {
                StepResult step = runStep(idxOn, protocol, target, concurrency, config);
                stepsOn.add(step);
                if ("STABLE".equals(step.getStatus())) {
                    if (concurrency > maxStableConcurrency) {
                        maxStableConcurrency = concurrency;
                        maxStableRps = step.getGoodputRps();
                    }
                } else if ("None (Completed maximum saturation tier)".equals(bottleneckInfo)) {
                    bottleneckInfo = String.format("Step %d (%,d clients - %s)", idxOn, concurrency, step.getStatus());
                }
                idxOn++;
            }

            String saturationAnalysis = analyzeSaturationCurve(stepsOn);
            return new BenchmarkResult(mode, protocol, target, stepsOn, stepsOff, maxStableConcurrency, maxStableRps, bottleneckInfo, envInfo, saturationAnalysis);
        } else if ("compare-cap".equalsIgnoreCase(mode)) {
            int[] capTiers = {1000, 2500, 5000, 10000};

            // Run CAP=1500
            System.setProperty("gatebridge.active.requests.cap", "1500");
            List<StepResult> stepsCap1500 = new ArrayList<>();
            int maxStableConcurrency = 0;
            double maxStableRps = 0.0;
            String bottleneckInfo = "None (Completed maximum saturation tier)";
            int idx1500 = 1;
            for (int concurrency : capTiers) {
                StepResult step = runStep(idx1500, protocol, target, concurrency, config);
                stepsCap1500.add(step);
                if ("STABLE".equals(step.getStatus())) {
                    if (concurrency > maxStableConcurrency) {
                        maxStableConcurrency = concurrency;
                        maxStableRps = step.getGoodputRps();
                    }
                } else if ("None (Completed maximum saturation tier)".equals(bottleneckInfo)) {
                    bottleneckInfo = String.format("Step %d (%,d clients - %s)", idx1500, concurrency, step.getStatus());
                }
                idx1500++;
            }

            // Run CAP=0 (UNLIMITED)
            System.setProperty("gatebridge.active.requests.cap", "0");
            List<StepResult> stepsCapUnlimited = new ArrayList<>();
            int idxUnlim = 1;
            for (int concurrency : capTiers) {
                stepsCapUnlimited.add(runStep(idxUnlim++, protocol, target, concurrency, config));
            }

            // Restore original cap config
            System.setProperty("gatebridge.active.requests.cap", String.valueOf(config.getCap()));

            String saturationAnalysis = analyzeSaturationCurve(stepsCap1500);
            return new BenchmarkResult(mode, protocol, target, stepsCap1500, stepsCapUnlimited, maxStableConcurrency, maxStableRps, bottleneckInfo, envInfo, saturationAnalysis);
        }

        List<StepResult> steps = new ArrayList<>();
        int maxStableConcurrency = 0;
        double maxStableRps = 0.0;
        String bottleneckInfo = "None (Completed maximum saturation tier)";

        if ("quick".equals(mode)) {
            StepResult step = runStep(1, protocol, target, 100, config);
            steps.add(step);
            if ("STABLE".equals(step.getStatus())) {
                maxStableConcurrency = 100;
                maxStableRps = step.getGoodputRps();
            } else {
                maxStableConcurrency = 0;
                maxStableRps = 0.0;
                bottleneckInfo = String.format("Step 1 (100 clients - %s)", step.getStatus());
            }
        } else if ("stress".equals(mode)) {
            int stepIndex = 1;
            for (int concurrency : STRESS_RAMP_UP_CLIENTS) {
                StepResult step = runStep(stepIndex, protocol, target, concurrency, config);
                steps.add(step);

                if ("STABLE".equals(step.getStatus())) {
                    if (concurrency > maxStableConcurrency) {
                        maxStableConcurrency = concurrency;
                        maxStableRps = step.getGoodputRps();
                    }
                } else if ("None (Completed maximum saturation tier)".equals(bottleneckInfo)) {
                    bottleneckInfo = String.format("Step %d (%,d clients - %s)", stepIndex, concurrency, step.getStatus());
                }
                stepIndex++;
            }
        }

        String saturationAnalysis = analyzeSaturationCurve(steps);
        return new BenchmarkResult(mode, protocol, target, steps, maxStableConcurrency, maxStableRps, bottleneckInfo, envInfo, saturationAnalysis);
    }

    public StepResult runStep(int stepIndex, String protocol, String target, int concurrency, int durationSeconds) {
        Config config = new Config();
        config.setWarmupSeconds(0);
        config.setDurationSeconds(durationSeconds);
        config.setRuns(1);
        return runStep(stepIndex, protocol, target, concurrency, config);
    }

    public StepResult runStep(int stepIndex, String protocol, String target, int concurrency, Config config) {
        int warmupSec = config.getWarmupSeconds();
        int durationSec = config.getDurationSeconds();
        int runs = Math.max(1, config.getRuns());

        MetricsCollector metrics = new MetricsCollector();
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch startLatch = new CountDownLatch(1);
        SystemMetricsCollector systemMetricsCollector = new SystemMetricsCollector();

        HttpBenchmarkClient httpClient = "http".equalsIgnoreCase(protocol) ? new HttpBenchmarkClient(metrics) : null;
        TcpBenchmarkClient tcpClient = "tcp".equalsIgnoreCase(protocol) ? new TcpBenchmarkClient(metrics) : null;
        WsBenchmarkClient wsClient = "ws".equalsIgnoreCase(protocol) ? new WsBenchmarkClient(metrics) : null;
        TelnetBenchmarkClient telnetClient = "telnet".equalsIgnoreCase(protocol) ? new TelnetBenchmarkClient(metrics) : null;

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

            // Warm-up phase
            if (warmupSec > 0) {
                try {
                    Thread.sleep(warmupSec * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                metrics.reset();
            }

            List<RunMetrics> runResults = new ArrayList<>();
            for (int r = 0; r < runs; r++) {
                metrics.reset();
                SystemMetricsCollector.SystemSnapshot snapStart = systemMetricsCollector.captureSnapshot();
                long runStartTime = System.currentTimeMillis();

                try {
                    Thread.sleep(durationSec * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                long runEndTime = System.currentTimeMillis();
                SystemMetricsCollector.SystemSnapshot snapEnd = systemMetricsCollector.captureSnapshot();
                SystemMetricsCollector.MetricsDelta delta = systemMetricsCollector.computeDelta(snapStart, snapEnd);

                double actualSec = Math.max(0.001, (runEndTime - runStartTime) / 1000.0);
                long totalReqs = metrics.getTotalRequests();
                long goodputReqs = metrics.getGoodputRequests();
                long cap503Reqs = metrics.getCap503Requests();
                long otherErrReqs = metrics.getOtherErrorRequests();
                long errReqs = metrics.getErrorRequests();

                double throughputRps = totalReqs / actualSec;
                double goodputRps = goodputReqs / actualSec;
                long p50 = metrics.getPercentileLatency(50.0);
                long p90 = metrics.getPercentileLatency(90.0);
                long p95 = metrics.getPercentileLatency(95.0);
                long p99 = metrics.getPercentileLatency(99.0);
                long p999 = metrics.getPercentileLatency(99.9);
                double errorPct = metrics.getErrorPercentage();

                runResults.add(new RunMetrics(totalReqs, goodputReqs, cap503Reqs, otherErrReqs, errReqs,
                        throughputRps, goodputRps, p50, p90, p95, p99, p999, errorPct, delta));
            }

            running.set(false);
            executor.shutdownNow();

            return aggregateMedianStepResult(stepIndex, concurrency, runResults);
        }
    }

    public static class RunMetrics {
        public final long totalReqs;
        public final long goodputReqs;
        public final long cap503Reqs;
        public final long otherErrReqs;
        public final long errReqs;
        public final double throughputRps;
        public final double goodputRps;
        public final long p50;
        public final long p90;
        public final long p95;
        public final long p99;
        public final long p999;
        public final double errorPct;
        public final SystemMetricsCollector.MetricsDelta systemMetrics;

        public RunMetrics(long totalReqs, long goodputReqs, long cap503Reqs, long otherErrReqs, long errReqs,
                          double throughputRps, double goodputRps, long p50, long p90, long p95, long p99, long p999,
                          double errorPct, SystemMetricsCollector.MetricsDelta systemMetrics) {
            this.totalReqs = totalReqs;
            this.goodputReqs = goodputReqs;
            this.cap503Reqs = cap503Reqs;
            this.otherErrReqs = otherErrReqs;
            this.errReqs = errReqs;
            this.throughputRps = throughputRps;
            this.goodputRps = goodputRps;
            this.p50 = p50;
            this.p90 = p90;
            this.p95 = p95;
            this.p99 = p99;
            this.p999 = p999;
            this.errorPct = errorPct;
            this.systemMetrics = systemMetrics;
        }
    }

    public static StepResult aggregateMedianStepResult(int stepIndex, int concurrency, List<RunMetrics> runs) {
        if (runs == null || runs.isEmpty()) {
            return new StepResult(stepIndex, concurrency, 0, 0, 0, 0, 0, 0.0, 0.0, 0, 0, 0, 0, 0, 0.0, true, "FAILED", new SystemMetricsCollector.MetricsDelta(0, 0, 0, 0, 0));
        }

        List<Long> totals = runs.stream().map(r -> r.totalReqs).toList();
        List<Long> goodputs = runs.stream().map(r -> r.goodputReqs).toList();
        List<Long> cap503s = runs.stream().map(r -> r.cap503Reqs).toList();
        List<Long> otherErrs = runs.stream().map(r -> r.otherErrReqs).toList();
        List<Long> errs = runs.stream().map(r -> r.errReqs).toList();
        List<Double> throughputRpsList = runs.stream().map(r -> r.throughputRps).toList();
        List<Double> goodputRpsList = runs.stream().map(r -> r.goodputRps).toList();
        List<Long> p50s = runs.stream().map(r -> r.p50).toList();
        List<Long> p90s = runs.stream().map(r -> r.p90).toList();
        List<Long> p95s = runs.stream().map(r -> r.p95).toList();
        List<Long> p99s = runs.stream().map(r -> r.p99).toList();
        List<Long> p999s = runs.stream().map(r -> r.p999).toList();
        List<Double> errorPcts = runs.stream().map(r -> r.errorPct).toList();
        List<Double> cpus = runs.stream().map(r -> r.systemMetrics.getCpuPercent()).toList();
        List<Long> heaps = runs.stream().map(r -> r.systemMetrics.getHeapUsedMb()).toList();
        List<Long> gcCounts = runs.stream().map(r -> r.systemMetrics.getGcCountDelta()).toList();
        List<Long> gcTimes = runs.stream().map(r -> r.systemMetrics.getGcTimeMsDelta()).toList();
        List<Integer> activeThreads = runs.stream().map(r -> r.systemMetrics.getActiveThreads()).toList();

        long medTotal = calculateMedianLong(totals);
        long medGoodput = calculateMedianLong(goodputs);
        long medCap503 = calculateMedianLong(cap503s);
        long medOtherErr = calculateMedianLong(otherErrs);
        long medErr = calculateMedianLong(errs);
        double medThroughputRps = calculateMedianDouble(throughputRpsList);
        double medGoodputRps = calculateMedianDouble(goodputRpsList);
        long medP50 = calculateMedianLong(p50s);
        long medP90 = calculateMedianLong(p90s);
        long medP95 = calculateMedianLong(p95s);
        long medP99 = calculateMedianLong(p99s);
        long medP999 = calculateMedianLong(p999s);
        double medErrorPct = calculateMedianDouble(errorPcts);
        double medCpu = calculateMedianDouble(cpus);
        long medHeap = calculateMedianLong(heaps);
        long medGcCount = calculateMedianLong(gcCounts);
        long medGcTime = calculateMedianLong(gcTimes);
        int medThreads = (int) calculateMedianLong(activeThreads.stream().map(Long::valueOf).toList());

        SystemMetricsCollector.MetricsDelta medSystemMetrics = new SystemMetricsCollector.MetricsDelta(
                medCpu, medHeap, medGcCount, medGcTime, medThreads
        );

        String status = determinePointHealthStatus(medErrorPct, medP99);
        boolean stopped = medErrorPct > 1.0 || medP99 > 2000;

        return new StepResult(stepIndex, concurrency, medTotal, medGoodput, medCap503, medOtherErr, medErr,
                medThroughputRps, medGoodputRps, medP50, medP90, medP95, medP99, medP999, medErrorPct,
                stopped, status, medSystemMetrics);
    }

    public static double calculateMedianDouble(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        } else {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        }
    }

    public static long calculateMedianLong(List<Long> values) {
        if (values == null || values.isEmpty()) return 0L;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        } else {
            return Math.round((sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0);
        }
    }

    public static String analyzeSaturationCurve(List<StepResult> steps) {
        if (steps == null || steps.isEmpty()) {
            return "No step data available for saturation analysis.";
        }

        double maxGoodputRps = 0.0;
        int maxGoodputClients = 0;
        int plateauClients = -1;
        int latencyKneeClients = -1;

        for (int i = 0; i < steps.size(); i++) {
            StepResult step = steps.get(i);
            if (step.getGoodputRps() > maxGoodputRps) {
                maxGoodputRps = step.getGoodputRps();
                maxGoodputClients = step.getConcurrency();
            }

            if (latencyKneeClients == -1 && (step.getP95LatencyMs() > 500 || step.getP99LatencyMs() > 1000)) {
                latencyKneeClients = step.getConcurrency();
            }

            if (i > 0 && plateauClients == -1 && maxGoodputRps > 0) {
                if (step.getGoodputRps() < maxGoodputRps * 0.98) {
                    plateauClients = steps.get(i - 1).getConcurrency();
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Peak Goodput: %,.0f RPS at %,d clients.", maxGoodputRps, maxGoodputClients));
        if (latencyKneeClients > 0) {
            sb.append(String.format(" Latency Knee observed at %,d clients.", latencyKneeClients));
        } else {
            sb.append(" Latency knee not reached within measured range.");
        }
        if (plateauClients > 0) {
            sb.append(String.format(" Throughput Plateau / Inflection Point reached at %,d clients.", plateauClients));
        } else {
            sb.append(" Throughput continued scaling linearly or stably.");
        }
        return sb.toString();
    }

    public static String formatReport(BenchmarkResult result) {
        StringBuilder sb = new StringBuilder();
        String modeHeader;
        if ("quick".equalsIgnoreCase(result.getMode())) {
            modeHeader = "QUICK SMOKE";
        } else if ("compare-fastpath".equalsIgnoreCase(result.getMode())) {
            modeHeader = "FAST-PATH DUAL COMPARISON";
        } else if ("compare-cap".equalsIgnoreCase(result.getMode())) {
            modeHeader = "CAP EXPERIMENT DUAL COMPARISON";
        } else {
            modeHeader = "STRESS RAMP-UP (13 TIERS)";
        }

        sb.append("====================================================================================================\n");
        sb.append(String.format("           GATEBRIDGE SCIENTIFIC BENCHMARK REPORT (Mode: %s)\n", modeHeader));
        sb.append("====================================================================================================\n");
        if (result.getEnvironmentInfo() != null) {
            SystemMetricsCollector.EnvironmentInfo env = result.getEnvironmentInfo();
            sb.append(String.format("Environment: %s %s (%s) | Cores: %d | Java: %s (%s) | Max Heap: %dMB\n",
                    env.getOsName(), env.getOsVersion(), env.getOsArch(), env.getAvailableProcessors(),
                    env.getJavaVersion(), env.getJavaVendor(), env.getMaxHeapMb()));
        }
        sb.append(String.format("Configuration: Protocol: %s | Target: %s | Mode: %s\n\n",
                result.getProtocol().toUpperCase(), result.getTarget(), result.getMode()));

        if ("compare-fastpath".equalsIgnoreCase(result.getMode()) && result.getComparisonSteps() != null) {
            sb.append("FAST-PATH DUAL COMPARISON (Fast-Path OFF vs Fast-Path ON):\n");
            sb.append(String.format("%-8s %16s %10s %16s %10s %18s %16s\n",
                    "CLIENTS", "OFF GOODPUT (RPS)", "OFF P99", "ON GOODPUT (RPS)", "ON P99", "GOODPUT DELTA", "LATENCY DELTA"));
            sb.append("----------------------------------------------------------------------------------------------------\n");

            List<StepResult> stepsOn = result.getSteps();
            List<StepResult> stepsOff = result.getComparisonSteps();
            int count = Math.min(stepsOn.size(), stepsOff.size());
            for (int i = 0; i < count; i++) {
                StepResult on = stepsOn.get(i);
                StepResult off = stepsOff.get(i);
                double gDelta = on.getGoodputRps() - off.getGoodputRps();
                double gPct = off.getGoodputRps() > 0 ? (gDelta / off.getGoodputRps() * 100.0) : 0.0;
                long p99Delta = on.getP99LatencyMs() - off.getP99LatencyMs();

                sb.append(String.format("%-8d %16.0f %9dms %16.0f %9dms %+17.0f (%+.1f%%) %+14dms\n",
                        on.getConcurrency(), off.getGoodputRps(), off.getP99LatencyMs(),
                        on.getGoodputRps(), on.getP99LatencyMs(), gDelta, gPct, p99Delta));
            }
            sb.append("----------------------------------------------------------------------------------------------------\n\n");
        } else if ("compare-cap".equalsIgnoreCase(result.getMode()) && result.getComparisonSteps() != null) {
            sb.append("CAP EXPERIMENT DUAL COMPARISON (CAP=1500 vs CAP=UNLIMITED):\n");
            sb.append(String.format("%-8s %16s %12s %10s %18s %14s %12s %14s\n",
                    "CLIENTS", "CAP1500 GOODPUT", "CAP1500 503s", "CAP1500 P99", "UNLIMITED GOODPUT", "UNLIMITED ERR", "UNLIMITED P99", "P99 DELTA"));
            sb.append("----------------------------------------------------------------------------------------------------\n");

            List<StepResult> cap1500 = result.getSteps();
            List<StepResult> unlim = result.getComparisonSteps();
            int count = Math.min(cap1500.size(), unlim.size());
            for (int i = 0; i < count; i++) {
                StepResult c1500 = cap1500.get(i);
                StepResult cUnlim = unlim.get(i);
                long p99Delta = cUnlim.getP99LatencyMs() - c1500.getP99LatencyMs();

                sb.append(String.format("%-8d %16.0f %12d %9dms %18.0f %14d %11dms %+13dms\n",
                        c1500.getConcurrency(), c1500.getGoodputRps(), c1500.getCap503Requests(), c1500.getP99LatencyMs(),
                        cUnlim.getGoodputRps(), cUnlim.getErrorRequests(), cUnlim.getP99LatencyMs(), p99Delta));
            }
            sb.append("----------------------------------------------------------------------------------------------------\n\n");
        }

        sb.append("MEASURED TIER METRICS:\n");
        sb.append(String.format("%-4s %9s %15s %8s %8s %8s %8s %8s %8s %8s %7s %9s   %s\n",
                "STEP", "CLIENTS", "GOODPUT (RPS)", "p50", "p90", "p95", "p99", "p99.9", "ERRORS", "503 CAP", "CPU%", "RAM(MB)", "STATUS"));
        sb.append("----------------------------------------------------------------------------------------------------\n");

        for (StepResult step : result.getSteps()) {
            String clientsStr = String.format("%,d", step.getConcurrency());
            String gRpsStr = String.format("%,.0f", step.getGoodputRps());
            String p50Str = String.format("%dms", step.getP50LatencyMs());
            String p90Str = String.format("%dms", step.getP90LatencyMs());
            String p95Str = String.format("%dms", step.getP95LatencyMs());
            String p99Str = String.format("%dms", step.getP99LatencyMs());
            String p999Str = String.format("%dms", step.getP999LatencyMs());
            String errStr = String.format("%,d", step.getErrorRequests());
            String cap503Str = String.format("%,d", step.getCap503Requests());
            String cpuStr = String.format("%.1f%%", step.getCpuPercent());
            String heapStr = String.format("%dMB", step.getHeapUsedMb());

            sb.append(String.format("%-4d %9s %15s %8s %8s %8s %8s %8s %8s %8s %7s %9s   %s\n",
                    step.getStepIndex(), clientsStr, gRpsStr, p50Str, p90Str, p95Str, p99Str, p999Str, errStr, cap503Str, cpuStr, heapStr, step.getStatus()));
        }

        sb.append("----------------------------------------------------------------------------------------------------\n");
        sb.append("SUMMARY & SATURATION ANALYSIS:\n");
        if (result.getMaxStableConcurrency() > 0) {
            sb.append(String.format("- Maximum Stable Capacity: %,d concurrent clients @ %,.0f Goodput RPS\n",
                    result.getMaxStableConcurrency(), result.getMaxStableRps()));
        } else {
            sb.append("- Maximum Stable Capacity: 0 concurrent clients @ 0 Goodput RPS\n");
        }
        sb.append(String.format("- Bottleneck / Point Health: %s\n", result.getBottleneckInfo()));
        if (result.getSaturationAnalysis() != null) {
            sb.append(String.format("- Saturation Curve Analysis: %s\n", result.getSaturationAnalysis()));
        }
        sb.append("====================================================================================================\n\n");

        sb.append("ASCII VISUALIZATION CHARTS:\n");
        sb.append(BenchmarkChartGenerator.generateAllCharts(result.getSteps()));

        return sb.toString();
    }

    public static void printHelp() {
        System.out.println("GateBridge BenchmarkRunner CLI");
        System.out.println("Usage: java hexacloud.infra.benchmark.BenchmarkRunner [options]");
        System.out.println("Options:");
        System.out.println("  --mode=quick|stress|compare-fastpath|compare-cap Execution mode: quick (100 clients), stress (13 ramp tiers), compare-fastpath (dual Fast-Path OFF/ON), or compare-cap (dual CAP=1500/UNLIMITED). Default: quick");
        System.out.println("  --protocol=http|tcp|ws|telnet|all Target protocol. Default: http");
        System.out.println("  --target=<url|host:port> Target URL or endpoint to benchmark. Default: protocol dependent");
        System.out.println("  --warmup=<duration>    Warm-up phase duration per tier (e.g., 3s). Default: 3s");
        System.out.println("  --duration=<duration>  Measurement duration per run (e.g., 10s). Default: 10s");
        System.out.println("  --runs=<number>        Number of measurement runs per tier for 3-run median repeatability. Default: 3");
        System.out.println("  --cap=<number|0>       Active requests cap (0 = unlimited). Default: 1500");
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
                singleConfig.setWarmupSeconds(config.getWarmupSeconds());
                singleConfig.setDurationSeconds(config.getDurationSeconds());
                singleConfig.setRuns(config.getRuns());
                singleConfig.setCap(config.getCap());
                BenchmarkResult res = runner.runBenchmark(singleConfig);
                System.out.println(formatReport(res));
            }
        } else {
            BenchmarkResult res = runner.runBenchmark(config);
            System.out.println(formatReport(res));
        }
    }
}
