package hexacloud.core.tui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TuiFrameBufferTest {

    @Test
    void testFrameBufferAccumulationAndOutput() {
        TuiFrameBuffer buffer = new TuiFrameBuffer(80, 24);
        buffer.beginFrame();
        buffer.printAt(1, 1, "Hello GateBridge");
        buffer.printAt(1, 2, "Line 2");

        String output = buffer.buildFrameString();
        assertNotNull(output);
        assertTrue(output.startsWith("\033[?25l\033[H"), "Frame output must start with \\033[?25l\\033[H");
        assertFalse(output.contains("\033[2J"), "Frame output must not contain per-frame clear screen \\033[2J");
        assertTrue(output.contains("Hello GateBridge"), "Frame output must contain 'Hello GateBridge'");
        assertTrue(output.contains("Line 2"), "Frame output must contain 'Line 2'");
        assertTrue(output.contains("\033[1;1HHello GateBridge"), "Frame output must correctly position line 1");
        assertTrue(output.contains("\033[2;1HLine 2"), "Frame output must correctly position line 2");
    }

    @Test
    void testFrameBufferDoesNotContainPerFrameClearScreen() {
        TuiFrameBuffer buffer = new TuiFrameBuffer(80, 24);
        buffer.beginFrame();
        buffer.printAt(5, 5, "Single Frame Test");
        String output = buffer.buildFrameString();
        assertFalse(output.contains("\033[2J"));
        assertTrue(output.startsWith("\033[?25l\033[H"));
    }
}
