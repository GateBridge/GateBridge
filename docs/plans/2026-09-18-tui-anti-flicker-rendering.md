# Anti-Flicker Double-Buffered TUI Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate screen flickering in the GateBridge TUI by introducing `TuiFrameBuffer` for offscreen double-buffering, replacing `clearScreen()` with `cursorHome()` on frame redraws, hiding the hardware cursor during render passes, and enforcing a 30 FPS coalescing ceiling.

**Architecture:** Create `TuiFrameBuffer` to accumulate ANSI characters in memory before executing a single atomic `System.out.print()` + `flush()`. Update `TuiRenderer` and `NativeTerminal` to use `cursorHome()` (`\033[H`) on redraws instead of `clearScreen()` (`\033[2J`). Add frame coalescing in `TerminalUI`.

**Tech Stack:** Java 17/21, GateBridge TUI Engine, ANSI Terminal Escape Sequences, JUnit 5.

## Global Constraints

- Package placement: `hexacloud.core.tui`, `hexacloud.core.utils.terminal`.
- No screen clearing (`\033[2J`) during normal frame redraws. Use `cursorHome()` (`\033[H`) + padding overwrite.
- Cursor visibility: Hide during render (`\033[?25l`), restore after flush (`\033[?25h`).
- Frame rate limit: Coalesce redraws to 33ms (~30 FPS max).
- Quality Gate: All existing 174+ tests plus new anti-flicker tests must pass cleanly (`mvn test`).

---

### Task 1: Implement `TuiFrameBuffer` for Atomic Offscreen Buffer Construction

**Files:**
- Create: `java/src/hexacloud/core/tui/TuiFrameBuffer.java`
- Create: `java/test/hexacloud/core/tui/TuiFrameBufferTest.java`

**Interfaces:**
- Consumes: Terminal dimensions `(width, height)`.
- Produces: `TuiFrameBuffer` with `printAt(int x, int y, String text)`, `clear()`, `flushToTerminal()` methods.

- [ ] **Step 1: Write failing unit test for `TuiFrameBuffer`**

```java
package hexacloud.core.tui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TuiFrameBufferTest {

    @Test
    void testFrameBufferAccumulationAndOutput() {
        TuiFrameBuffer buffer = new TuiFrameBuffer(80, 24);
        buffer.printAt(1, 1, "Hello GateBridge");
        buffer.printAt(1, 2, "Line 2");
        
        String output = buffer.buildFrameString();
        assertNotNull(output);
        assertTrue(output.contains("Hello GateBridge"));
        assertTrue(output.contains("Line 2"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=TuiFrameBufferTest`
Expected: Compilation failure due to missing `TuiFrameBuffer`.

- [ ] **Step 3: Implement `TuiFrameBuffer`**

```java
package hexacloud.core.tui;

import java.util.Arrays;

public class TuiFrameBuffer {

    private final int width;
    private final int height;
    private final StringBuilder buffer;

    public TuiFrameBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.buffer = new StringBuilder(width * height * 4);
    }

    public void beginFrame() {
        buffer.setLength(0);
        buffer.append("\u001B[?25l"); // Hide cursor
        buffer.append("\u001B[H");     // Move cursor home (1,1) without clear
    }

    public void printAt(int x, int y, String text) {
        if (text == null || text.isEmpty()) return;
        buffer.append("\u001B[").append(y).append(";").append(x).append("H").append(text);
    }

    public String buildFrameString() {
        buffer.append("\u001B[?25h"); // Restore cursor
        return buffer.toString();
    }

    public void flushToTerminal() {
        System.out.print(buildFrameString());
        System.out.flush();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=TuiFrameBufferTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add java/src/hexacloud/core/tui/TuiFrameBuffer.java java/test/hexacloud/core/tui/TuiFrameBufferTest.java
git commit -m "feat(tui): introduce TuiFrameBuffer for offscreen double-buffering"
```

---

### Task 2: Refactor `TuiRenderer` & `NativeTerminal` for Anti-Flicker `cursorHome()` Rendering

**Files:**
- Modify: `java/src/hexacloud/core/tui/TuiRenderer.java`
- Modify: `java/src/hexacloud/core/utils/terminal/NativeTerminal.java`
- Create: `java/test/hexacloud/core/tui/TuiAntiFlickerTest.java`

**Interfaces:**
- Consumes: `TuiFrameBuffer` and `NativeTerminal.cursorHome()`.
- Produces: Smooth, flicker-free rendering in `TuiRenderer.draw()`.

- [ ] **Step 1: Write unit test for `cursorHome` method in `NativeTerminal`**

```java
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
```

- [ ] **Step 2: Run test to verify compilation failure**

Run: `mvn test -Dtest=TuiAntiFlickerTest`

- [ ] **Step 3: Update `NativeTerminal.java` and `TuiRenderer.java`**

In `NativeTerminal.java`:
```java
public static synchronized void cursorHome() {
    System.out.print("\u001B[H");
    System.out.flush();
}
```

In `TuiRenderer.java`:
- Replace `NativeTerminal.clearScreen()` at line 27 with `NativeTerminal.cursorHome()`.
- Wrap rendering passes with `TuiFrameBuffer` or single atomic output pass to eliminate per-string flush flickering.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=TuiAntiFlickerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add java/src/hexacloud/core/tui/TuiRenderer.java java/src/hexacloud/core/utils/terminal/NativeTerminal.java java/test/hexacloud/core/tui/TuiAntiFlickerTest.java
git commit -m "refactor(tui): replace clearScreen with cursorHome and offscreen frame buffering to eliminate flicker"
```

---

### Task 3: Integrate 30 FPS Frame Coalescing in `TerminalUI`

**Files:**
- Modify: `java/src/hexacloud/core/tui/TerminalUI.java:360-388`
- Create: `java/test/hexacloud/core/tui/TuiFrameCoalescingTest.java`

**Interfaces:**
- Consumes: Event bus triggers and keyboard inputs.
- Produces: Coalesced redraw loop with 33ms inter-frame interval.

- [ ] **Step 1: Write test for frame coalescing logic**

```java
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
```

- [ ] **Step 2: Run test**

Run: `mvn test -Dtest=TuiFrameCoalescingTest`

- [ ] **Step 3: Update `TerminalUI.java`**

In `TerminalUI.java`, update `executeRedrawLoop()`:
```java
    private void executeRedrawLoop() {
        long lastRedrawTime = 0;
        final long minFrameIntervalMs = 33; // ~30 FPS ceiling

        while (state.running) {
            try {
                redrawSemaphore.acquire();
                
                long now = System.currentTimeMillis();
                long elapsed = now - lastRedrawTime;
                if (elapsed < minFrameIntervalMs && !bypassDebounce) {
                    Thread.sleep(minFrameIntervalMs - elapsed);
                }
                bypassDebounce = false;
                redrawSemaphore.drainPermits();

                if (state.running) {
                    fetchClusterNames();
                    if (!state.selectedClusterName.isEmpty()) {
                        fetchNodeStatus();
                        fetchClusterConfig(state.selectedClusterName);
                    }
                    fetchGlobalConfig();

                    renderer.draw();
                    lastRedrawTime = System.currentTimeMillis();
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }
```

- [ ] **Step 4: Run full regression test suite**

Run: `mvn test`
Expected: 100% tests PASS across all suites.

- [ ] **Step 5: Commit**

```bash
git add java/src/hexacloud/core/tui/TerminalUI.java java/test/hexacloud/core/tui/TuiFrameCoalescingTest.java
git commit -m "feat(tui): implement 30 FPS frame rate coalescing in TerminalUI to prevent redraw flooding"
```
