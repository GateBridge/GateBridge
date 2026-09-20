package hexacloud.core.tui.harness;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Harness to record and verify deterministic screen frames for TUI parity checks.
 */
public class GoldenFrameCapture {

    public static final Map<String, String> GOLDEN_FRAMES = new ConcurrentHashMap<>();

    /**
     * Asserts frame parity against stored golden frame, or records initial golden frame if missing.
     *
     * @param name Identifier of the golden frame
     * @param actualFrame Captured frame string
     * @return true if actual frame matches golden frame or if new golden frame was stored
     */
    public static boolean assertParity(String name, String actualFrame) {
        if (name == null || actualFrame == null) {
            return false;
        }
        if (!GOLDEN_FRAMES.containsKey(name)) {
            GOLDEN_FRAMES.put(name, actualFrame);
            return true;
        }
        String golden = GOLDEN_FRAMES.get(name);
        return golden != null && golden.equals(actualFrame);
    }
}
