package hexacloud.core.utils.terminal;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NativeTerminal {
    private static boolean loaded = false;
    private static boolean sttyRawModeActive = false;

    private static final AnsiEscapeParser ANSI_PARSER = new AnsiEscapeParser();
    private static final ExecutorService PLATFORM_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NativeTerminal-PlatformThread");
        t.setDaemon(true);
        return t;
    });

    static {
        // Try custom path from System Property or Env Var first
        String customPath = System.getProperty("gatebridge.jni.path");
        if (customPath == null) {
            customPath = System.getenv("GATEBRIDGE_JNI_PATH");
        }
        if (customPath != null && !customPath.trim().isEmpty()) {
            try {
                File file = new File(customPath.trim());
                if (file.exists()) {
                    System.load(file.getAbsolutePath());
                    loaded = true;
                }
            } catch (Throwable t) {
                System.err.println("Warning: Failed to load JNI library from custom path: " + customPath + ". Error: " + t.getMessage());
            }
        }

        // Try loading from packaged JAR resources next if not loaded
        if (!loaded) {
            try {
                String osName = System.getProperty("os.name").toLowerCase();
                String libName;
                if (osName.contains("win")) {
                    libName = "hexaterminal.dll";
                } else if (osName.contains("mac")) {
                    libName = "libhexaterminal.dylib";
                } else {
                    libName = "libhexaterminal.so";
                }

                String[] resourcePaths = {
                    "/native/linux-x86_64/" + libName,
                    "/native/" + libName
                };

                for (String resPath : resourcePaths) {
                    try (java.io.InputStream in = NativeTerminal.class.getResourceAsStream(resPath)) {
                        if (in != null) {
                            File tempFile = File.createTempFile("libhexaterminal", libName.substring(libName.lastIndexOf('.')));
                            tempFile.deleteOnExit();
                            try (java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile)) {
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, bytesRead);
                                }
                            }
                            System.load(tempFile.getAbsolutePath());
                            loaded = true;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }

        if (!loaded) {
            String[] possiblePaths = {
                "libhexaterminal.so",
                "java/libhexaterminal.so",
                "java/resources/native/libhexaterminal.so",
                "src/main/resources/native/linux-x86_64/libhexaterminal.so",
                "/tmp/libhexaterminal.so",
                "libhexaterminal.dylib",
                "java/libhexaterminal.dylib",
                "hexaterminal.dll",
                "java/hexaterminal.dll"
            };
            for (String path : possiblePaths) {
                try {
                    File file = new File(path);
                    if (file.exists()) {
                        System.load(file.getAbsolutePath());
                        loaded = true;
                        break;
                    }
                } catch (Throwable t) {
                    // Try next path
                }
            }
        }

        if (!loaded) {
            try {
                System.loadLibrary("hexaterminal");
                loaded = true;
            } catch (Throwable t) {
                System.err.println("Warning: libhexaterminal could not be loaded. Native JNI raw input disabled. Falling back to Java console emulation.");
            }
        }
    }

    private static native void initTerminal0();
    private static native void resetTerminal0();
    private static native void clearScreen0();
    private static native void printAt0(int x, int y, String text);
    private static native int readKey0();
    private static native boolean saveConfig0(String filepath, String content);
    private static native int getTerminalWidth0();
    private static native int getTerminalHeight0();

    /**
     * Checks if the application is running under Maven Surefire or automated test environments.
     */
    public static boolean isTestEnvironment() {
        return System.getProperty("surefire.real.class.path") != null
            || System.getProperty("surefire.test.class.path") != null
            || System.getProperty("org.codehaus.surefire") != null
            || System.getProperty("test.env") != null
            || "true".equalsIgnoreCase(System.getProperty("gatebridge.test.mode"));
    }

    /**
     * Checks if standard input is attached to an interactive terminal console.
     */
    public static boolean isInteractiveTty() {
        if (isTestEnvironment()) {
            return false;
        }
        return System.console() != null;
    }

    public static boolean loadJni(String path) {
        if (loaded) return true;
        try {
            File file = new File(path);
            if (file.exists()) {
                System.load(file.getAbsolutePath());
                loaded = true;
                return true;
            }
        } catch (Throwable t) {
            System.err.println("Warning: Failed to load JNI library from: " + path + ". Error: " + t.getMessage());
        }
        return false;
    }

    public static synchronized void initTerminal() {
        if (isTestEnvironment()) {
            return; // Skip setting raw mode during unit test execution
        }
        if (loaded) {
            try {
                initTerminal0();
                return;
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }
        // Fallback Unix stty raw mode
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("linux") || osName.contains("mac") || osName.contains("nix") || osName.contains("nux")) {
                try {
                    new ProcessBuilder("sh", "-c", "stty raw -echo < /dev/tty").start().waitFor();
                    sttyRawModeActive = true;
                } catch (Exception ignored) {}

                // One-time initial dimension fetch
                try {
                    Process p = new ProcessBuilder("sh", "-c", "tput cols < /dev/tty; tput lines < /dev/tty").start();
                    try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                        String wStr = r.readLine();
                        String hStr = r.readLine();
                        if (wStr != null && hStr != null) {
                            cachedWidth = Math.max(20, Integer.parseInt(wStr.trim()));
                            cachedHeight = Math.max(5, Integer.parseInt(hStr.trim()));
                        }
                    }
                    p.waitFor();
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            // Ignore
        }
        // Enter alternate screen buffer, clear screen once on startup, home cursor, hide cursor
        hexacloud.core.utils.common.DebugUtils.getOriginalOut().print("\033[?1049h\033[2J\033[H\033[?25l");
        hexacloud.core.utils.common.DebugUtils.getOriginalOut().flush();
    }

    public static synchronized void resetTerminal() {
        if (isTestEnvironment()) {
            return;
        }
        if (loaded) {
            try {
                resetTerminal0();
                return;
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }
        if (sttyRawModeActive) {
            try {
                new ProcessBuilder("sh", "-c", "stty sane < /dev/tty").start().waitFor();
            } catch (Exception e) {
                // Ignore
            }
            sttyRawModeActive = false;
        }
        // Exit alternate screen buffer, show cursor, and reset colors/attributes
        hexacloud.core.utils.common.DebugUtils.getOriginalOut().print("\033[?1049l\033[?25h\033[0m\n");
        hexacloud.core.utils.common.DebugUtils.getOriginalOut().flush();
    }

    public static synchronized void clearScreen() {
        if (loaded) {
            try {
                clearScreen0();
                return;
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }
        // ANSI escape sequence to clear screen, move cursor home and clear scrollback buffer
        hexacloud.core.utils.common.DebugUtils.getOriginalOut().print("\u001B[2J\u001B[H\u001B[3J");
        hexacloud.core.utils.common.DebugUtils.getOriginalOut().flush();
    }

    public static synchronized void cursorHome() {
        hexacloud.core.utils.common.DebugUtils.getOriginalOut().print("\u001B[H");
        hexacloud.core.utils.common.DebugUtils.getOriginalOut().flush();
    }

    public static synchronized void printAt(int x, int y, String text) {
        if (loaded) {
            try {
                printAt0(x, y, text);
                return;
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }
        // ANSI escape sequence to position cursor at y, x and print text
        hexacloud.core.utils.common.DebugUtils.getOriginalOut().print("\u001B[" + y + ";" + x + "H" + text);
        hexacloud.core.utils.common.DebugUtils.getOriginalOut().flush();
    }

    /**
     * Reads the next key code. Executes blocking JNI poll on a dedicated Platform Thread if called
     * from a Virtual Thread (Loom) to prevent carrier thread pinning.
     */
    public static int readKey() {
        if (isCurrentThreadVirtual()) {
            try {
                return PLATFORM_EXECUTOR.submit(NativeTerminal::readKeyInternal).get();
            } catch (Exception e) {
                return -1;
            }
        } else {
            return readKeyInternal();
        }
    }

    private static boolean isCurrentThreadVirtual() {
        try {
            java.lang.reflect.Method method = Thread.class.getMethod("isVirtual");
            return (Boolean) method.invoke(Thread.currentThread());
        } catch (Throwable t) {
            return false;
        }
    }

    private static synchronized int readKeyInternal() {
        if (loaded) {
            try {
                int val = readKey0();
                if (val == 2000) { // Window resize signal
                    forceUpdateTerminalSize();
                    return 2000;
                }
                if (val >= 0) {
                    return ANSI_PARSER.parseNextByte(val);
                } else {
                    return ANSI_PARSER.flushPendingEscape();
                }
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }

        // Fallback Java reading
        try {
            if (System.in.available() > 0) {
                int c = System.in.read();
                if (c >= 0) {
                    return ANSI_PARSER.parseNextByte(c);
                }
            } else {
                return ANSI_PARSER.flushPendingEscape();
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public static synchronized boolean saveConfig(String filepath, String content) {
        if (loaded) {
            try {
                return saveConfig0(filepath, content);
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }
        try (FileWriter fw = new FileWriter(filepath)) {
            fw.write(content);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int cachedWidth = 80;
    private static int cachedHeight = 24;
    private static long lastSizeCheck = 0;

    public static synchronized void forceUpdateTerminalSize() {
        lastSizeCheck = 0;
        updateTerminalSize();
    }

    public static synchronized int getTerminalWidth() {
        updateTerminalSize();
        return Math.max(20, cachedWidth);
    }

    public static synchronized int getTerminalHeight() {
        updateTerminalSize();
        return Math.max(5, cachedHeight);
    }

    private static void updateTerminalSize() {
        if (loaded) {
            try {
                int w = getTerminalWidth0();
                int h = getTerminalHeight0();
                if (w > 0 && h > 0) {
                    cachedWidth = w;
                    cachedHeight = h;
                    return;
                }
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastSizeCheck < 1000) {
            return;
        }
        lastSizeCheck = now;

        // Try environment variables COLUMNS and LINES
        String cols = System.getenv("COLUMNS");
        String lines = System.getenv("LINES");
        if (cols != null && lines != null) {
            try {
                int w = Integer.parseInt(cols.trim());
                int h = Integer.parseInt(lines.trim());
                if (w > 0 && h > 0) {
                    cachedWidth = w;
                    cachedHeight = h;
                    return;
                }
            } catch (Exception ignored) {}
        }
    }
}