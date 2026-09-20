# Terminal UI

The GateBridge Terminal UI is a modular, high-telemetry terminal console located in `java/src/hexacloud/core/tui/`.

## Architecture & Separation of Concerns

The TUI subsystem isolates operational responsibilities across specialized components:

1. **`TerminalUI.java`** — The coordinator that orchestrates background data synchronization, periodic UI refreshes, and shutdown hooks.
2. **`TuiState.java`** — Encapsulates UI navigation, active viewports, cursor indices, and cached cluster/node telemetry.
3. **`TuiRenderer.java`** — Responsible for drawing boxes, borders, styling logs, and rendering panels based on immutable state snapshots.
4. **`TuiKeyHandler.java`** — Intercepts key presses and converts them into navigation or editing events.
5. **`TuiPrompts.java`** — Manages interactive dialogues (like creating a cluster or updating node parameters) by temporarily suspending raw terminal mode.
6. **`TuiConstants.java`** — Centralizes ANSI escape color codes, view numbers, and panel focus IDs.
7. **`TuiEventLoop.java`** — Single-threaded event reactor for processing UI events in sequence, enforcing single-threaded state mutations.
8. **`UIEvent.java`** — Event interfaces and records (`KeyPressEvent`, `ResizeEvent`, `RedrawEvent`, `ActionEvent`, `ShutdownEvent`).
9. **`NodeView.java`** — Immutable snapshot representation of a `ServerNode` for TUI rendering to prevent partial render reads and race conditions.
10. **`AnsiEscapeParser.java`** — Universal stateful Java parser mapping raw escape byte streams and ANSI sequences to key codes.
11. **`TuiFrameBuffer.java`** — Double-buffering canvas that builds ANSI frame buffers and flushes them atomically to minimize flicker.

## Configuration & Feature Flags (`TerminalUiPort`)

Clients interact with the TUI using the `TerminalUiPort` interface obtained from `TerminalUiFactory`. The TUI permissions can be restricted using a fluent builder API:

```java
TerminalUiFactory.createTui("DevOps Control Plane")
    .readOnly(false)                     // Set to true to disable all write operations
    .gatewayManagementEnabled(true)     // Enable [G] key to start/stop server listeners
    .clusterManagementEnabled(true)     // Enable [C] key to create clusters
    .nodeManagementEnabled(true)        // Enable [A]/[D] to register/deregister nodes
    .nodeConfigurationEnabled(true)     // Enable [Enter] config of ping routes & headers
    .redirectSystemOut(false)           // If true, redirects System.out/System.err to TUI log panel
    .seedGateway(hexacloud)             // Inject already started gateway instance
    .startToggleMode();                 // Start in non-blocking toggle/detachable mode
```

*   **`readOnly(boolean)`** — Restricts write actions inside the console.
*   **`gatewayManagementEnabled(boolean)`** — Enables manually starting/stopping gateway listeners.
*   **`clusterManagementEnabled(boolean)`** — Enables cluster creation.
*   **`nodeManagementEnabled(boolean)`** — Enables registering/deregistering service nodes.
*   **`nodeConfigurationEnabled(boolean)`** — Enables updating route/ping header settings.
*   **`redirectSystemOut(boolean)`** — Sets whether standard output (`System.out` and `System.err`) is hijacked and redirected to the dashboard's log pane (defaults to `false` for embedded library usage, `true` for standalone executables).
*   **`seedGateway(RunningGatewayPort)`** — Seeds the console with a pre-configured gateway.

## Non-Blocking Detachable Mode (`startToggleMode`)

When calling `startToggleMode()`, the application runs the gateways in the background and prints standard framework logs to standard output. 
*   **Attach:** Pressing `ENTER` (or key `M`) at any time clears the screen, activates the Alternate Screen Buffer (`\e[?1049h`), and opens the interactive TUI.
*   **Detach:** Pressing `Q` or `ESC` inside the TUI detaches the panel, exits the Alternate Screen Buffer (`\e[?1049l`), resets the terminal to canonical mode, and safely resumes printing background logs to standard output without stopping active gateways or killing the JVM process.

## Alternate Screen Buffer & Clean Lifecycle

The TUI uses the ANSI **Alternate Screen Buffer** (`\e[?1049h` / `\e[?1049l`) during startup and teardown:
- Upon initialization, `NativeTerminal.initTerminal()` emits `\033[?1049h` to switch to an isolated terminal buffer, hiding the cursor (`\033[?25l`) and clearing the screen.
- Upon shutdown or detachment, `NativeTerminal.resetTerminal()` emits `\033[?1049l` to restore the primary terminal screen and cursor (`\033[?25h`), preserving the user's terminal scrollback buffer intact.

## Immutable State Snapshots (`NodeView`)

To prevent partial render reads and race conditions when background threads (such as `ThreadPingScheduler` or network listeners) update node state during a draw cycle, rendering components operate exclusively on `NodeView` immutable state snapshots. Calling `NodeView.from(serverNode)` captures a point-in-time snapshot of all status, latency, CPU/RAM telemetry, and ping configuration attributes.

## UI Interaction & Shortcuts

