package hexacloud.core.tui;

import java.util.Arrays;

public class TuiFrameBuffer {

    private final int width;
    private final int height;
    private final StringBuilder buffer;

    public TuiFrameBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.buffer = new StringBuilder(width * height * 4);
    }

    public void beginFrame() {
        buffer.setLength(0);
        buffer.append("\u001B[?25l"); // Hide cursor
        buffer.append("\u001B[H");     // Move cursor home (1,1) without clear
    }

    public void printAt(int x, int y, String text) {
        if (text == null || text.isEmpty()) return;
        buffer.append("\u001B[").append(y).append(";").append(x).append("H").append(text);
    }

    public String buildFrameString() {
        buffer.append("\u001B[?25h"); // Restore cursor
        return buffer.toString();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void flushToTerminal() {
        System.out.print(buildFrameString());
        System.out.flush();
    }
}
