package hexacloud.core.utils.terminal;

import java.util.Scanner;

public class TerminalScanner {

    private TerminalScanner() {}

    /**
     * Reads a full line of text from standard input safely without pre-buffering.
     * 
     * @return the trimmed input line, or an empty string if EOF.
     */
    public static synchronized String readLine() {
        try {
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = System.in.read()) != -1) {
                if (b == '\n') {
                    break;
                }
                if (b == '\r') {
                    // Check if \n follows
                    if (System.in.available() > 0) {
                        System.in.mark(1);
                        int next = System.in.read();
                        if (next != '\n') {
                            System.in.reset();
                        }
                    }
                    break;
                }
                sb.append((char) b);
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Reads the next word/token from standard input safely.
     * 
     * @return the trimmed token, or an empty string if EOF.
     */
    public static synchronized String readToken() {
        String line = readLine();
        if (!line.isEmpty()) {
            String[] parts = line.split("\\s+");
            return parts[0].trim();
        }
        return "";
    }
}
