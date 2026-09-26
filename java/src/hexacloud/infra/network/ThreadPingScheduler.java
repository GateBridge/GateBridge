package hexacloud.infra.network;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import hexacloud.core.cluster.event.ClusterEvent;
import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.cluster.event.ClusterListener;
import hexacloud.core.cluster.event.ClusterEvent.NodeStatusChanged;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.PingResult;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.PingClientPort;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.core.utils.concurrent.ThreadManager;
import hexacloud.core.config.ClusterConfig;

/**
 * Thread ping scheduler that schedules health check tasks at fixed rates 
 * using decoupled PingClientPort adapters.
 */
public class ThreadPingScheduler {

    private ScheduledExecutorService scheduler;
    private int interval = ClusterConfig.DEFAULT_PING_INTERVAL_SECONDS;
    private volatile int failureThreshold = ClusterConfig.DEFAULT_FAILURE_THRESHOLD;
    private volatile int recoveryThreshold = ClusterConfig.DEFAULT_RECOVERY_THRESHOLD;

    private final ConcurrentHashMap<String, Integer> failureCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> recoveryCounters = new ConcurrentHashMap<>();

    private final String clusterName;
    private final PingClientPort pingClient;
    private final ClusterEventBusManager eventManager;

    public ThreadPingScheduler(String clusterName, ClusterEventBusManager eventManager) {
        this.clusterName = clusterName;
        this.eventManager = eventManager;
        this.pingClient = new MultiProtocolPingAdapter();
        if (eventManager != null) {
            eventManager.sub(ClusterEvent.NodeDeregistered.class, (ClusterListener) event -> {
                if (event instanceof ClusterEvent.NodeDeregistered dereg && dereg.host() != null) {
                    failureCounters.remove(dereg.host());
                    recoveryCounters.remove(dereg.host());
                }
            });
        }
    }
    
    public void startPingScheduler(Supplier<List<ServerNode>> clusterSupplier) {

        if(scheduler == null || scheduler.isShutdown()) {
            scheduler = ThreadManager.newScheduledThreadPool(ClusterConfig.SCHEDULER_THREAD_POOL_SIZE, "ping-scheduler-");
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    for(ServerNode node : clusterSupplier.get()) {
                        if(node != null) {
                            pingClusterNode(node);
                        }
                    }
                } catch (Exception e) {
                    DebugUtils.error(clusterName, null, "Unexpected error in ping scheduler execution", e);
                }
            }, 0, this.interval, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    public void stopPingScheduler() {
        if(scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if(!scheduler.awaitTermination(ClusterConfig.AWAIT_TERMINATION_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch(InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void setInterval(int intervalInSeconds) {
        this.interval = intervalInSeconds;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = Math.max(1, failureThreshold);
    }

    public int getRecoveryThreshold() {
        return recoveryThreshold;
    }

    public void setRecoveryThreshold(int recoveryThreshold) {
        this.recoveryThreshold = Math.max(1, recoveryThreshold);
    }

    public void evaluatePingOutcome(ServerNode node, NodeStatus resultStatus) {
        String nodeId = node.getId();
        if (resultStatus == NodeStatus.ONLINE) {
            failureCounters.put(nodeId, 0);
            int successes = recoveryCounters.compute(nodeId, (k, v) -> v == null ? 1 : v + 1);
            if (node.status() != NodeStatus.ONLINE && successes >= recoveryThreshold) {
                recoveryCounters.put(nodeId, 0);
                eventManager.dispatch(new NodeStatusChanged(node.getFullHost(), NodeStatus.ONLINE, nodeId));
                DebugUtils.info("Dispatching NodeStatusChanged ONLINE event for node " + node.getFullHost() + " (" + nodeId + ")");
            }
        } else {
            recoveryCounters.put(nodeId, 0);
            int failures = failureCounters.compute(nodeId, (k, v) -> v == null ? 1 : v + 1);
            if (node.status() != NodeStatus.OFFLINE && failures >= failureThreshold) {
                failureCounters.put(nodeId, 0);
                eventManager.dispatch(new NodeStatusChanged(node.getFullHost(), NodeStatus.OFFLINE, nodeId));
                DebugUtils.info("Dispatching NodeStatusChanged OFFLINE event for node " + node.getFullHost() + " (" + nodeId + ")");
            }
        }
    }

    private void pingClusterNode(ServerNode node) {
        if (!node.pingEnabled()) {
            return;
        }
        CompletableFuture<PingResult> response = pingClient.fetchPingAsync(clusterName, node);

        response.thenAccept(result -> {
            evaluatePingOutcome(node, result.status());
            
            if (result.hasTelemetry()){
                eventManager.dispatch(new hexacloud.core.cluster.event.ClusterEvent.NodeTelemetryUpdated(node.getFullHost(), node.getId()));
            }
        });
    }
}
