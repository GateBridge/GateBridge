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

    @Test
    void testNanoTimeFrameIntervalCalculation() {
        long lastRedrawNano = 1_000_000_000L;
        long nowNano = 1_035_000_000L; // 35 ms elapsed
        long elapsedMs = (nowNano - lastRedrawNano) / 1_000_000L;

        assertEquals(35L, elapsedMs);
        long minFrameIntervalMs = 33L;
        assertTrue(elapsedMs >= minFrameIntervalMs);
    }

    @Test
    void testNanoTimeSubFrameIntervalSleepCalculation() {
        long lastRedrawNano = 1_000_000_000L;
        long nowNano = 1_010_000_000L; // 10 ms elapsed
        long elapsedMs = (nowNano - lastRedrawNano) / 1_000_000L;

        assertEquals(10L, elapsedMs);
        long minFrameIntervalMs = 33L;
        assertTrue(elapsedMs < minFrameIntervalMs);

        long sleepTimeMs = minFrameIntervalMs - elapsedMs;
        assertEquals(23L, sleepTimeMs);
    }

    @Test
    void testNanoTimeSystemClockMeasurement() throws InterruptedException {
        long startNano = System.nanoTime();
        Thread.sleep(15);
        long nowNano = System.nanoTime();
        long elapsedMs = (nowNano - startNano) / 1_000_000L;

        assertTrue(elapsedMs >= 10L, "Elapsed ms calculated from nanoTime should be at least 10ms after 15ms sleep");
    }
}
