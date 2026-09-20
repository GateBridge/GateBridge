package hexacloud.core.tui.harness;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import hexacloud.core.tui.TerminalUI;
import hexacloud.core.tui.TuiFrameBuffer;
import hexacloud.core.tui.view.DashboardViewRenderer;

public class GoldenFrameParityTest {

    @Test
    void testDashboard80x24FrameParity() {
        TerminalUI tui = new TerminalUI("GateBridge Control Plane");
        DashboardViewRenderer dashboardRenderer = new DashboardViewRenderer(tui, tui.renderer());

        TuiFrameBuffer frameBuffer = new TuiFrameBuffer(80, 24);
        frameBuffer.beginFrame();
        tui.renderer().drawHeader(frameBuffer, tui.displayName());
        dashboardRenderer.draw(frameBuffer);

        String frameStr = frameBuffer.buildFrameString();
        assertNotNull(frameStr, "Captured frame string must not be null");

        boolean parityMatched = GoldenFrameCapture.assertParity("dashboard_80x24", frameStr);
        assertTrue(parityMatched, "Golden frame parity assertion failed for dashboard_80x24");
    }
}
