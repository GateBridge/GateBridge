package hexacloud.infra.benchmark;

import java.util.concurrent.atomic.LongAdder;

public class MetricsCollector {
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder errorRequests = new LongAdder();
    private final LongAdder[] latencyBuckets = new LongAdder[2002];

    public MetricsCollector() {
        for (int i = 0; i < latencyBuckets.length; i++) {
            latencyBuckets[i] = new LongAdder();
        }
    }

    public void recordRequest(long latencyMs, boolean success) {
        totalRequests.increment();
        if (!success) {
            errorRequests.increment();
        }
        int index = (int) Math.min(2001, Math.max(0, latencyMs));
        latencyBuckets[index].increment();
    }

    public long getTotalRequests() {
        return totalRequests.sum();
    }

    public long getErrorRequests() {
        return errorRequests.sum();
    }

    public double getErrorPercentage() {
        long total = getTotalRequests();
        return total == 0 ? 0.0 : (double) getErrorRequests() / total * 100.0;
    }

    public long getPercentileLatency(double percentile) {
        long total = totalRequests.sum();
        if (total == 0) return 0;

        long targetCount = (long) Math.ceil((percentile / 100.0) * total);
        if (targetCount <= 0) targetCount = 1;

        long accumulated = 0;
        for (int i = 0; i < latencyBuckets.length; i++) {
            accumulated += latencyBuckets[i].sum();
            if (accumulated >= targetCount) {
                return i;
            }
        }
        return 2001;
    }

    public synchronized void reset() {
        totalRequests.reset();
        errorRequests.reset();
        for (LongAdder bucket : latencyBuckets) {
            bucket.reset();
        }
    }
}
