package hexacloud.core.tui;

import hexacloud.core.tui.view.*;
import hexacloud.core.utils.terminal.NativeTerminal;
import static hexacloud.core.tui.TuiConstants.*;

/**
 * Orchestrates rendering by delegating to modular view-specific renderers.
 */
public class TuiRenderer {

    private final TerminalUI tui;
    private final DashboardViewRenderer dashboardRenderer;
    private final ClusterDetailViewRenderer clusterDetailRenderer;
    private final NodeConfigViewRenderer nodeConfigRenderer;
    private final FullLogsViewRenderer fullLogsRenderer;

    public TuiRenderer(TerminalUI tui) {
        this.tui = tui;
        this.dashboardRenderer = new DashboardViewRenderer(tui, this);
        this.clusterDetailRenderer = new ClusterDetailViewRenderer(tui, this);
        this.nodeConfigRenderer = new NodeConfigViewRenderer(tui, this);
        this.fullLogsRenderer = new FullLogsViewRenderer(tui, this);
    }

    public void draw() {
        int width = NativeTerminal.getTerminalWidth();
        int height = NativeTerminal.getTerminalHeight();
        TuiFrameBuffer frameBuffer = new TuiFrameBuffer(width, height);
        frameBuffer.beginFrame();
        draw(frameBuffer);
        frameBuffer.flushToTerminal();
    }

    public void draw(TuiFrameBuffer frameBuffer) {
        TuiState state = tui.state();

        switch (state.currentView) {
            case VIEW_DASHBOARD:
                drawHeader(frameBuffer, tui.displayName());
                dashboardRenderer.draw(frameBuffer);
                break;
            case VIEW_CLUSTER_DETAIL:
                drawHeader(frameBuffer, state.selectedClusterName + " - Cluster Console");
                clusterDetailRenderer.draw(frameBuffer);
                break;
            case VIEW_FULL_LOGS:
                drawHeader(frameBuffer, "Detailed System Logs");
                fullLogsRenderer.draw(frameBuffer);
                break;
            case VIEW_NODE_CONFIG:
                drawHeader(frameBuffer, "Node Config Panel");
                nodeConfigRenderer.draw(frameBuffer);
                break;
        }
    }

    public void drawBox(int x1, int y1, int x2, int y2, String title, boolean highlighted) {
        drawBox(null, x1, y1, x2, y2, title, highlighted);
    }

    public void drawBox(TuiFrameBuffer frameBuffer, int x1, int y1, int x2, int y2, String title, boolean highlighted) {
        String boxColor = highlighted ? WHITE_BOLD : CYAN;
        
        StringBuilder horizontal = new StringBuilder();
        for (int i = x1 + 1; i < x2; i++) horizontal.append("─");
        
        if (frameBuffer != null) {
            frameBuffer.printAt(x1, y1, boxColor + "┌" + horizontal + "┐" + RESET);
            frameBuffer.printAt(x1, y2, boxColor + "└" + horizontal + "┘" + RESET);
            
            for (int y = y1 + 1; y < y2; y++) {
                frameBuffer.printAt(x1, y, boxColor + "│" + RESET);
                frameBuffer.printAt(x2, y, boxColor + "│" + RESET);
            }
            
            if (title != null && !title.isEmpty()) {
                String titleStr = " " + title + " ";
                frameBuffer.printAt(x1 + 2, y1, boxColor + "┤" + WHITE_BOLD + titleStr + boxColor + "├" + RESET);
            }
        } else {
            NativeTerminal.printAt(x1, y1, boxColor + "┌" + horizontal + "┐" + RESET);
            NativeTerminal.printAt(x1, y2, boxColor + "└" + horizontal + "┘" + RESET);
            
            for (int y = y1 + 1; y < y2; y++) {
                NativeTerminal.printAt(x1, y, boxColor + "│" + RESET);
                NativeTerminal.printAt(x2, y, boxColor + "│" + RESET);
            }
            
            if (title != null && !title.isEmpty()) {
                String titleStr = " " + title + " ";
                NativeTerminal.printAt(x1 + 2, y1, boxColor + "┤" + WHITE_BOLD + titleStr + boxColor + "├" + RESET);
            }
        }
    }

    public void drawHeader(String viewTitle) {
        drawHeader(null, viewTitle);
    }

    public void drawHeader(TuiFrameBuffer frameBuffer, String viewTitle) {
        int width = NativeTerminal.getTerminalWidth() - 2;
        if (width < 40) width = 40; // Hard minimum
        String boxColor = CYAN;
        StringBuilder borderTop = new StringBuilder("╔");
        StringBuilder borderBottom = new StringBuilder("╚");
        for (int i = 0; i < width; i++) {
            borderTop.append("═");
            borderBottom.append("═");
        }
        borderTop.append("╗");
        borderBottom.append("╝");
        
        if (frameBuffer != null) {
            frameBuffer.printAt(1, 1, boxColor + borderTop.toString() + RESET);
        } else {
            NativeTerminal.printAt(1, 1, boxColor + borderTop.toString() + RESET);
        }
        
        int padding = Math.max(0, (width - viewTitle.length()) / 2);
        StringBuilder sb = new StringBuilder();
        sb.append("║");
        for (int i = 0; i < padding; i++) sb.append(" ");
        sb.append(WHITE_BOLD).append(viewTitle).append(boxColor);
        for (int i = 0; i < width - padding - viewTitle.length(); i++) sb.append(" ");
        sb.append("║");
        
        if (frameBuffer != null) {
            frameBuffer.printAt(1, 2, boxColor + sb.toString() + RESET);
            frameBuffer.printAt(1, 3, boxColor + borderBottom.toString() + RESET);
        } else {
            NativeTerminal.printAt(1, 2, boxColor + sb.toString() + RESET);
            NativeTerminal.printAt(1, 3, boxColor + borderBottom.toString() + RESET);
        }
    }
}
