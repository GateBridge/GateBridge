# Connection Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement unified connection lifecycle abstraction (`ConnectionContext`, `ConnectionLifecycleListener`, `ConnectionRegistry`) and integrate across transport listeners in `GateBridge/GateBridge`.

**Architecture:** Create package `hexacloud.core.server.connection` containing `ConnectionContext`, `ConnectionLifecycleListener`, and `ConnectionRegistry`. Integrate connection tracking into `ServerManager` and transport implementations in `hexacloud.infra.server`.

**Tech Stack:** Java 21, JUnit 5, ConcurrentHashMap, Project Loom Virtual Threads.

## Global Constraints
- Target Package: `hexacloud.core.server.connection`
- Transport Package: `hexacloud.infra.server`
- Zero Magic Numbers: Default timeouts from `ClusterConfig`.

---

### Task 1: Create Connection Lifecycle Abstractions & Unit Tests

**Files:**
- Create: `java/src/hexacloud/core/server/connection/ConnectionContext.java`
- Create: `java/src/hexacloud/core/server/connection/ConnectionContextImpl.java`
- Create: `java/src/hexacloud/core/server/connection/ConnectionLifecycleListener.java`
- Create: `java/src/hexacloud/core/server/connection/ConnectionRegistry.java`
- Create: `java/test/hexacloud/core/server/connection/ConnectionRegistryTest.java`

**Interfaces:**
- Consumes: `hexacloud.core.utils.common.DebugUtils`
- Produces: `ConnectionRegistry` for connection management across transports

- [ ] **Step 1: Write `ConnectionContext.java` interface**

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

- [ ] **Step 2: Write `ConnectionContextImpl.java` implementation**

Implement `ConnectionContext` backing fields and socket/close runnable callbacks.

- [ ] **Step 3: Write `ConnectionLifecycleListener.java` interface**

```java
package hexacloud.core.server.connection;

public interface ConnectionLifecycleListener {
    void onConnect(ConnectionContext context);
    void onHeartbeat(ConnectionContext context);
    void onDisconnect(ConnectionContext context);
    void onError(ConnectionContext context, Throwable cause);
}
```

- [ ] **Step 4: Write `ConnectionRegistry.java` class**

Implement thread-safe connection registry with callback dispatch and idle timeout reclamation.

- [ ] **Step 5: Write unit tests in `ConnectionRegistryTest.java`**

Implement unit tests verifying:
1. `onConnect` registration.
2. `onHeartbeat` touch updates.
3. `onDisconnect` removal and listener notification.
4. `reclaimIdleConnections` timeout cleanup.

- [ ] **Step 6: Run tests and verify PASS**

Run: `mvn test -Dtest=ConnectionRegistryTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit changes**

```bash
git add java/src/hexacloud/core/server/connection/ java/test/hexacloud/core/server/connection/
git commit -m "feat(server): implement connection lifecycle abstractions and registry"
```

---

### Task 2: Integrate `ConnectionRegistry` into `ServerManager` and Transports

**Files:**
- Modify: `java/src/hexacloud/core/server/ServerManager.java`
- Modify: `java/src/hexacloud/infra/server/TcpProxyTransport.java`
- Modify: `java/src/hexacloud/infra/server/TelnetTransport.java`
- Create: `java/test/hexacloud/core/server/connection/ConnectionLifecycleIntegrationTest.java`

- [ ] **Step 1: Update `ServerManager.java` to expose `ConnectionRegistry`**

Add `ConnectionRegistry` instance to `ServerManager` and pass to active transports.

- [ ] **Step 2: Update `TcpProxyTransport.java` with connection lifecycle calls**

Register `ConnectionContext` on client accept and fire `onDisconnect` on close.

- [ ] **Step 3: Update `TelnetTransport.java` with connection lifecycle calls**

Register `ConnectionContext` on telnet session accept and fire `onDisconnect` on session close.

- [ ] **Step 4: Write integration test in `ConnectionLifecycleIntegrationTest.java`**

Verify end-to-end lifecycle callbacks when connecting via socket shims.

- [ ] **Step 5: Run tests and verify PASS**

Run: `mvn test -Dtest=ConnectionLifecycleIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit changes**

```bash
git add java/src/ java/test/
git commit -m "feat(server): integrate connection lifecycle tracking across transport listeners"
```

---

### Task 3: Documentation & Issue Resolution

**Files:**
- Modify: `docs/architecture.md`

- [ ] **Step 1: Update `docs/architecture.md`**

Add section on `hexacloud.core.server.connection` package and lifecycle sequence.

- [ ] **Step 2: Run full build and test suite**

Run: `mvn clean verify`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit changes**

```bash
git add docs/architecture.md
git commit -m "docs(architecture): document connection lifecycle subsystem"
```
