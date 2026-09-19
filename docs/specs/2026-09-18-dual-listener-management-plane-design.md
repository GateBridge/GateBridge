# Design Specification: Dual-Listener Management Plane Architecture

**Author:** Antigravity AI & GateBridge Team  
**Date:** 2026-09-18  
**Status:** Draft / Proposed  
**Branch:** `feature/workflow-optimizations`  

---

## 1. Executive Summary

Currently, GateBridge processes both public proxy data traffic (*Data Plane*) and cluster administration requests (*Management Plane*, such as `/v1/register`, `/v1/telemetry`, `/v1/get_nodes_json`, `/v1/create_cluster`) on a single shared HTTP server socket and port. 

This monolithic listener design poses two major challenges:
1. **Security Vulnerability (Attack Surface Exposure):** Administrative and control-plane endpoints are co-located on public-facing data ports, relying entirely on route-level authorization to prevent unauthorized cluster reconfigurations.
2. **Performance Degradation on Data Plane Hotpath:** Every incoming proxy request on the high-throughput data path incurs conditional evaluations (`isLocal()`, `clusterRegistry.getRoutes()`) to check if the request matches local or cluster-level administrative endpoints.

This specification defines the **Dual-Listener Architecture**, physically segregating the **Data Plane** (public proxy routing) and **Management Plane** (administrative endpoints) into separate network listeners with distinct sockets, security policies, and execution pipelines.

---

## 2. Architecture & Design Requirements

```
                       +-------------------------------------------------+
                       |                GateBridge Gateway               |
                       |                                                 |
[Public Clients]  ---> | Data Plane Listener (Port 4001)                 |
                       |  - Ultra-Fast Proxy Forwarding                  |
                       |  - Zero Local Admin Routing Branching           | ---> [Upstream Clusters]
                       |  - Rejects /v1/* Admin Requests (404/403)       |
                       +-------------------------------------------------+

                       +-------------------------------------------------+
[Internal Admin/   ---> | Management Plane Listener (Port 9090)           |
 Cluster Nodes]        |  - Bound to 127.0.0.1 (Loopback / Private VPC)   |
                       |  - Handles ClusterController (/v1/*)            | ---> [RouteRegistry / State]
                       |  - Local Execution Pipeline Only                |
                       +-------------------------------------------------+
```

### 2.1 Component Breakdown

1. **Management Transport (`UndertowManagementTransport` / `ManagementServer`):**
   - Encapsulates an Undertow HTTP server bound strictly to an administrative host (default `127.0.0.1`) and port (default `9090`).
   - Houses the `RouteRegistry` and executes administrative route controllers (`ClusterController`, custom business controllers).
   - Operates independently from proxy forwarding logic, connection pools, and data plane rate-limiters.

2. **Data Plane Transport (`UndertowHttpTransport`):**
   - Dedicated solely to L7 reverse proxy forwarding.
   - Stripped of `isLocal()` and cluster-level administrative lookup loops inside `executeRoute()`.
   - Rejects administrative route patterns (e.g. `/v1/register`, `/v1/telemetry`) with `404 Not Found` or `403 Forbidden`.

3. **Orchestration & Configuration (`ServerManager`, `GatewayBuilderPort`, `LocalGatewayAdapter`):**
   - `ServerManager` manages the lifecycle (start, stop, health) of both `UndertowHttpTransport` and `UndertowManagementTransport`.
   - New configuration parameters:
     - `gatebridge.admin.enabled` (boolean, default `true`)
     - `gatebridge.admin.host` (String, default `127.0.0.1`)
     - `gatebridge.admin.port` (int, default `9090`)

---

## 3. Security & Performance Guarantees

### 3.1 Security Guarantees
- **Network Isolation:** Admin endpoints are inaccessible from external network interfaces when bound to `127.0.0.1` or isolated VPC subnets.
- **Data Plane Hardening:** Public clients cannot invoke cluster mutation endpoints (`/v1/create_cluster`, `/v1/set_allowed_ips`, `/v1/deregister`) even if authentication middleware is bypassed.

### 3.2 Performance Guarantees
- **Data Plane Hotpath Simplification:** `executeRoute()` in `UndertowHttpTransport` becomes a direct proxy pipeline execution without map lookups for local administrative handlers.
- **Reduced Lock Contention:** Administrative request processing does not contend for thread pool resources or buffer pools allocated to high-throughput proxy traffic.

---

## 4. Backward Compatibility

- System properties `gatebridge.admin.enabled` and `gatebridge.admin.port` allow operators to disable the management listener or rebind it to a custom port.
- In single-port testing environments, a fallback flag `gatebridge.admin.legacy.singleport=true` can temporarily re-enable legacy co-located routing for legacy test suites.

---

## 5. Verification & Testing Plan

1. **Unit Tests:**
   - Verify `UndertowManagementTransportTest` processes `/v1/get_nodes_json` and `/v1/telemetry` on port `9090`.
   - Verify `UndertowHttpTransportTest` returns `404` or `403` when requesting `/v1/get_nodes_json` on data port `4001`.
2. **Integration Tests:**
   - Verify cluster node registration (`ClusterController`) via management port `9090` correctly updates cluster state for proxy routing on port `4001`.
3. **Regression Tests:**
   - Run complete suite (`mvn test`) to ensure zero regressions across all 164 existing tests.
