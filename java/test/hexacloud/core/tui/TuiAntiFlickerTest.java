package hexacloud.core.tui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import hexacloud.core.utils.terminal.NativeTerminal;

public class TuiAntiFlickerTest {

    @Test
    void testCursorHomeSequence() {
        assertDoesNotThrow(NativeTerminal::cursorHome);
    }
}
