package hexacloud.core.tui.view;

import java.util.List;
import hexacloud.core.tui.TerminalUI;
import hexacloud.core.tui.TuiFrameBuffer;
import hexacloud.core.tui.TuiRenderer;
import hexacloud.core.tui.TuiState;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.core.utils.terminal.NativeTerminal;
import hexacloud.core.utils.common.StrUtils;

import static hexacloud.core.tui.TuiConstants.*;

/**
 * Handles visual rendering for the Full Logs View.
 */
public class FullLogsViewRenderer {
    private final TerminalUI tui;
    private final TuiRenderer mainRenderer;

    public FullLogsViewRenderer(TerminalUI tui, TuiRenderer mainRenderer) {
        this.tui = tui;
        this.mainRenderer = mainRenderer;
    }

    public void draw() {
        int W = NativeTerminal.getTerminalWidth();
        int H = NativeTerminal.getTerminalHeight();
        TuiFrameBuffer frameBuffer = new TuiFrameBuffer(W, H);
        frameBuffer.beginFrame();
        draw(frameBuffer);
        frameBuffer.flushToTerminal();
    }

    public void draw(TuiFrameBuffer frameBuffer) {
        TuiState state = tui.state();
        int W = Math.max(80, frameBuffer.getWidth());
        int H = Math.max(24, frameBuffer.getHeight());

        mainRenderer.drawBox(frameBuffer, 2, 5, W, H - 2, "DETAILED SYSTEM LOGS", true);

        List<DebugUtils.LogEntry> logs = DebugUtils.getAllLogs();
        int y = 6;
        int viewportHeight = (H - 2) - 5 - 1; // From row 6 to H - 3
        
        tui.adjustLogsViewport(logs.size(), viewportHeight);

        if (logs.isEmpty()) {
            frameBuffer.printAt(4, y, "No logs recorded yet.");
            y++;
        } else {
            int maxLineWidth = W - 7;
            for (int i = 0; i < viewportHeight; i++) {
                int index = state.logViewportStart + i;
                if (index >= logs.size()) break;

                DebugUtils.LogEntry entry = logs.get(index);
                String logLine = entry.toString();
                String prefix = index == state.selectedLogIndex ? "➔ " : "  ";
                String lineText = prefix + logLine;
                String outputLine = lineText.length() > maxLineWidth ? lineText.substring(0, maxLineWidth) : lineText + StrUtils.repeat(" ", maxLineWidth - lineText.length());

                if (entry.getLevel() == DebugUtils.LogLevel.ERROR) {
                    frameBuffer.printAt(4, y, RED + outputLine + RESET);
                } else if (entry.getLevel() == DebugUtils.LogLevel.INFO) {
                    frameBuffer.printAt(4, y, CYAN + outputLine + RESET);
                } else {
                    frameBuffer.printAt(4, y, outputLine);
                }
                y++;
            }
            if (state.logViewportStart > 0) {
                frameBuffer.printAt(W - 2, 6, WHITE_BOLD + "▲" + RESET);
            }
            if (state.logViewportStart + viewportHeight < logs.size()) {
                frameBuffer.printAt(W - 2, H - 3, WHITE_BOLD + "▼" + RESET);
            }
        }

        // Clear any remaining lines in viewport
        for (int r = y; r <= H - 3; r++) {
            frameBuffer.printAt(4, r, StrUtils.repeat(" ", W - 7));
        }

        frameBuffer.printAt(2, H - 1, StrUtils.repeat(" ", W - 4));
        frameBuffer.printAt(2, H - 1, WHITE_BOLD + "Controls:" + RESET + " [Backspace] Back to Dashboard  [UP/DOWN] Scroll logs");
    }
}
