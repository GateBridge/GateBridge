package hexacloud.infra.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

public class MetricsCollector {
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder errorRequests = new LongAdder();
    private final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

    public void recordRequest(long latencyMs, boolean success) {
        totalRequests.increment();
        if (!success) {
            errorRequests.increment();
        }
        synchronized (latencies) {
            if (latencies.size() < 100_000) {
                latencies.add(latencyMs);
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
        synchronized (latencies) {
            if (latencies.isEmpty()) return 0;
            List<Long> sorted = new ArrayList<>(latencies);
            Collections.sort(sorted);
            int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
            index = Math.max(0, Math.min(index, sorted.size() - 1));
            return sorted.get(index);
        }
    }

    public void reset() {
        totalRequests.reset();
        errorRequests.reset();
        latencies.clear();
    }
}
