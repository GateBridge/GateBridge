# Design Specification: Anti-Flicker Double-Buffered TUI Rendering

**Author:** Antigravity AI & GateBridge Team  
**Date:** 2026-09-18  
**Status:** Approved  
**Branch:** `master`  

---

## 1. Executive Summary

The current GateBridge Terminal UI (TUI) exhibits severe screen flickering during navigation (`[Up / Down]` arrow keys) and rapid log/event updates. This flickering is caused by invoking `NativeTerminal.clearScreen()` (`\033[2J\033[3J`) on every redraw cycle, which completely erases the terminal scrollback buffer before re-rendering UI boxes.

This specification defines the **Anti-Flicker Double-Buffered TUI Rendering Architecture**, eliminating screen clear sequences during frame redraws, introducing offscreen string buffering with atomic buffer flushes, hiding the hardware cursor during render passes, and enforcing frame coalescing at 30 FPS.

---

## 2. Technical Architecture & Rendering Strategy

### 2.1 Atomic Offscreen Double-Buffering (`TuiFrameBuffer`)

Instead of making dozens of individual `NativeTerminal.printAt()` calls that output immediately to stdout, all drawing operations write into an in-memory `TuiFrameBuffer`:

1. **Offscreen Canvas:** A character matrix matching terminal dimensions `(width, height)`.
2. **Atomic Write & Flush:** Upon completing frame rendering, the buffer converts to a single string containing position jumps (`\033[y;xH`) and ANSI styles, executing a single `System.out.print(frameString)` and `System.out.flush()`.

### 2.2 Eliminating `clearScreen()` in Redraw Loop

- **Legacy Behavior:** `clearScreen()` called on every frame $\rightarrow$ screen erased to background color $\rightarrow$ visual flickering.
- **New Behavior:**
  - `clearScreen()` is invoked **only** on TUI startup or terminal resize (`SIGWINCH` / viewport change).
  - During standard frame redraws, `NativeTerminal.cursorHome()` (`\033[H`) moves the cursor to (1,1) without erasing pixels. The new frame string directly overwrites old characters.

### 2.3 Hardware Cursor Management (`\033[?25l` / `\033[?25h`)

- Before writing the frame buffer: Output `\033[?25l` (Hide hardware cursor).
- After completing the frame flush: Output `\033[?25h` (Show hardware cursor) or keep hidden until text prompt input.

### 2.4 Frame Rate Coalescing (30 FPS Ceiling)

In `TerminalUI.java`:
- Enforce a minimum inter-frame sleep interval of **33ms** (~30 FPS max redraw frequency).
- Rapid bursts of system logs or event notifications are coalesced into a single atomic frame render pass.

---

## 3. Verification & Quality Assurance

1. **Unit & Integration Tests:**
   - Verify `TuiFrameBufferTest` produces exact character matrices and ANSI string outputs.
   - Verify `TuiAntiFlickerTest` confirms `clearScreen()` is not called during normal frame redraws.
2. **Regression Testing:**
   - Run complete test suite (`mvn test`) ensuring 100% test pass rate across all 174+ existing tests.
