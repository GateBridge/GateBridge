package hexacloud.infra.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class MetricsCollector {
    private static final int MAX_SAMPLES = 100_000;
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder errorRequests = new LongAdder();
    private final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
    private final AtomicInteger sampleCount = new AtomicInteger(0);

    public void recordRequest(long latencyMs, boolean success) {
        totalRequests.increment();
        if (!success) {
            errorRequests.increment();
        }
        latencies.offer(latencyMs);
        if (sampleCount.incrementAndGet() > MAX_SAMPLES) {
            if (latencies.poll() != null) {
                sampleCount.decrementAndGet();
            }
        }
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
        if (latencies.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(latencies);
        if (sorted.isEmpty()) return 0;
        Collections.sort(sorted);
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    public synchronized void reset() {
        totalRequests.reset();
        errorRequests.reset();
        latencies.clear();
        sampleCount.set(0);
    }
}
