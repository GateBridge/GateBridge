# Core Architecture Documentation Design

## Overview
This specification defines the comprehensive architecture guide document created inside `docs/architecture.md` for `GateBridge/GateBridge`. The goal is to provide a clear, professional technical breakdown for developers reading about the project or contributing to the codebase.

---

## 1. Document Location & Scope
- **File Location**: `docs/architecture.md`
- **Language**: Technical English
- **Target Audience**: Core developers, enterprise integrators, open-source contributors, and architecture auditors.

---

## 2. Document Content Breakdown

1. **System Overview & Engineering Philosophy**:
   - High-performance, lightweight Java cluster gateway.
   - Loom virtual threading model (`ThreadManager`) vs legacy thread pools or reactive callbacks.
   - Port-Adapter architecture (`hexacloud.core.ports`).

2. **Repository & Ecosystem Topology**:
   - `GateBridge/GateBridge` (Core Open Source): Public framework, multi-protocol transports, TUI dashboard, system routes `/V1/*`.
   - `GateBridge/gatebridge-enterprise` (Enterprise Extensions): Dynamic SSL reload (`MutableKeyManager`), cloud telemetry, private enterprise features.

3. **Layered Architecture & Mermaid Diagram**:
   - Client Connections -> Transport Layer (Undertow HTTP, WebSocket, Telnet, TCP Proxy) -> Command Dispatcher & Route Scanner -> Cluster Controllers -> Upstream Forwarders -> DevOps TUI Telemetry Console.

4. **Request & Connection Lifecycle**:
   - Step-by-step trace of request handling across transport boundaries.

5. **DevOps Terminal UI Subsystem (`hexacloud.core.tui`)**:
   - Real-time thread metrics (App vs Daemon OS threads), event feeds, System.out redirection, toggle mode.

6. **Multi-JDK Source Overlay Build System**:
   - `source-overlay-plugin` overlaying JDK 17 and JDK 8 source overlays onto JDK 21 Loom baseline.

7. **Contributor Architecture Rules**:
   - Zero magic numbers (`ClusterConfig`, `TuiConstants`).
   - Non-blocking Loom thread execution rules.
   - Clean port interface decoupling.

---

## 3. Verification Plan
1. Validate Markdown formatting, Mermaid diagram syntax, and file links.
2. Verify all package names and class references match existing codebase.
3. Commit and push to `dev` and promote to `master`.
