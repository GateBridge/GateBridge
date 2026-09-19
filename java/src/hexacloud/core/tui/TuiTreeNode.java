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
