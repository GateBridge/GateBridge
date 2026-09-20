package hexacloud.core.tui.model;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.PingProtocol;
import hexacloud.core.model.RoutingProtocol;
import hexacloud.core.model.ServerNode;

/**
 * Immutable snapshot representation of a ServerNode for TUI rendering.
 * Prevents race conditions and partial render reads when background threads update server nodes.
 */
public final class NodeView {

    private final String id;
    private final String name;
    private final String host;
    private final int port;
    private final NodeStatus status;
    private final boolean isExternal;
    private final PingProtocol pingProtocol;
    private final String pingPath;
    private final String pingHeaderName;
    private final String pingHeaderValue;
    private final boolean isDynamic;
    private final boolean telemetryOnly;
    private final RoutingProtocol routingProtocol;
    private final int latencyMs;
    private final double cpuUsage;
    private final double ramUsage;
    private final String runtime;
    private final String role;

    public NodeView(String id, String name, String host, int port, NodeStatus status,
                    boolean isExternal, PingProtocol pingProtocol, String pingPath,
                    String pingHeaderName, String pingHeaderValue, boolean isDynamic,
                    boolean telemetryOnly, RoutingProtocol routingProtocol,
                    int latencyMs, double cpuUsage, double ramUsage, String runtime, String role) {
        this.id = id != null ? id : "";
        this.name = name != null ? name : "";
        this.host = host != null ? host : "";
        this.port = port;
        this.status = status != null ? status : NodeStatus.OFFLINE;
        this.isExternal = isExternal;
        this.pingProtocol = pingProtocol != null ? pingProtocol : PingProtocol.HTTP;
        this.pingPath = pingPath != null ? pingPath : "/";
        this.pingHeaderName = pingHeaderName;
        this.pingHeaderValue = pingHeaderValue;
        this.isDynamic = isDynamic;
        this.telemetryOnly = telemetryOnly;
        this.routingProtocol = routingProtocol != null ? routingProtocol : RoutingProtocol.HTTP;
        this.latencyMs = latencyMs;
        this.cpuUsage = cpuUsage;
        this.ramUsage = ramUsage;
        this.runtime = runtime != null ? runtime : "";
        this.role = role != null ? role : (isExternal ? "EXTERNAL" : (telemetryOnly ? "TELEMETRY" : "GATEWAY"));
    }

    public static NodeView from(ServerNode node) {
        if (node == null) return null;
        String calculatedRole = node.isExternal() ? "EXTERNAL" : (node.telemetryOnly() ? "TELEMETRY" : "GATEWAY");
        return new NodeView(
            node.getId(),
            node.name(),
            node.host(),
            node.port(),
            node.status(),
            node.isExternal(),
            node.pingProtocol(),
            node.pingPath(),
            node.pingHeaderName(),
            node.pingHeaderValue(),
            node.isDynamic(),
            node.telemetryOnly(),
            node.routingProtocol(),
            node.latencyMs(),
            node.cpuUsage(),
            node.ramUsage(),
            node.runtime(),
            calculatedRole
        );
    }

    public String id() { return id; }
    public String getId() { return id; }

    public String name() { return name; }
    public String getName() { return name; }

    public String host() { return host; }
    public String getHost() { return host; }

    public int port() { return port; }
    public int getPort() { return port; }

    public NodeStatus status() { return status; }
    public NodeStatus getStatus() { return status; }

    public boolean isExternal() { return isExternal; }

    public PingProtocol pingProtocol() { return pingProtocol; }
    public PingProtocol getPingProtocol() { return pingProtocol; }

    public String pingPath() { return pingPath; }
    public String getPingPath() { return pingPath; }

    public String pingHeaderName() { return pingHeaderName; }
    public String getPingHeaderName() { return pingHeaderName; }

    public String pingHeaderValue() { return pingHeaderValue; }
    public String getPingHeaderValue() { return pingHeaderValue; }

    public boolean isDynamic() { return isDynamic; }

    public boolean telemetryOnly() { return telemetryOnly; }

    public RoutingProtocol routingProtocol() { return routingProtocol; }
    public RoutingProtocol getRoutingProtocol() { return routingProtocol; }

    public int latencyMs() { return latencyMs; }
    public int getLatencyMs() { return latencyMs; }

    public double cpuUsage() { return cpuUsage; }
    public double getCpuUsage() { return cpuUsage; }

    public double ramUsage() { return ramUsage; }
    public double getRamUsage() { return ramUsage; }

    public String runtime() { return runtime; }
    public String getRuntime() { return runtime; }

    public String role() { return role; }
    public String getRole() { return role; }
    public String nodeRole() { return role; }

    public String getFullHost() {
        return this.host + ":" + this.port;
    }

    public String getHostWithoutProtocol() {
        if (host == null) return "";
        return host.replaceAll("^[a-zA-Z]+://", "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeView nodeView = (NodeView) o;
        return port == nodeView.port &&
                isExternal == nodeView.isExternal &&
                isDynamic == nodeView.isDynamic &&
                telemetryOnly == nodeView.telemetryOnly &&
                latencyMs == nodeView.latencyMs &&
                Double.compare(nodeView.cpuUsage, cpuUsage) == 0 &&
                Double.compare(nodeView.ramUsage, ramUsage) == 0 &&
                java.util.Objects.equals(id, nodeView.id) &&
                java.util.Objects.equals(name, nodeView.name) &&
                java.util.Objects.equals(host, nodeView.host) &&
                status == nodeView.status &&
                pingProtocol == nodeView.pingProtocol &&
                java.util.Objects.equals(pingPath, nodeView.pingPath) &&
                java.util.Objects.equals(pingHeaderName, nodeView.pingHeaderName) &&
                java.util.Objects.equals(pingHeaderValue, nodeView.pingHeaderValue) &&
                routingProtocol == nodeView.routingProtocol &&
                java.util.Objects.equals(runtime, nodeView.runtime) &&
                java.util.Objects.equals(role, nodeView.role);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, name, host, port, status, isExternal, pingProtocol,
                pingPath, pingHeaderName, pingHeaderValue, isDynamic, telemetryOnly,
                routingProtocol, latencyMs, cpuUsage, ramUsage, runtime, role);
    }

    @Override
    public String toString() {
        return "NodeView{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", status=" + status +
                ", isExternal=" + isExternal +
                ", pingProtocol=" + pingProtocol +
                ", routingProtocol=" + routingProtocol +
                ", latencyMs=" + latencyMs +
                ", cpuUsage=" + cpuUsage +
                ", ramUsage=" + ramUsage +
                ", role='" + role + '\'' +
                '}';
    }
}
