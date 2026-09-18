package hexacloud.infra.benchmark;

import java.util.concurrent.atomic.LongAdder;

public class MetricsCollector {
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder goodputRequests = new LongAdder();
    private final LongAdder cap503Requests = new LongAdder();
    private final LongAdder otherErrorRequests = new LongAdder();
    private final LongAdder[] latencyBuckets = new LongAdder[10002];

    public MetricsCollector() {
        for (int i = 0; i < latencyBuckets.length; i++) {
            latencyBuckets[i] = new LongAdder();
        }
    }

    public void recordRequest(long latencyMs, int statusCode) {
        totalRequests.increment();
        if (statusCode >= 200 && statusCode < 400) {
            goodputRequests.increment();
            int index = (int) Math.min(10001, Math.max(0, latencyMs));
            latencyBuckets[index].increment();
        } else if (statusCode == 503) {
            cap503Requests.increment();
        } else {
            otherErrorRequests.increment();
        }
    }

    public void recordRequest(long latencyMs, boolean success) {
        totalRequests.increment();
        if (success) {
            goodputRequests.increment();
            int index = (int) Math.min(10001, Math.max(0, latencyMs));
            latencyBuckets[index].increment();
        } else {
            otherErrorRequests.increment();
        }
    }

    public long getTotalRequests() {
        return totalRequests.sum();
    }

    public long getGoodputRequests() {
        return goodputRequests.sum();
    }

    public long getCap503Requests() {
        return cap503Requests.sum();
    }

    public long getOtherErrorRequests() {
        return otherErrorRequests.sum();
    }

    public long getErrorRequests() {
        return cap503Requests.sum() + otherErrorRequests.sum();
    }

    public double getErrorPercentage() {
        long total = getTotalRequests();
        return total == 0 ? 0.0 : (double) getErrorRequests() / total * 100.0;
    }

    public double getGoodputPercentage() {
        long total = getTotalRequests();
        return total == 0 ? 0.0 : (double) getGoodputRequests() / total * 100.0;
    }

    public long getPercentileLatency(double percentile) {
        long count = goodputRequests.sum();
        if (count == 0) return 0;

        long targetCount = (long) Math.ceil((percentile / 100.0) * count);
        if (targetCount <= 0) targetCount = 1;

        long accumulated = 0;
        for (int i = 0; i < latencyBuckets.length; i++) {
            accumulated += latencyBuckets[i].sum();
            if (accumulated >= targetCount) {
                return i;
            }
        }
        return 10001;
    }

    public synchronized void reset() {
        totalRequests.reset();
        goodputRequests.reset();
        cap503Requests.reset();
        otherErrorRequests.reset();
        for (LongAdder bucket : latencyBuckets) {
            bucket.reset();
        }
    }
}
