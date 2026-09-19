package hexacloud.core.tui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TuiFrameBufferTest {

    @Test
    void testFrameBufferAccumulationAndOutput() {
        TuiFrameBuffer buffer = new TuiFrameBuffer(80, 24);
        buffer.printAt(1, 1, "Hello GateBridge");
        buffer.printAt(1, 2, "Line 2");
        
        String output = buffer.buildFrameString();
        assertNotNull(output);
        assertTrue(output.contains("Hello GateBridge"));
        assertTrue(output.contains("Line 2"));
    }
}
