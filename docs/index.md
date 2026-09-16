# GateBridge Documentation

## Overview

GateBridge is a small Java framework for cluster gateway management and terminal-based telemetry monitoring.

It is designed to let users compose cluster gateways and monitor node state without exposing low-level terminal rendering details.

For an in-depth breakdown of system internals, Loom concurrency, request lifecycles, and contributor engineering standards, see the **[Core Architecture Guide](architecture.md)**.

## Documentation links

- [Core Architecture Guide](architecture.md) — comprehensive architecture guide covering system design, hexagonal ports-and-adapters, Loom concurrency, request/connection lifecycles, DevOps TUI, and multi-JDK build engine.
- [Gateway Usage](gateway.md) — how to configure a gateway, register servers, and start the local control plane.
- [Terminal UI](terminal-ui.md) — how `TerminalUI` works, its screens, and how to run the monitor.
- [Concurrency & Threads](thread-manager.md) — how the Loom-based `ThreadManager` schedules lightweight virtual tasks.
- [Framework Extensibility](framework-extensibility.md) — how to programmatically configure security, rates, and register route controllers.
- [Custom Events](events.md) — how to define and dispatch custom events, how to register event listener controllers, and built-in event behavior.
- [Examples](examples.md) — practical bootstrap and event wiring patterns for GateBridge.
- [Logging System](logging.md) — how to integrate logging adapters (like Log4j2), log levels, and TUI redirection.
