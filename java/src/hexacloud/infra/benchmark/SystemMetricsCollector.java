package hexacloud.infra.benchmark;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

public class SystemMetricsCollector {

    public static class EnvironmentInfo {
        private final String osName;
        private final String osArch;
        private final String osVersion;
        private final int availableProcessors;
        private final String javaVersion;
        private final String javaVendor;
        private final long maxHeapMb;

        public EnvironmentInfo(String osName, String osArch, String osVersion, int availableProcessors,
                               String javaVersion, String javaVendor, long maxHeapMb) {
            this.osName = osName;
            this.osArch = osArch;
            this.osVersion = osVersion;
            this.availableProcessors = availableProcessors;
            this.javaVersion = javaVersion;
            this.javaVendor = javaVendor;
            this.maxHeapMb = maxHeapMb;
        }

        public String getOsName() { return osName; }
        public String getOsArch() { return osArch; }
        public String getOsVersion() { return osVersion; }
        public int getAvailableProcessors() { return availableProcessors; }
        public String getJavaVersion() { return javaVersion; }
        public String getJavaVendor() { return javaVendor; }
        public long getMaxHeapMb() { return maxHeapMb; }
    }

    public static class SystemSnapshot {
        private final long timestampMs;
        private final double processCpuLoad;
        private final long heapUsedMb;
        private final long totalGcCount;
        private final long totalGcTimeMs;
        private final int activeThreads;

        public SystemSnapshot(long timestampMs, double processCpuLoad, long heapUsedMb,
                               long totalGcCount, long totalGcTimeMs, int activeThreads) {
            this.timestampMs = timestampMs;
            this.processCpuLoad = processCpuLoad;
            this.heapUsedMb = heapUsedMb;
            this.totalGcCount = totalGcCount;
            this.totalGcTimeMs = totalGcTimeMs;
            this.activeThreads = activeThreads;
        }

        public long getTimestampMs() { return timestampMs; }
        public double getProcessCpuLoad() { return processCpuLoad; }
        public long getHeapUsedMb() { return heapUsedMb; }
        public long getTotalGcCount() { return totalGcCount; }
        public long getTotalGcTimeMs() { return totalGcTimeMs; }
        public int getActiveThreads() { return activeThreads; }
    }

    public static class MetricsDelta {
        private final double cpuPercent;
        private final long heapUsedMb;
        private final long gcCountDelta;
        private final long gcTimeMsDelta;
        private final int activeThreads;

        public MetricsDelta(double cpuPercent, long heapUsedMb, long gcCountDelta, long gcTimeMsDelta, int activeThreads) {
            this.cpuPercent = cpuPercent;
            this.heapUsedMb = heapUsedMb;
            this.gcCountDelta = gcCountDelta;
            this.gcTimeMsDelta = gcTimeMsDelta;
            this.activeThreads = activeThreads;
        }

        public double getCpuPercent() { return cpuPercent; }
        public long getHeapUsedMb() { return heapUsedMb; }
        public long getGcCountDelta() { return gcCountDelta; }
        public long getGcTimeMsDelta() { return gcTimeMsDelta; }
        public int getActiveThreads() { return activeThreads; }
    }

    private final OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> gcMxBeans = ManagementFactory.getGarbageCollectorMXBeans();
    private final ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();

    public EnvironmentInfo getEnvironmentInfo() {
        return new EnvironmentInfo(
                System.getProperty("os.name", "Unknown"),
                System.getProperty("os.arch", "Unknown"),
                System.getProperty("os.version", "Unknown"),
                Runtime.getRuntime().availableProcessors(),
                System.getProperty("java.version", "Unknown"),
                System.getProperty("java.vendor", "Unknown"),
                Runtime.getRuntime().maxMemory() / (1024 * 1024)
        );
    }

    public SystemSnapshot captureSnapshot() {
        long timestamp = System.currentTimeMillis();
        long heapUsed = memoryMxBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);

        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : gcMxBeans) {
            long count = gc.getCollectionCount();
            if (count > 0) gcCount += count;
            long time = gc.getCollectionTime();
            if (time > 0) gcTime += time;
        }

        int threads = threadMxBean.getThreadCount();
        double cpu = getCpuLoad();

        return new SystemSnapshot(timestamp, cpu, heapUsed, gcCount, gcTime, threads);
    }

    public MetricsDelta computeDelta(SystemSnapshot start, SystemSnapshot end) {
        long gcCountDelta = Math.max(0, end.getTotalGcCount() - start.getTotalGcCount());
        long gcTimeMsDelta = Math.max(0, end.getTotalGcTimeMs() - start.getTotalGcTimeMs());
        double cpu = Math.max(0.0, end.getProcessCpuLoad());

        return new MetricsDelta(cpu, end.getHeapUsedMb(), gcCountDelta, gcTimeMsDelta, end.getActiveThreads());
    }

    private double getCpuLoad() {
        if (osMxBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osMxBean;
            double processCpu = sunOsBean.getProcessCpuLoad();
            if (processCpu >= 0.0) {
                return processCpu * 100.0;
            }
            double sysCpu = sunOsBean.getCpuLoad();
            if (sysCpu >= 0.0) {
                return sysCpu * 100.0;
            }
        }
        double load = osMxBean.getSystemLoadAverage();
        return load >= 0.0 ? load : 0.0;
    }
}
