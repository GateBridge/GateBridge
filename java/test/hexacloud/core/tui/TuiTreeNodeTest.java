package hexacloud.core.tui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TuiTreeNodeTest {

    @Test
    void testTreeNodeHierarchyAndExpansion() {
        TuiTreeNode gwNode = new TuiTreeNode(TuiTreeNode.NodeType.GATEWAY, "main-gw", "GATEWAY: main-gw [Data: :4001 ONLINE] [Mgmt: :9090 ONLINE]", 0);
        TuiTreeNode clusterNode = new TuiTreeNode(TuiTreeNode.NodeType.CLUSTER, "backend-cluster", "TARGET CLUSTER: backend-cluster", 1);
        
        gwNode.addChild(clusterNode);
        
        assertEquals(1, gwNode.getChildren().size());
        assertTrue(gwNode.isExpanded());
        
        gwNode.setExpanded(false);
        assertFalse(gwNode.isExpanded());
    }
}
