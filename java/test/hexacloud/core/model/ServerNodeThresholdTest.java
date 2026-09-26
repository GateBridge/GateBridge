package hexacloud.core.model;

import hexacloud.core.config.ClusterConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ServerNodeThresholdTest {

    @Test
    void testClusterConfigThresholdDefaults() {
        assertEquals(3, ClusterConfig.DEFAULT_FAILURE_THRESHOLD);
        assertEquals(2, ClusterConfig.DEFAULT_RECOVERY_THRESHOLD);
    }

    @Test
    void testServerNodeDefaultCounters() {
        ServerNode node = new ServerNode("http://localhost", 8080, NodeStatus.ONLINE, false);
        assertEquals(0, node.consecutiveFailures());
        assertEquals(0, node.consecutiveSuccesses());
    }

    @Test
    void testServerNodeWithConsecutiveCounters() {
        ServerNode node = new ServerNode("http://localhost", 8080, NodeStatus.ONLINE, false)
                .withConsecutiveCounters(3, 1);

        assertEquals(3, node.consecutiveFailures());
        assertEquals(1, node.consecutiveSuccesses());
    }

    @Test
    void testServerNodeCounterPreservationOnStatusCopy() {
        ServerNode node = new ServerNode("http://localhost", 8080, NodeStatus.ONLINE, false)
                .withConsecutiveCounters(2, 0);

        assertEquals(2, node.consecutiveFailures());
        assertEquals(0, node.consecutiveSuccesses());

        ServerNode updated = node.withStatus(NodeStatus.OFFLINE);

        assertEquals(NodeStatus.OFFLINE, updated.status());
        assertEquals(2, updated.consecutiveFailures(), "withStatus must preserve consecutiveFailures counter");
        assertEquals(0, updated.consecutiveSuccesses(), "withStatus must preserve consecutiveSuccesses counter");
    }

    @Test
    void testServerNodeCounterPreservationOnOtherCopyWithMethods() {
        ServerNode node = new ServerNode("http://localhost", 8080, NodeStatus.ONLINE, false)
                .withConsecutiveCounters(4, 2);

        ServerNode withDynamic = node.withDynamic(true);
        assertEquals(4, withDynamic.consecutiveFailures());
        assertEquals(2, withDynamic.consecutiveSuccesses());
        assertTrue(withDynamic.isDynamic());

        ServerNode withPing = node.withPingProtocol(PingProtocol.TCP);
        assertEquals(4, withPing.consecutiveFailures());
        assertEquals(2, withPing.consecutiveSuccesses());
        assertEquals(PingProtocol.TCP, withPing.pingProtocol());

        ServerNode withRouting = node.withRoutingProtocol(RoutingProtocol.TCP);
        assertEquals(4, withRouting.consecutiveFailures());
        assertEquals(2, withRouting.consecutiveSuccesses());
        assertEquals(RoutingProtocol.TCP, withRouting.routingProtocol());
    }
}
