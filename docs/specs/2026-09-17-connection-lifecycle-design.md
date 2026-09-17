# Connection Lifecycle Abstraction Design

## Overview
Issue #4 (`[M0] Define connection lifecycle`) requires establishing a unified, transport-agnostic connection lifecycle abstraction across all GateBridge server transport listeners (HTTP, WebSocket, Telnet, and TCP Proxy).

This document specifies the architecture, interfaces, registration mechanisms, and test strategies for the `hexacloud.core.server.connection` package.

---

## 1. Domain Model & Interfaces

### 1.1 `ConnectionContext` (`hexacloud.core.server.connection.ConnectionContext`)
An immutable interface representing an active client network connection:
```java
package hexacloud.core.server.connection;

public interface ConnectionContext {
    String getConnectionId();
    String getProtocol();
    String getRemoteAddress();
    long getConnectedAtMs();
    long getLastActiveMs();
    boolean isAlive();
    void touch();
    void close();
}
```

### 1.2 `ConnectionLifecycleListener` (`hexacloud.core.server.connection.ConnectionLifecycleListener`)
An event listener interface for transport connection lifecycle notifications:
```java
package hexacloud.core.server.connection;

public interface ConnectionLifecycleListener {
    void onConnect(ConnectionContext context);
    void onHeartbeat(ConnectionContext context);
    void onDisconnect(ConnectionContext context);
    void onError(ConnectionContext context, Throwable cause);
}
```

### 1.3 `ConnectionRegistry` (`hexacloud.core.server.connection.ConnectionRegistry`)
A thread-safe central registry managing active connections and executing idle reclamation:
- Maintains active `ConcurrentHashMap<String, ConnectionContext>` map.
- Fires listener callbacks for `onConnect`, `onHeartbeat`, `onDisconnect`, and `onError`.
- Reclaims connections exceeding configured idle timeouts (`reclaimIdleConnections(long timeoutMs)`).

---

## 2. Transport Integrations

1. **`ServerManager` Integration**:
   - Holds shared `ConnectionRegistry` instance.
   - Passes `ConnectionRegistry` to server transport listeners.
2. **Transport Adapters (`hexacloud.infra.server`)**:
   - `TcpProxyTransport`: Registers client and node sockets on connect/disconnect.
   - `TelnetTransport`: Registers terminal session sockets on accept and unregisters on disconnect.
   - `WsTransport`: Registers WebSocket session channels.
   - `UndertowHttpTransport`: Registers HTTP exchange connections.

---

## 3. Verification & Testing
- Unit tests for `ConnectionContext` and `ConnectionRegistry` in `java/test/hexacloud/core/server/connection/`.
- Integration tests verifying connection lifecycle callbacks under active traffic, idle timeout, and abrupt closes.
- Updated documentation in `docs/architecture.md`.
