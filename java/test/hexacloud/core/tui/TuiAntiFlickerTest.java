package hexacloud.core.tui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import hexacloud.core.utils.terminal.NativeTerminal;

public class TuiAntiFlickerTest {

    @Test
    void testCursorHomeSequence() {
        assertDoesNotThrow(NativeTerminal::cursorHome);
    }

    @Test
    void testAtomicFrameBufferAccumulation() {
        TerminalUI tui = new TerminalUI("Test Gateway");
        TuiRenderer renderer = new TuiRenderer(tui);
        TuiFrameBuffer frameBuffer = new TuiFrameBuffer(110, 24);
        frameBuffer.beginFrame();
        
        assertDoesNotThrow(() -> renderer.draw(frameBuffer));
        String output = frameBuffer.buildFrameString();
        
        assertNotNull(output);
        assertTrue(output.contains("Test Gateway"));
        assertTrue(output.contains("SYSTEM RESOURCES"));
        assertTrue(output.contains("\u001B[?25l")); // Hides cursor at start
        assertTrue(output.contains("\u001B[?25h")); // Restores cursor at end
    }
}
