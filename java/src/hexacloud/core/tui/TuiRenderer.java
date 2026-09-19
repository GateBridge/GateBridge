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
        NativeTerminal.cursorHome();
        TuiState state = tui.state();

        switch (state.currentView) {
            case VIEW_DASHBOARD:
                drawHeader(tui.displayName());
                dashboardRenderer.draw();
                break;
            case VIEW_CLUSTER_DETAIL:
                drawHeader(state.selectedClusterName + " - Cluster Console");
                clusterDetailRenderer.draw();
                break;
            case VIEW_FULL_LOGS:
                drawHeader("Detailed System Logs");
                fullLogsRenderer.draw();
                break;
            case VIEW_NODE_CONFIG:
                drawHeader("Node Config Panel");
                nodeConfigRenderer.draw();
                break;
        }
    }

    public void drawBox(int x1, int y1, int x2, int y2, String title, boolean highlighted) {
        String boxColor = highlighted ? WHITE_BOLD : CYAN;
        
        StringBuilder horizontal = new StringBuilder();
        for (int i = x1 + 1; i < x2; i++) horizontal.append("─");
        
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

    public void drawHeader(String viewTitle) {
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
        
        NativeTerminal.printAt(1, 1, boxColor + borderTop.toString() + RESET);
        
        int padding = Math.max(0, (width - viewTitle.length()) / 2);
        StringBuilder sb = new StringBuilder();
        sb.append("║");
        for (int i = 0; i < padding; i++) sb.append(" ");
        sb.append(WHITE_BOLD).append(viewTitle).append(boxColor);
        for (int i = 0; i < width - padding - viewTitle.length(); i++) sb.append(" ");
        sb.append("║");
        
        NativeTerminal.printAt(1, 2, boxColor + sb.toString() + RESET);
        NativeTerminal.printAt(1, 3, boxColor + borderBottom.toString() + RESET);
    }
}
