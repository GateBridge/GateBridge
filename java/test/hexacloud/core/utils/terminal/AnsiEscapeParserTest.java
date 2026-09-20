package hexacloud.core.utils.terminal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static hexacloud.core.tui.TuiConstants.*;

public class AnsiEscapeParserTest {

    @Test
    public void testCsiArrowKeys() {
        assertEquals(KEY_UP, AnsiEscapeParser.parse("\033[A"));
        assertEquals(KEY_DOWN, AnsiEscapeParser.parse("\033[B"));
        assertEquals(KEY_RIGHT, AnsiEscapeParser.parse("\033[C"));
        assertEquals(KEY_LEFT, AnsiEscapeParser.parse("\033[D"));
    }

    @Test
    public void testSs3ArrowKeys() {
        assertEquals(KEY_UP, AnsiEscapeParser.parse("\033OA"));
        assertEquals(KEY_DOWN, AnsiEscapeParser.parse("\033OB"));
        assertEquals(KEY_RIGHT, AnsiEscapeParser.parse("\033OC"));
        assertEquals(KEY_LEFT, AnsiEscapeParser.parse("\033OD"));
    }

    @Test
    public void testSingleCharacters() {
        assertEquals((int) 'a', AnsiEscapeParser.parse("a"));
        assertEquals((int) 'Z', AnsiEscapeParser.parse("Z"));
        assertEquals((int) ' ', AnsiEscapeParser.parse(" "));
        assertEquals(KEY_ENTER, AnsiEscapeParser.parse("\n"));
        assertEquals(KEY_ENTER, AnsiEscapeParser.parse("\r"));
        assertEquals(KEY_BACKSPACE, AnsiEscapeParser.parse("\b"));
        assertEquals(KEY_BACKSPACE, AnsiEscapeParser.parse(new byte[]{127}));
        assertEquals(KEY_ESC, AnsiEscapeParser.parse("\033"));
    }

    @Test
    public void testExtendedKeys() {
        assertEquals(KEY_HOME, AnsiEscapeParser.parse("\033[H"));
        assertEquals(KEY_HOME, AnsiEscapeParser.parse("\033[1~"));
        assertEquals(KEY_HOME, AnsiEscapeParser.parse("\033[7~"));

        assertEquals(KEY_END, AnsiEscapeParser.parse("\033[F"));
        assertEquals(KEY_END, AnsiEscapeParser.parse("\033[4~"));
        assertEquals(KEY_END, AnsiEscapeParser.parse("\033[8~"));

        assertEquals(KEY_PAGE_UP, AnsiEscapeParser.parse("\033[5~"));
        assertEquals(KEY_PAGE_DOWN, AnsiEscapeParser.parse("\033[6~"));
        assertEquals(KEY_BACKSPACE, AnsiEscapeParser.parse("\033[3~"));
    }

    @Test
    public void testStreamingParser() {
        AnsiEscapeParser parser = new AnsiEscapeParser();
        assertEquals(-1, parser.parseNextByte(27));
        assertEquals(-1, parser.parseNextByte('[')) ;
        assertEquals(KEY_UP, parser.parseNextByte('A'));

        assertEquals(-1, parser.parseNextByte(27));
        assertEquals(-1, parser.parseNextByte('O'));
        assertEquals(KEY_DOWN, parser.parseNextByte('B'));

        assertEquals((int) 'x', parser.parseNextByte('x'));
    }

    @Test
    public void testEdgeCasesAndFlush() {
        AnsiEscapeParser parser = new AnsiEscapeParser();
        // Single ESC without follow up byte
        assertEquals(-1, parser.parseNextByte(27));
        assertEquals(KEY_ESC, parser.flushPendingEscape());

        // Unknown escape sequence \033[Z
        assertEquals(-1, parser.parseNextByte(27));
        assertEquals(-1, parser.parseNextByte('['));
        assertEquals(-1, parser.parseNextByte('Z'));

        // Invalid / null byte arrays
        assertEquals(-1, AnsiEscapeParser.parse((byte[]) null));
        assertEquals(-1, AnsiEscapeParser.parse(new byte[0]));
        assertEquals(-1, AnsiEscapeParser.parse((String) null));
        assertEquals(-1, AnsiEscapeParser.parse(""));
    }
}
