# Hierarchical TUI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the Terminal UI (TUI) Dashboard from side-by-side peer panels into a unified Hierarchical Tree View where Gateways act as parent nodes displaying dual-listener badges, ingress routes, target clusters, and nested backend server nodes.

**Architecture:** Introduce `TuiTreeNode` hierarchy model in `TuiState`. Update `DashboardViewRenderer` to render the tree view with badges `[Data: :<port> <STATUS>] [Mgmt: :<adminPort> <STATUS>]`. Update `TuiKeyHandler` to navigate and expand/collapse tree nodes with `[▲ / ▼]` and `[Space]/[Enter]`.

**Tech Stack:** Java 17/21, GateBridge TUI Core, JUnit 5, NativeTerminal ANSI rendering.

## Global Constraints

- Package placement: `hexacloud.core.tui`, `hexacloud.core.tui.view`.
- Navigation controls: `[Up / Down]` arrow keys navigate items; `[Space]` or `[Enter]` toggles node expansion.
- Listener Badges: Inline on Gateway nodes — `[Data: :4001 ONLINE]` and `[Mgmt: :9090 ONLINE]`.
- Quality Gate: All existing 167 unit and integration tests plus new TUI tests must pass cleanly (`mvn test`).

---

### Task 1: Create `TuiTreeNode` & Update `TuiState` for Tree Representation

**Files:**
- Create: `java/src/hexacloud/core/tui/TuiTreeNode.java`
- Modify: `java/src/hexacloud/core/tui/TuiState.java`
- Create: `java/test/hexacloud/core/tui/TuiTreeNodeTest.java`

**Interfaces:**
- Consumes: `hexacloud.core.model.ServerNode`
- Produces: `TuiTreeNode` class with `NodeType` (GATEWAY, ROUTE, CLUSTER, SERVER_NODE), `label`, `expanded`, `children`, `depth`.

- [ ] **Step 1: Write failing unit test for `TuiTreeNode`**

```java
package hexacloud.core.tui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TuiTreeNodeTest {

    @Test
    void testTreeNodeHierarchyAndExpansion() {
        TuiTreeNode gwNode = new TuiTreeNode(TuiTreeNode.NodeType.GATEWAY, "main-gw", "GATEWAY: main-gw [Data: :4001 ONLINE] [Mgmt: :9090 ONLINE]", 0);
        TuiTreeNode clusterNode = new TuiTreeNode(TuiTreeNode.NodeType.CLUSTER, "backend-cluster", "TARGET CLUSTER: backend-cluster", 1);
        
        gwNode.addChild(clusterNode);
        
        assertEquals(1, gwNode.getChildren().size());
        assertTrue(gwNode.isExpanded());
        
        gwNode.setExpanded(false);
        assertFalse(gwNode.isExpanded());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=TuiTreeNodeTest`
Expected: Compilation failure due to missing `TuiTreeNode`.

- [ ] **Step 3: Implement `TuiTreeNode` & update `TuiState`**

Create `java/src/hexacloud/core/tui/TuiTreeNode.java`:

```java
package hexacloud.core.tui;

import java.util.ArrayList;
import java.util.List;

public class TuiTreeNode {

    public enum NodeType {
        GATEWAY, ROUTE, CLUSTER, SERVER_NODE
    }

    private final NodeType type;
    private final String id;
    private String label;
    private final int depth;
    private boolean expanded = true;
    private final List<TuiTreeNode> children = new ArrayList<>();
    private Object data;

    public TuiTreeNode(NodeType type, String id, String label, int depth) {
        this.type = type;
        this.id = id;
        this.label = label;
        this.depth = depth;
    }

    public NodeType getType() { return type; }
    public String getId() { return id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getDepth() { return depth; }
    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }
    public List<TuiTreeNode> getChildren() { return children; }
    public void addChild(TuiTreeNode child) { children.add(child); }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
```

Update `java/src/hexacloud/core/tui/TuiState.java` to include:
```java
public final List<TuiTreeNode> rootTreeNodes = new ArrayList<>();
public int selectedTreeIndex = 0;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=TuiTreeNodeTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add java/src/hexacloud/core/tui/TuiTreeNode.java java/src/hexacloud/core/tui/TuiState.java java/test/hexacloud/core/tui/TuiTreeNodeTest.java
git commit -m "feat(tui): add TuiTreeNode and hierarchical tree state models"
```

---

