# Design Specification: Hierarchical TUI Redesign

**Author:** Antigravity AI & GateBridge Team  
**Date:** 2026-09-18  
**Status:** Approved  
**Branch:** `master`  

---

## 1. Executive Summary

The current Terminal User Interface (TUI) in GateBridge presents `CLUSTERS` and `GATEWAYS` as side-by-side, independent peer panels. Conceptually, however, a **Gateway** acts as the front-door L7 router and management listener, while a **Cluster** represents the logical target pool of backend application nodes (`ServerNode`).

This specification defines the redesign of the TUI Dashboard into a **Unified Hierarchical Tree View**, where Gateways act as parent nodes displaying their listeners (Data Plane & Management Plane), ingress routes, target clusters, and nested backend server nodes.

---

## 2. Component Breakdown & Data Models

### 2.1 Unified Tree Structure (`TuiNodeTree` / `TuiState`)

The updated `TuiState` aggregates gateway and cluster hierarchies into a single navigable tree:

```
▼ 🌐 GATEWAY: main-gw  [Data: :4001 ONLINE] [Mgmt: :9090 ONLINE]
  ├─ 🛣️ Ingress Route: /proxy/**
  │   └─ 📦 TARGET CLUSTER: backend-cluster (Mode: HYBRID)
  │       ├─ 🟢 Node 1: http://127.0.0.1:3001  [ONLINE]  (42ms) | CPU: 12% RAM: 80MB
  │       └─ 🔴 Node 2: http://127.0.0.1:3002  [OFFLINE] (0ms)  | CPU: 0%  RAM: 0MB
```

### 2.2 Dual-Listener Inline Status Badge
On each Gateway parent node, the header displays an inline status badge showing both listeners:
- `[Data: :<port> <STATUS>]`: Data Plane public proxy listener (e.g. `:4001 ONLINE`).
- `[Mgmt: :<adminPort> <STATUS>]`: Dedicated Management Plane listener (e.g. `:9090 ONLINE`).

### 2.3 Keyboard Controls & Tree Navigation
- `[Up Arrow / Down Arrow]`: Move selection up/down through the expanded tree items.
- `[Space]` / `[Enter]`: Toggle expansion/collapse of Gateway or Cluster sub-trees.
- `[G]`: Toggle Gateway Data Plane listener status (ONLINE/OFFLINE).
- `[N]`: Register/Add a new node to the active cluster.
- `[L]`: Switch to Full Logs view.
- `[Q]`: Exit TUI.

---

## 3. Modular Renderer Updates

1. **`DashboardViewRenderer.java`**:
   - Replaces side-by-side `CLUSTERS` and `GATEWAYS` split boxes with a single full-width `GATEWAYS & CLUSTERS HIERARCHY` tree panel.
   - Retains the bottom split panels for `RECENT SYSTEM LOGS`, `GATEWAYS & SYSTEM METRICS`, and `RECENT EVENTS`.

2. **`TuiKeyHandler.java`**:
   - Updates selection index logic (`selectedTreeIndex`) to handle hierarchical item traversal, expansion toggles, and scroll viewports.

---

## 4. Backward Compatibility

- No changes to core gateway routing or server lifecycle logic.
- Full compatibility with existing `TerminalUiFactory.createTui(...)` and `seedGateway(...)` entry points.
