# Event-Driven Terminal UI Architecture

The GateBridge Terminal UI operates on a fully **event-driven reactor model** to ensure instant screen updates, zero idle CPU consumption, and unified event propagation across local domains and network adapters.

---

## 1. Single-Threaded Event Reactor (`TuiEventLoop`)

Instead of running on a periodic polling loop that consumes CPU cycles, the Terminal UI separates processing into an event-driven reactor architecture powered by `TuiEventLoop`:

1.  **Single-Threaded Event Reactor (`TuiEventLoop`):** A dedicated worker thread that dequeues and dispatches `UIEvent` objects (`KeyPressEvent`, `ResizeEvent`, `RedrawEvent`, `ActionEvent`, `ShutdownEvent`) sequentially. This guarantees single-threaded state mutations on `TuiState` without locks or concurrency contention.
2.  **Input Reader Worker:** A lightweight virtual thread (`TuiInputReader`) running a low-latency keyboard polling loop (`NativeTerminal.readKey()`). Keystrokes and escape streams are parsed by `AnsiEscapeParser` and posted as `KeyPressEvent` objects to the `TuiEventLoop` queue.
3.  **`SIGWINCH` Self-Pipe Signal:** Native C code (`hexaterminal.c`) intercepts operating system `SIGWINCH` window resize signals asynchronously via a self-pipe. It pushes signal byte `2000` to the Java input stream. `AnsiEscapeParser` decodes `2000` and immediately enqueues a `ResizeEvent` to recalculate terminal geometry.
4.  **Asynchronous Offloading (`submitAsync`):** Long-running I/O tasks or prompts are executed on background virtual threads via `TuiEventLoop.submitAsync(blockingTask, completionCallback)`, posting completion events back to the main event queue upon finishing.

---

## 2. Immutable State Snapshots (`NodeView`)

To prevent partial render reads, visual glitches, and race conditions when background telemetry workers (such as `ThreadPingScheduler`) update `ServerNode` attributes mid-render:

1.  **Point-in-Time Snapshots:** Renderers create immutable `NodeView` snapshots (`NodeView.from(serverNode)`) prior to drawing frame buffers.
2.  **Isolated Thread Safety:** Renderers read exclusively from `NodeView` instances, decoupling screen drawing from live concurrent node model mutations.

---

## 3. Universal `AnsiEscapeParser` & Alternate Screen Buffer

1.  **`AnsiEscapeParser`**: Stateful ANSI parser mapping raw byte streams, CSI parameters (e.g. `\033[A`, `\033[1;5H`), SS3 sequences, and signal codes (e.g. `2000` resize) to unified key identifiers.
2.  **Alternate Screen Buffer (`\e[?1049h` / `\e[?1049l`)**: On activation (`initTerminal`), the console switches to an isolated ANSI buffer (`\033[?1049h`) and hides the cursor (`\033[?25l`). On exit (`resetTerminal`), it restores standard terminal history (`\033[?1049l`) cleanly.

---

## 4. Event Propagation (Event Bubbling)

All domain events from local gateway adapters propagate up to a central event bus to trigger TUI updates:

```
[Cluster Local Event Bus] ----(Bubbles Up)----> [Global Event Bus] ----(Triggers UIEvent)----> [TuiEventLoop Redraw]
```

### 4.1. Central Event Bus
Inside `EventBusManager`, a static `GLOBAL` event bus is defined. All individual cluster event managers automatically bubble up their dispatched events to the global event bus.

### 4.2. Supported Events & Recents Feed
The `TerminalUI` subscribes to the following events on the global event bus:
- **`NodeStatusChanged`** — Fired when a node changes its connectivity status.
- **`NodeTelemetryUpdated`** — Fired when a node's CPU, RAM, or latency metrics are updated.
- **`NodeEventSubmitted`** — Fired when a service node submits a custom named event through the telemetry API.
- **`NodeRegistered`** — Fired when a new node is registered.
- **`NodeDeregistered`** — Fired when an existing node is removed.
- **`ClusterRegistered`** — Fired when a new cluster is created or registered.

In addition to these structural redraw triggers, a **Global Event Interceptor** is registered inside the TUI loop to intercept all custom and system events. Intercepted events are displayed in the **RECENT EVENTS** panel of the Dashboard with dynamic relative time tracking (e.g. `[15s] CustomEvent: myMessage`).

---

## 5. Coalescing & Debouncing Redraws (`TuiFrameBuffer`)

To prevent terminal screen flickering when multiple events fire in rapid succession (e.g., when multiple node statuses update simultaneously during startup or active pinging):

1.  Redraw requests post a `RedrawEvent` token to `TuiEventLoop`.
2.  `TuiFrameBuffer` double-buffers output, accumulating screen strings into a single string buffer.
3.  The frame is flushed atomically to standard output (`flushToTerminal()`) in a single I/O operation.

