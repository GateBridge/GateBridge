package hexacloud.core.tui;

import hexacloud.core.utils.common.DebugUtils;
import java.io.PrintStream;

/**
 * Double-buffered 2D Cell Terminal Frame Buffer.
 * Guarantees zero screen flicker, cell-level clipping, full row overwriting,
 * and robust ANSI sequence handling without string slicing corruption.
 */
public class TuiFrameBuffer {

    private final int width;
    private final int height;

    private static class Cell {
        char ch;
        String style;

        Cell() {
            this.ch = ' ';
            this.style = "";
        }

        void reset() {
            this.ch = ' ';
            this.style = "";
        }

        void set(char ch, String style) {
            this.ch = ch;
            this.style = style != null ? style : "";
        }
    }

    private final Cell[][] grid;

    public TuiFrameBuffer(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.grid = new Cell[this.height][this.width];
        for (int r = 0; r < this.height; r++) {
            for (int c = 0; c < this.width; c++) {
                grid[r][c] = new Cell();
            }
        }
    }

    public void beginFrame() {
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                grid[r][c].reset();
            }
        }
    }

    public void printAt(int x, int y, String text) {
        if (text == null || text.isEmpty()) return;
        if (y < 1 || y > height) return;

        int rowIdx = y - 1;
        int colIdx = x - 1;
        String currentStyle = "";

        int i = 0;
        int len = text.length();
        while (i < len) {
            char c = text.charAt(i);

            // Handle ANSI Escape Sequences
            if (c == '\033') {
                int start = i;
                i++;
                if (i < len && text.charAt(i) == '[') {
                    i++;
                    while (i < len) {
                        char seqChar = text.charAt(i);
                        i++;
                        if (Character.isLetter(seqChar) || seqChar == 'm') {
                            break;
                        }
                    }
                }
                String ansiSeq = text.substring(start, i);
                if (ansiSeq.equals("\033[0m") || ansiSeq.equals("\033[m")) {
                    currentStyle = "";
                } else {
                    currentStyle += ansiSeq;
                }
                continue;
            }

            // Standard printable character
            if (colIdx >= 0 && colIdx < width) {
                grid[rowIdx][colIdx].set(c, currentStyle);
            }
            colIdx++;
            i++;
        }
    }

    public String buildFrameString() {
        StringBuilder sb = new StringBuilder(width * height * 4);
        
        sb.append("\u001B[?25l"); // Hide cursor
        sb.append("\u001B[H");    // Move home (1,1)
        sb.append("\u001B[?7l");  // Disable autowrap (DECAWM)

        String activeStyle = "";

        for (int r = 0; r < height; r++) {
            sb.append("\u001B[").append(r + 1).append(";1H");
            for (int c = 0; c < width; c++) {
                Cell cell = grid[r][c];
                if (!cell.style.equals(activeStyle)) {
                    if (cell.style.isEmpty()) {
                        sb.append("\u001B[0m");
                    } else {
                        sb.append(cell.style);
                    }
                    activeStyle = cell.style;
                }
                sb.append(cell.ch);
            }
        }

        if (!activeStyle.isEmpty()) {
            sb.append("\u001B[0m");
        }
        sb.append("\u001B[?7h");  // Restore autowrap
        sb.append("\u001B[?25h"); // Restore cursor

        return sb.toString();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void flushToTerminal() {
        PrintStream out = DebugUtils.getOriginalOut();
        out.print(buildFrameString());
        out.flush();
    }
}
