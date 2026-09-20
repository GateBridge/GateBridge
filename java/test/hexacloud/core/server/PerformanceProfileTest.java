package hexacloud.core.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PerformanceProfileTest {

    @Test
    public void testBalanced1GbProfileParameters() {
        PerformanceProfile profile = PerformanceProfile.BALANCED_1GB;
        assertEquals(500, profile.getConnectionPoolSize());
        assertEquals(2500, profile.getActiveRequestsCap());
        assertEquals(64, profile.getMaxBufferPoolSize());
        assertTrue(profile.isFastPathEnabled());
    }

    @Test
    public void testResilientProfileParameters() {
        PerformanceProfile profile = PerformanceProfile.RESILIENT;
        assertEquals(500, profile.getConnectionPoolSize());
        assertEquals(1500, profile.getActiveRequestsCap());
        assertEquals(32, profile.getMaxBufferPoolSize());
        assertTrue(profile.isFastPathEnabled());
    }

    @Test
    public void testMaxPerformanceProfileParameters() {
        PerformanceProfile profile = PerformanceProfile.MAX_PERFORMANCE;
        assertEquals(1000, profile.getConnectionPoolSize());
        assertEquals(0, profile.getActiveRequestsCap());
        assertEquals(256, profile.getMaxBufferPoolSize());
        assertTrue(profile.isFastPathEnabled());
    }

    @Test
    public void testStandardProfileParameters() {
        PerformanceProfile profile = PerformanceProfile.STANDARD;
        assertEquals(50, profile.getConnectionPoolSize());
        assertEquals(0, profile.getActiveRequestsCap());
        assertEquals(16, profile.getMaxBufferPoolSize());
        assertFalse(profile.isFastPathEnabled());
    }
}
