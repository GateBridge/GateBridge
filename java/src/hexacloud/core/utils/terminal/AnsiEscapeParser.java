package hexacloud.core.utils.terminal;

import static hexacloud.core.tui.TuiConstants.*;

/**
 * Universal Java ANSI Escape Sequence Parser.
 * Maps escape sequence byte arrays, strings, or raw byte streams to integer key codes
 * matching {@link hexacloud.core.tui.TuiConstants}.
 */
public class AnsiEscapeParser {

    private static final int STATE_IDLE = 0;
    private static final int STATE_ESC = 1;
    private static final int STATE_CSI = 2;
    private static final int STATE_SS3 = 3;
    private static final int STATE_CSI_PARAM = 4;

    private int state = STATE_IDLE;
    private final StringBuilder paramBuffer = new StringBuilder();

    /**
     * Parses a complete byte array representing a single key or escape sequence.
     *
     * @param bytes sequence bytes
     * @return integer key code matching TuiConstants
     */
    public static int parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return -1;
        }
        return parse(bytes, 0, bytes.length);
    }

    /**
     * Parses a byte array slice.
     *
     * @param bytes sequence bytes
     * @param offset start index
     * @param length number of bytes
     * @return integer key code matching TuiConstants
     */
    public static int parse(byte[] bytes, int offset, int length) {
        if (bytes == null || length <= 0 || offset < 0 || offset + length > bytes.length) {
            return -1;
        }

        AnsiEscapeParser parser = new AnsiEscapeParser();
        int lastResult = -1;
        for (int i = offset; i < offset + length; i++) {
            int b = bytes[i] & 0xFF;
            int res = parser.parseNextByte(b);
            if (res != -1) {
                lastResult = res;
            }
        }

        if (lastResult != -1) {
            return lastResult;
        }
        return parser.flushPendingEscape();
    }

    /**
     * Parses an escape sequence string.
     *
     * @param seq escape sequence string (e.g. "\033[A" or "\033OA")
     * @return integer key code matching TuiConstants
     */
    public static int parse(String seq) {
        if (seq == null || seq.isEmpty()) {
            return -1;
        }
        byte[] bytes = seq.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        return parse(bytes);
    }

    /**
     * Stateful streaming parser. Pushes a single raw byte into the state machine.
     *
     * @param byteVal raw byte value (0..255, or ASCII code)
     * @return resolved key code if complete sequence parsed, or -1 if sequence is in-flight / discarded
     */
    public synchronized int parseNextByte(int byteVal) {
        if (byteVal < 0) {
            return -1;
        }

        switch (state) {
            case STATE_IDLE:
                if (byteVal == KEY_ESC) { // 27
                    state = STATE_ESC;
                    paramBuffer.setLength(0);
                    return -1;
                }
                if (byteVal == 13 || byteVal == 10) { // Enter (\r or \n)
                    return KEY_ENTER;
                }
                if (byteVal == 127 || byteVal == 8) { // Backspace / Delete
                    return KEY_BACKSPACE;
                }
                return byteVal;

            case STATE_ESC:
                if (byteVal == '[') {
                    state = STATE_CSI;
                    paramBuffer.setLength(0);
                    return -1;
                }
                if (byteVal == 'O') {
                    state = STATE_SS3;
                    paramBuffer.setLength(0);
                    return -1;
                }
                if (byteVal == KEY_ESC) { // Double ESC
                    state = STATE_ESC;
                    paramBuffer.setLength(0);
                    return KEY_ESC;
                }
                // Alt + char or unrecognized ESC combination
                state = STATE_IDLE;
                return byteVal;

            case STATE_CSI:
                switch (byteVal) {
                    case 'A': state = STATE_IDLE; return KEY_UP;
                    case 'B': state = STATE_IDLE; return KEY_DOWN;
                    case 'C': state = STATE_IDLE; return KEY_RIGHT;
                    case 'D': state = STATE_IDLE; return KEY_LEFT;
                    case 'H': state = STATE_IDLE; return KEY_HOME;
                    case 'F': state = STATE_IDLE; return KEY_END;
                    default:
                        if (byteVal >= '0' && byteVal <= '9') {
                            paramBuffer.append((char) byteVal);
                            state = STATE_CSI_PARAM;
                            return -1;
                        }
                        state = STATE_IDLE;
                        return -1;
                }

            case STATE_SS3:
                switch (byteVal) {
                    case 'A': state = STATE_IDLE; return KEY_UP;
                    case 'B': state = STATE_IDLE; return KEY_DOWN;
                    case 'C': state = STATE_IDLE; return KEY_RIGHT;
                    case 'D': state = STATE_IDLE; return KEY_LEFT;
                    case 'H': state = STATE_IDLE; return KEY_HOME;
                    case 'F': state = STATE_IDLE; return KEY_END;
                    default:
                        state = STATE_IDLE;
                        return -1;
                }

            case STATE_CSI_PARAM:
                if (byteVal >= '0' && byteVal <= '9') {
                    paramBuffer.append((char) byteVal);
                    return -1;
                }
                if (byteVal == '~') {
                    state = STATE_IDLE;
                    String p = paramBuffer.toString();
                    switch (p) {
                        case "1":
                        case "7":
                            return KEY_HOME;
                        case "4":
                        case "8":
                            return KEY_END;
                        case "5":
                            return KEY_PAGE_UP;
                        case "6":
                            return KEY_PAGE_DOWN;
                        case "3":
                            return KEY_BACKSPACE;
                        default:
                            return -1;
                    }
                }
                state = STATE_IDLE;
                return -1;

            default:
                state = STATE_IDLE;
                return -1;
        }
    }

    /**
     * Resets internal state to IDLE.
     */
    public synchronized void reset() {
        state = STATE_IDLE;
        paramBuffer.setLength(0);
    }

    /**
     * Flushes any pending ESC state.
     *
     * @return KEY_ESC if state was STATE_ESC, or -1 otherwise
     */
    public synchronized int flushPendingEscape() {
        if (state == STATE_ESC) {
            state = STATE_IDLE;
            paramBuffer.setLength(0);
            return KEY_ESC;
        }
        state = STATE_IDLE;
        paramBuffer.setLength(0);
        return -1;
    }
}
