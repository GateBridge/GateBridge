package hexacloud.core.tui;

/**
 * Global constants for the Terminal User Interface, including ANSI colors,
 * navigation view modes, active panels, and loop delays.
 */
public final class TuiConstants {
    
    // ANSI Terminal Colors
    public static final String RESET = "\033[0m";
    public static final String GREEN = "\033[32m";
    public static final String RED = "\033[31m";
    public static final String YELLOW = "\033[33m";
    public static final String CYAN = "\033[36m";
    public static final String MAGENTA = "\033[35m";
    public static final String GRAY = "\033[90m";
    public static final String WHITE_BOLD = "\033[1;37m";

    // View Navigation Modes
    public static final int VIEW_DASHBOARD = 0;
    public static final int VIEW_CLUSTER_DETAIL = 1;
    public static final int VIEW_FULL_LOGS = 2;
    public static final int VIEW_NODE_CONFIG = 3;

    // Active Panel Focus
    public static final int PANEL_TREE = 0;
    public static final int PANEL_CLUSTERS = 0;
    public static final int PANEL_SERVICES = 1;
    public static final int PANEL_GATEWAYS = 2;

    // Loop & Rendering Delays (ms)
    public static final long UI_RENDER_LOOP_DELAY_MS = 100L;
    public static final long UI_INPUT_POLL_DELAY_MS = 50L;
    public static final long UI_KEY_POLL_DELAY_MS = 15L;

    // Terminal Key Codes
    public static final int KEY_ENTER = 10;
    public static final int KEY_ESC = 27;
    public static final int KEY_BACKSPACE = 127;
    public static final int KEY_UP = 1000;
    public static final int KEY_DOWN = 1001;
    public static final int KEY_RIGHT = 1002;
    public static final int KEY_LEFT = 1003;
    public static final int KEY_PAGE_UP = 1004;
    public static final int KEY_PAGE_DOWN = 1005;
    public static final int KEY_HOME = 1006;
    public static final int KEY_END = 1007;
    public static final int KEY_RESIZE = 2000;

    private TuiConstants() {}
}
