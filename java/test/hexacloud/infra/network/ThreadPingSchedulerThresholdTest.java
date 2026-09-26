package hexacloud.infra.network;

import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.cluster.event.ClusterEvent;
import hexacloud.core.cluster.event.ClusterListener;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class ThreadPingSchedulerThresholdTest {

    @Test
    void testNodeStatusFlipsOnlyAfterThresholdFailures() {
        ClusterEventBusManager eventManager = new ClusterEventBusManager();
        AtomicInteger offlineEventCount = new AtomicInteger(0);

        ClusterListener listener = event -> {
            if (event instanceof ClusterEvent.NodeStatusChanged statusEvent) {
                if (statusEvent.status() == NodeStatus.OFFLINE) {
                    offlineEventCount.incrementAndGet();
                }
            }
        };
        eventManager.sub(ClusterEvent.NodeStatusChanged.class, listener);

        ThreadPingScheduler scheduler = new ThreadPingScheduler("test-gateway", eventManager);
        scheduler.setFailureThreshold(3);

        ServerNode node = new ServerNode("http://127.0.0.1", 9999, NodeStatus.ONLINE, false);

        // Process 1st failure -> threshold not met (1/3)
        scheduler.evaluatePingOutcome(node, NodeStatus.OFFLINE);
        assertEquals(0, offlineEventCount.get(), "Status should not flip on 1st failed ping");

        // Process 2nd failure -> threshold not met (2/3)
        scheduler.evaluatePingOutcome(node, NodeStatus.OFFLINE);
        assertEquals(0, offlineEventCount.get(), "Status should not flip on 2nd failed ping");

        // Process 3rd failure -> threshold met (3/3)
        scheduler.evaluatePingOutcome(node, NodeStatus.OFFLINE);
        assertEquals(1, offlineEventCount.get(), "Status MUST flip to OFFLINE on 3rd consecutive failed ping");
    }

    @Test
    void testNodeStatusFlipsOnlyAfterThresholdRecoveries() {
        ClusterEventBusManager eventManager = new ClusterEventBusManager();
        AtomicInteger onlineEventCount = new AtomicInteger(0);

        ClusterListener listener = event -> {
            if (event instanceof ClusterEvent.NodeStatusChanged statusEvent) {
                if (statusEvent.status() == NodeStatus.ONLINE) {
                    onlineEventCount.incrementAndGet();
                }
            }
        };
        eventManager.sub(ClusterEvent.NodeStatusChanged.class, listener);

        ThreadPingScheduler scheduler = new ThreadPingScheduler("test-gateway", eventManager);
        scheduler.setRecoveryThreshold(2);

        ServerNode node = new ServerNode("http://127.0.0.1", 9999, NodeStatus.OFFLINE, false);

        // Process 1st recovery -> threshold not met (1/2)
        scheduler.evaluatePingOutcome(node, NodeStatus.ONLINE);
        assertEquals(0, onlineEventCount.get(), "Status should not flip on 1st successful ping");

        // Process 2nd recovery -> threshold met (2/2)
        scheduler.evaluatePingOutcome(node, NodeStatus.ONLINE);
        assertEquals(1, onlineEventCount.get(), "Status MUST flip to ONLINE on 2nd consecutive successful ping");
    }
}
