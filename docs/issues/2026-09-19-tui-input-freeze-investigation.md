# Issue Investigation: DevOps TUI Input Reader Lockup / Freeze

**Date:** 2026-09-19  
**Status:** Resolved  
**Component:** `c/hexaterminal.c`, `TerminalUI.java`

---

## 1. Symptoms
- After several rapid keypresses in the DevOps TUI (or when running inside Kitty/integrated IDE terminal tabs), key input would periodically freeze and stop responding to keyboard navigation (`[Up]`, `[Down]`, `[Enter]`).
- The TUI stayed rendered on screen, but further keypresses produced no actions or state updates.

---

## 2. Root Cause Analysis

### Root Cause 1: High-Frequency File Descriptor Flag Toggling (`fcntl`)
- In `c/hexaterminal.c`, `readKey0` was calling `fcntl(in_fd, F_SETFL, flags | O_NONBLOCK)` and restoring `fcntl(in_fd, F_SETFL, flags)` on **every single poll** (20 times per second / 50ms interval).
- Continually modifying file descriptor status flags on `/dev/tty` or `STDIN` while reading characters can trigger race conditions between Linux TTY line discipline drivers and userland non-blocking reads, causing `read()` calls to freeze or block indefinitely on certain kernel/TTY buffer states.

### Root Cause 2: Unhandled Virtual Thread Exception Failure
- `TuiInputReader` inside `TerminalUI.java` executed inside a Java virtual thread (`ThreadManager.startVirtual("TuiInputReader", ...)`).
- If an unhandled exception occurred during native JNI execution or state handling, the virtual thread would terminate silently, stopping all input processing while the redraw loop remained alive.

---

## 3. Resolution

1. **Persistent `O_NONBLOCK` Initialization**:
   - Updated `c/hexaterminal.c` (`Java_hexacloud_core_utils_terminal_NativeTerminal_initTerminal0`) to query initial `fcntl` flags (`orig_in_flags`) and set `O_NONBLOCK` **once** when entering raw mode.
   - Restored original `fcntl` flags upon exiting raw mode in `resetTerminal0`.
   - Removed all redundant `fcntl` system calls from `readKey0`.

2. **JNI Library Re-compilation & Synchronization**:
   - Recompiled `libhexaterminal.so` with GCC 16 and synchronized copies in `java/resources/native/libhexaterminal.so` and `java/libhexaterminal.so`.

3. **Virtual Thread Exception Guard**:
   - Wrapped the key read loop body in `TerminalUI.java` (`startInputReader`) with a `try-catch (Throwable)` guard so input polling continues uninterrupted even if unexpected exceptions occur.

---

## 4. Verification
- Compiled and executed full test suite (`mvn clean test`): **181 tests passed, 0 failures**.
- Tested rapid input navigation and terminal focus sequences in Kitty and IDE integrated terminals.
