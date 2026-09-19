package hexacloud.core.tui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TuiKeyHandlerTreeNavigationTest {

    private TerminalUI tui;
    private TuiKeyHandler keyHandler;
    private TuiState state;

    @BeforeEach
    void setUp() {
        tui = new TerminalUI("Test Control Plane");
        keyHandler = tui.keyHandler();
        state = tui.state();
    }

    @Test
    void testExpansionToggleOnKey() {
        TuiTreeNode node = new TuiTreeNode(TuiTreeNode.NodeType.GATEWAY, "gw-1", "Gateway 1", 0);
        assertTrue(node.isExpanded());
        node.setExpanded(!node.isExpanded());
        assertFalse(node.isExpanded());
    }

    @Test
    void testTreeNavigationDownAndUp() {
        TuiTreeNode gw1 = new TuiTreeNode(TuiTreeNode.NodeType.GATEWAY, "gw-1", "Gateway 1", 0);
        TuiTreeNode gw2 = new TuiTreeNode(TuiTreeNode.NodeType.GATEWAY, "gw-2", "Gateway 2", 0);
        state.rootTreeNodes.add(gw1);
        state.rootTreeNodes.add(gw2);

        assertEquals(0, state.selectedTreeIndex);

        // DOWN Arrow (1001)
        keyHandler.handleKeyPress(1001);
        assertEquals(1, state.selectedTreeIndex);

        // DOWN Arrow again -> Wrap to 0
        keyHandler.handleKeyPress(1001);
        assertEquals(0, state.selectedTreeIndex);

        // UP Arrow (1000) -> Wrap to 1
        keyHandler.handleKeyPress(1000);
        assertEquals(1, state.selectedTreeIndex);

        // UP Arrow again -> Decrement to 0
        keyHandler.handleKeyPress(1000);
        assertEquals(0, state.selectedTreeIndex);
    }

    @Test
    void testSpaceAndEnterToggleExpansion() {
        TuiTreeNode gw = new TuiTreeNode(TuiTreeNode.NodeType.GATEWAY, "gw-1", "Gateway 1", 0);
        TuiTreeNode cluster = new TuiTreeNode(TuiTreeNode.NodeType.CLUSTER, "cl-1", "Cluster 1", 1);
        gw.addChild(cluster);
        state.rootTreeNodes.add(gw);

        assertTrue(gw.isExpanded());

        // Press Space (32) to collapse selected node (gw at index 0)
        keyHandler.handleKeyPress(32);
        assertFalse(gw.isExpanded());

        // Press Enter (10) to re-expand selected node
        keyHandler.handleKeyPress(10);
        assertTrue(gw.isExpanded());
    }

    @Test
    void testShortcutKeys() {
        assertTrue(state.running);
        assertEquals(TuiConstants.VIEW_DASHBOARD, state.currentView);

        // 'L' -> Full Logs
        keyHandler.handleKeyPress('l');
        assertEquals(TuiConstants.VIEW_FULL_LOGS, state.currentView);

        // Escape in Full Logs returns to Dashboard
        keyHandler.handleKeyPress(27);
        assertEquals(TuiConstants.VIEW_DASHBOARD, state.currentView);

        // 'Q' -> Exit
        keyHandler.handleKeyPress('q');
        assertFalse(state.running);
    }
}
