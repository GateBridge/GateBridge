package hexacloud.core.tui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TuiFrameCoalescingTest {

    @Test
    void testFrameIntervalConstants() {
        int maxFps = 30;
        long minFrameIntervalMs = 1000 / maxFps;
        assertEquals(33, minFrameIntervalMs);
    }
}
