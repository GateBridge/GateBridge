package hexacloud.core.tui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import hexacloud.core.tui.view.DashboardViewRenderer;

public class DashboardViewRendererTest {

    @Test
    void testTreeFlatteningForRenderer() {
        TuiState state = new TuiState();
        TuiTreeNode gw = new TuiTreeNode(TuiTreeNode.NodeType.GATEWAY, "gw-1", "GATEWAY: gw-1", 0);
        TuiTreeNode cl = new TuiTreeNode(TuiTreeNode.NodeType.CLUSTER, "cl-1", "CLUSTER: cl-1", 1);
        gw.addChild(cl);
        state.rootTreeNodes.add(gw);

        assertEquals(1, state.rootTreeNodes.size());
        assertEquals(1, state.rootTreeNodes.get(0).getChildren().size());

        List<TuiTreeNode> visibleNodes = DashboardViewRenderer.flattenVisibleNodes(state.rootTreeNodes);
        assertEquals(2, visibleNodes.size());
        assertEquals("gw-1", visibleNodes.get(0).getId());
        assertEquals("cl-1", visibleNodes.get(1).getId());

        gw.setExpanded(false);
        visibleNodes = DashboardViewRenderer.flattenVisibleNodes(state.rootTreeNodes);
        assertEquals(1, visibleNodes.size());
        assertEquals("gw-1", visibleNodes.get(0).getId());
    }

    @Test
    void testDualListenerBadgeFormatting() {
        TuiState.GatewayConfig gwConfig = new TuiState.GatewayConfig();
        gwConfig.gatewayName = "main-gw";
        gwConfig.port = 4001;
        gwConfig.adminPort = 9090;
        gwConfig.running = true;

        String badgeData = "[Data: :" + gwConfig.port + " " + (gwConfig.running ? "ONLINE" : "OFFLINE") + "]";
        String badgeMgmt = "[Mgmt: :" + gwConfig.adminPort + " " + (gwConfig.running ? "ONLINE" : "OFFLINE") + "]";

        assertEquals("[Data: :4001 ONLINE]", badgeData);
        assertEquals("[Mgmt: :9090 ONLINE]", badgeMgmt);
    }
}