### Dashboard View (`VIEW_DASHBOARD`)
- `TAB` — Switch focus between the left panel (Clusters) and the right panel (Services).
- `UP` / `DOWN` — Navigate the selected list (clusters list or nodes list).
- `ENTER` — Open the Cluster Console Detail View for the selected cluster.
- `[G]` — Open Gateway Setup to start/stop the gateway for the active cluster.
- `[C]` — Create a new cluster.
- `[L]` — Open full, scrollable system logs panel.
- `[Q]` / `ESC` — Terminate TUI and close the application.

### Cluster Detail View (`VIEW_CLUSTER_DETAIL`)
- `UP` / `DOWN` — Navigate registered service nodes.
- `ENTER` — Open the Node Config Panel for the selected service node.
- `[G]` — Open Gateway Setup to start/stop the gateway.
- `[A]` — Register a new node (specifies port).
- `[D]` — Deregister/delete the selected node.
- `[I]` — Configure Allowed Client IPs (comma-separated).
- `[T]` — Edit response Timeout in milliseconds.
- `[L]` — Edit Rate Limit (format: `<requests> <durationSeconds>`).
- `BACKSPACE` / `ESC` — Return to the Dashboard View.

### Node Config Panel (`VIEW_NODE_CONFIG`)
- `[P]` — Toggle ping health check monitoring (Enabled/Disabled).
- `[E]` — Change ping endpoint route (e.g. `/health`).
- `[H]` — Edit authentication header name (e.g. `X-API-Key`).
- `[V]` — Edit token value.
- `BACKSPACE` / `ESC` — Return to Cluster Detail View.

### Full Logs View (`VIEW_FULL_LOGS`)
- `UP` / `DOWN` — Scroll through detailed logs history.
- `BACKSPACE` / `ESC` — Return to Dashboard View.

## Automatic Persistence

Any cluster parameter updates, node additions/deletions, or non-sensitive configuration modifications made in the TUI are automatically saved in the background to the `java/resources/hexacloud-state.properties` configuration file. This allows restarting the application with a clean one-line loader (`OnlyTerminalMain.java`) while preserving operational customizations.

Secrets are intentionally not persisted. Cluster API tokens and ping header values must be provided again at runtime through code, environment configuration, or the TUI.

## Dependencies & Architecture

The Terminal UI relies on:
- `NativeTerminal` for native screen manipulation, `SIGWINCH` self-pipe handling, and JNI-level keyboard polling.
- `AnsiEscapeParser` for stateful decoding of ANSI escape sequences across all platforms.
- `TuiEventLoop` single-threaded event reactor for deterministic event queue processing.
- `TuiFrameBuffer` double-buffered canvas for flicker-free rendering.
- `TerminalScanner` for clean console line reads without interrupting native settings.
- `DebugUtils` for dispatching logging statements displayed on the dashboard.

## JNI Native Terminal & Platform Portability

The GateBridge TUI raw key interception, window resize detection, and screen rendering run on three compatibility levels to maximize hardware and OS support:

1.  **Level 1: Native JNI Mode (Best Performance)**: Uses a thin compiled C library (`libhexaterminal`) calling native OS API hooks (`ioctl` on Linux/macOS, `GetConsoleScreenBufferInfo` on Windows). Features a **`SIGWINCH` self-pipe** mechanism that captures terminal window resize signals asynchronously and writes a signal byte (2000) to the input pipe, triggering instant `ResizeEvent` redraws.
2.  **Level 2: Pure Java `stty` Fallback (Unix)**: If JNI is not loaded on a Linux or macOS machine, the system falls back to spawning raw mode process controllers (`stty raw -echo < /dev/tty`). Combined with universal `AnsiEscapeParser`, arrow keys and single keystrokes work 100% natively without compilation.
3.  **Level 3: ANSI Emulation Fallback (Windows/CI)**: If JNI is not loaded on Windows, it defaults to basic ANSI emulation controls and `AnsiEscapeParser` stream parsing.

### Specifying Custom JNI Paths

If you are not on Linux (e.g., macOS or Windows) and want to use Native JNI Mode, you will need to compile the native library from GitHub sources (`c/hexaterminal.c`) and direct the framework to load it using one of three options:

*   **JVM Argument**: Launch your application with the `-Dgatebridge.jni.path` system property:
    ```bash
    java -Dgatebridge.jni.path=/path/to/libhexaterminal.dylib -jar app.jar
    ```
*   **Environment Variable**: Configure the `GATEBRIDGE_JNI_PATH` variable:
    ```bash
    export GATEBRIDGE_JNI_PATH=/path/to/libhexaterminal.dylib
    ```
*   **Programmatic API**: Call the JNI loader hook before initializing the `TerminalUI`:
    ```java
    NativeTerminal.loadJni("/path/to/libhexaterminal.dylib");
    ```

### Compilation Guide

To compile the C source file (`hexaterminal.c`) for your specific OS and hardware architecture:

*   **macOS (Apple Silicon or Intel)**:
    ```bash
    gcc -shared -fPIC -o libhexaterminal.dylib c/hexaterminal.c -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin"
    ```
*   **Linux (ARM64/Raspberry Pi or x86)**:
    ```bash
    gcc -shared -fPIC -o libhexaterminal.so c/hexaterminal.c -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux"
    ```
*   **Windows (MSVC or Mingw)**:
    ```bash
    gcc -shared -o hexaterminal.dll c/hexaterminal.c -I"%JAVA_HOME%\include" -I"%JAVA_HOME%\include\win32"
    ```