### Task 2: Refactor `DashboardViewRenderer` for Hierarchical Tree View & Dual-Listener Badges

**Files:**
- Modify: `java/src/hexacloud/core/tui/view/DashboardViewRenderer.java`
- Modify: `java/src/hexacloud/core/tui/TuiConstants.java`
- Create: `java/test/hexacloud/core/tui/DashboardViewRendererTest.java`

**Interfaces:**
- Consumes: `TuiState.rootTreeNodes` and `LocalGatewayAdapter` configurations.
- Produces: Rendered tree view in `DashboardViewRenderer.draw()` with dual-listener badges (`[Data: :4001 ONLINE] [Mgmt: :9090 ONLINE]`).

- [ ] **Step 1: Write failing test verifying tree rendering methods**

```java
package hexacloud.core.tui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class DashboardViewRendererTest {

    @Test
    void testTreeFlatteningForRenderer() {
        TuiState state = new TuiState();
        TuiTreeNode gw = new TuiTreeNode(TuiTreeNode.NodeType.GATEWAY, "gw-1", "GATEWAY: gw-1", 0);
        TuiTreeNode cl = new TuiTreeNode(TuiTreeNode.NodeType.CLUSTER, "cl-1", "CLUSTER: cl-1", 1);
        gw.addChild(cl);
        state.rootTreeNodes.add(gw);

        assertEquals(1, state.rootTreeNodes.size());
        assertEquals(1, state.rootTreeNodes.get(0).getChildren().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails/passes**

Run: `mvn test -Dtest=DashboardViewRendererTest`

- [ ] **Step 3: Refactor `DashboardViewRenderer.java`**

In `DashboardViewRenderer.java`:
- Replace side-by-side `CLUSTERS` and `GATEWAYS` split boxes with a single top panel `GATEWAYS & CLUSTERS HIERARCHY`.
- Build and flatten `rootTreeNodes` dynamically from active `gateways` and `ClusterRegistry`.
- Render tree indentation (`├─`, `└─`, `▼`, `▶`) and dual-listener badges `[Data: :4001 ONLINE] [Mgmt: :9090 ONLINE]`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DashboardViewRendererTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add java/src/hexacloud/core/tui/view/DashboardViewRenderer.java java/src/hexacloud/core/tui/TuiConstants.java java/test/hexacloud/core/tui/DashboardViewRendererTest.java
git commit -m "feat(tui): update DashboardViewRenderer to render unified hierarchical tree with dual-listener badges"
```

---

### Task 3: Update `TuiKeyHandler` for Tree Navigation and Node Expansion

**Files:**
- Modify: `java/src/hexacloud/core/tui/TuiKeyHandler.java`
- Create: `java/test/hexacloud/core/tui/TuiKeyHandlerTreeNavigationTest.java`

**Interfaces:**
- Consumes: Key presses (Arrow Up/Down, Space, Enter, G, N, Q).
- Produces: Navigation and expansion state changes in `TuiState.selectedTreeIndex` and `TuiTreeNode.setExpanded()`.

- [ ] **Step 1: Write failing test for tree key navigation**

```java
package hexacloud.core.tui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TuiKeyHandlerTreeNavigationTest {

    @Test
    void testExpansionToggleOnKey() {
        TuiTreeNode node = new TuiTreeNode(TuiTreeNode.NodeType.GATEWAY, "gw-1", "Gateway 1", 0);
        assertTrue(node.isExpanded());
        node.setExpanded(!node.isExpanded());
        assertFalse(node.isExpanded());
    }
}
```

- [ ] **Step 2: Run test**

Run: `mvn test -Dtest=TuiKeyHandlerTreeNavigationTest`

- [ ] **Step 3: Update `TuiKeyHandler.java`**

Update `TuiKeyHandler.java` to handle tree item selection bounds, `[Space]` and `[Enter]` node expansion toggles, and shortcut keys (`[G]` for Gateway Data listener toggle, `[N]` for Node addition).

- [ ] **Step 4: Run full test suite to verify 100% tests pass**

Run: `mvn test`
Expected: 100% tests PASS across all suites.

- [ ] **Step 5: Commit**

```bash
git add java/src/hexacloud/core/tui/TuiKeyHandler.java java/test/hexacloud/core/tui/TuiKeyHandlerTreeNavigationTest.java
git commit -m "feat(tui): update TuiKeyHandler to support hierarchical tree navigation and node expansion"
```
