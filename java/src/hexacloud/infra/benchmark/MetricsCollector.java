package hexacloud.infra.benchmark;

import java.util.concurrent.atomic.LongAdder;

public class MetricsCollector {
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder errorRequests = new LongAdder();
    private final LongAdder[] buckets = new LongAdder[11];

    private static final long[] BUCKET_UPPER_BOUNDS = {
        0,    // <1ms
        5,    // 1-5ms
        10,   // 5-10ms
        25,   // 10-25ms
        50,   // 25-50ms
        100,  // 50-100ms
        250,  // 100-250ms
        500,  // 250-500ms
        1000, // 500-1000ms
        2000, // 1000-2000ms
        2001  // >2000ms
    };

    public MetricsCollector() {
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new LongAdder();
        }
    }

    private int getBucketIndex(long latencyMs) {
        if (latencyMs < 1) return 0;
        if (latencyMs < 5) return 1;
        if (latencyMs < 10) return 2;
        if (latencyMs < 25) return 3;
        if (latencyMs < 50) return 4;
        if (latencyMs < 100) return 5;
        if (latencyMs < 250) return 6;
        if (latencyMs < 500) return 7;
        if (latencyMs < 1000) return 8;
        if (latencyMs < 2000) return 9;
        return 10;
    }

    public void recordRequest(long latencyMs, boolean success) {
        totalRequests.increment();
        if (!success) {
            errorRequests.increment();
        }
        int bucket = getBucketIndex(latencyMs);
        buckets[bucket].increment();
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
        for (int i = 0; i < buckets.length; i++) {
            accumulated += buckets[i].sum();
            if (accumulated >= targetCount) {
                return BUCKET_UPPER_BOUNDS[i];
            }
        }
        return BUCKET_UPPER_BOUNDS[buckets.length - 1];
    }

    public synchronized void reset() {
        totalRequests.reset();
        errorRequests.reset();
        for (LongAdder bucket : buckets) {
            bucket.reset();
        }
    }
}
