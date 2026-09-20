package hexacloud.core.tui.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.PingProtocol;
import hexacloud.core.model.RoutingProtocol;
import hexacloud.core.model.ServerNode;
import hexacloud.core.tui.TerminalUI;
import hexacloud.core.tui.TuiState;
import hexacloud.core.tui.TuiTreeNode;
import hexacloud.core.tui.model.NodeView;

public class TuiEventLoopTest {

    @Test
    void testPostEventAndProcessOneDeterministic() {
        TerminalUI tui = new TerminalUI("Test Control Plane");
        TuiEventLoop eventLoop = tui.eventLoop();

        assertTrue(eventLoop.isQueueEmpty());
        assertEquals(0, eventLoop.getQueueSize());

        // Process on empty queue
        assertFalse(eventLoop.processOne());

        // Post KeyPressEvent
        eventLoop.postEvent(new UIEvent.KeyPressEvent(1001)); // DOWN key
        assertEquals(1, eventLoop.getQueueSize());
        assertFalse(eventLoop.isQueueEmpty());

        // Process event deterministically
        boolean processed = eventLoop.processOne();
        assertTrue(processed);
        assertEquals(0, eventLoop.getQueueSize());
    }

    @Test
    void testSelectionStateUpdateOnProcessingEvents() {
        TerminalUI tui = new TerminalUI("Test Control Plane");
        TuiState state = tui.state();
        TuiEventLoop eventLoop = tui.eventLoop();

        // Setup tree nodes in Dashboard state
        TuiTreeNode node1 = new TuiTreeNode(TuiTreeNode.NodeType.CLUSTER, "cluster-1", "cluster-1", 0);
        TuiTreeNode node2 = new TuiTreeNode(TuiTreeNode.NodeType.CLUSTER, "cluster-2", "cluster-2", 0);
        state.rootTreeNodes.add(node1);
        state.rootTreeNodes.add(node2);
        state.selectedTreeIndex = 0;

        // Post DOWN arrow key press event
        eventLoop.postEvent(new UIEvent.KeyPressEvent(1001)); // DOWN Arrow
        eventLoop.processOne();

        // Verify selected tree index updated
        assertEquals(1, state.selectedTreeIndex);

        // Post UP arrow key press event
        eventLoop.postEvent(new UIEvent.KeyPressEvent(1000)); // UP Arrow
        eventLoop.processOne();

        // Verify selected tree index moved back to 0
        assertEquals(0, state.selectedTreeIndex);
    }

    @Test
    void testNodeViewImmutableSnapshot() {
        ServerNode serverNode = new ServerNode(
            "node-1", "http://127.0.0.1", 8080, NodeStatus.ONLINE, false,
            PingProtocol.HTTP, "/healthz", "X-Api-Token", "secret123",
            false, false, RoutingProtocol.HTTP
        );
        serverNode.setLatencyMs(45);
        serverNode.setCpuUsage(18.5);
        serverNode.setRamUsage(512.0);
        serverNode.setRuntime("Java 21");

        // Take snapshot
        NodeView view = NodeView.from(serverNode);

        assertNotNull(view);
        assertEquals("node-1", view.name());
        assertEquals("http://127.0.0.1", view.host());
        assertEquals(8080, view.port());
        assertEquals(NodeStatus.ONLINE, view.status());
        assertEquals(45, view.latencyMs());
        assertEquals(18.5, view.cpuUsage(), 0.001);
        assertEquals(512.0, view.ramUsage(), 0.001);
        assertEquals("Java 21", view.runtime());
        assertEquals("GATEWAY", view.role());
        assertEquals("http://127.0.0.1:8080", view.getFullHost());

        // Mutate original ServerNode on background thread simulate update
        serverNode.setLatencyMs(999);
        serverNode.setCpuUsage(99.9);
        serverNode.setRamUsage(4096.0);

        // Immutable snapshot MUST retain original values
        assertEquals(45, view.latencyMs());
        assertEquals(18.5, view.cpuUsage(), 0.001);
        assertEquals(512.0, view.ramUsage(), 0.001);
    }

    @Test
    void testSubmitAsyncOffloading() throws InterruptedException {
        TerminalUI tui = new TerminalUI("Test Control Plane");
        TuiEventLoop eventLoop = tui.eventLoop();

        AtomicBoolean asyncCompleted = new AtomicBoolean(false);
        AtomicBoolean callbackExecuted = new AtomicBoolean(false);

        eventLoop.submitAsync(
            () -> {
                asyncCompleted.set(true);
            },
            () -> {
                callbackExecuted.set(true);
            }
        );

        // Wait brief moment for virtual thread to execute and post event to queue
        Thread.sleep(100);

        // Queue should now contain the completion ActionEvent
        assertEquals(1, eventLoop.getQueueSize());
        assertTrue(asyncCompleted.get(), "Async background task should complete");
        assertFalse(callbackExecuted.get(), "Completion callback should not execute until dispatched by event loop");

        // Process event on reactor loop
        boolean processed = eventLoop.processOne();
        assertTrue(processed);
        assertTrue(callbackExecuted.get(), "Completion callback should be executed by event loop dispatch");
    }

    @Test
    void testCustomEventHandlerDispatch() {
        AtomicBoolean customHandled = new AtomicBoolean(false);
        TuiEventLoop eventLoop = new TuiEventLoop(null, event -> {
            if (event instanceof UIEvent.ActionEvent) {
                UIEvent.ActionEvent ae = (UIEvent.ActionEvent) event;
                if ("CUSTOM_TEST".equals(ae.action())) {
                    customHandled.set(true);
                }
            }
        });

        eventLoop.postEvent(new UIEvent.ActionEvent("CUSTOM_TEST"));
        assertEquals(1, eventLoop.getQueueSize());

        eventLoop.processOne();
        assertTrue(customHandled.get());
    }
}
