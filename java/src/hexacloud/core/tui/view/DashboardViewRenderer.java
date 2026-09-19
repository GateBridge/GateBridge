package hexacloud.core.tui.view;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.event.TuiEvent;
import hexacloud.core.model.ServerNode;
import hexacloud.core.tui.TerminalUI;
import hexacloud.core.tui.TuiFrameBuffer;
import hexacloud.core.tui.TuiRenderer;
import hexacloud.core.tui.TuiState;
import hexacloud.core.tui.TuiTreeNode;
import hexacloud.core.tui.TuiTreeNode.NodeType;
import hexacloud.core.utils.common.Casts;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.core.utils.terminal.NativeTerminal;
import hexacloud.core.utils.common.StrUtils;

import static hexacloud.core.tui.TuiConstants.*;

/**
 * Handles visual rendering for the main DevOps Dashboard View with a unified hierarchical tree structure.
 */
public class DashboardViewRenderer {
    private final TerminalUI tui;
    private final TuiRenderer mainRenderer;
    private static boolean cpuErrorLogged = false;
    private static boolean threadErrorLogged = false;

    public DashboardViewRenderer(TerminalUI tui, TuiRenderer mainRenderer) {
        this.tui = tui;
        this.mainRenderer = mainRenderer;
    }

    public static List<TuiTreeNode> flattenVisibleNodes(List<TuiTreeNode> rootNodes) {
        List<TuiTreeNode> visibleList = new ArrayList<>();
        if (rootNodes != null) {
            for (TuiTreeNode root : rootNodes) {
                collectVisibleNodes(root, visibleList);
            }
        }
        return visibleList;
    }

    private static void collectVisibleNodes(TuiTreeNode node, List<TuiTreeNode> list) {
        list.add(node);
        if (node.isExpanded() && node.getChildren() != null) {
            for (TuiTreeNode child : node.getChildren()) {
                collectVisibleNodes(child, list);
            }
        }
    }

    public void updateTreeNodes(TuiState state) {
        Set<String> expandedIds = new HashSet<>();
        collectExpandedIds(state.rootTreeNodes, expandedIds);
        boolean isInitial = state.rootTreeNodes.isEmpty();

        state.rootTreeNodes.clear();

        Set<String> processedClusters = new HashSet<>();

        if (!state.gateways.isEmpty()) {
            for (TuiState.GatewayConfig gw : state.gateways) {
                String gwId = "gw:" + gw.gatewayName;
                String dataBadge = "[Data: :" + gw.port + " " + (gw.running ? "ONLINE" : "OFFLINE") + "]";
                String mgmtBadge = "[Mgmt: :" + gw.adminPort + " " + (gw.running ? "ONLINE" : "OFFLINE") + "]";
                String gwLabel = "GATEWAY: " + gw.gatewayName + "  " + dataBadge + " " + mgmtBadge;

                TuiTreeNode gwNode = new TuiTreeNode(NodeType.GATEWAY, gwId, gwLabel, 0);
                gwNode.setData(gw);
                if (!isInitial) {
                    gwNode.setExpanded(expandedIds.contains(gwId));
                }
                state.rootTreeNodes.add(gwNode);

                if (gw.clusterName != null && !gw.clusterName.isEmpty()) {
                    processedClusters.add(gw.clusterName);
                    String routeId = gwId + ":route:/proxy/**";
                    TuiTreeNode routeNode = new TuiTreeNode(NodeType.ROUTE, routeId, "Ingress Route: /proxy/**", 1);
                    if (!isInitial) {
                        routeNode.setExpanded(expandedIds.contains(routeId));
                    }
                    gwNode.addChild(routeNode);

                    Cluster c = ClusterRegistry.getInstance().getCluster(gw.clusterName);
                    String modeStr = (c != null) ? c.getRoutingMode().name() : "HYBRID";
                    String clusterId = routeId + ":cluster:" + gw.clusterName;
                    TuiTreeNode clusterNode = new TuiTreeNode(NodeType.CLUSTER, clusterId, "TARGET CLUSTER: " + gw.clusterName + " (Mode: " + modeStr + ")", 2);
                    clusterNode.setData(gw.clusterName);
                    if (!isInitial) {
                        clusterNode.setExpanded(expandedIds.contains(clusterId));
                    }
                    routeNode.addChild(clusterNode);

                    List<ServerNode> nodes = (c != null) ? c.getCluster() : java.util.Collections.emptyList();
                    for (int i = 0; i < nodes.size(); i++) {
                        ServerNode node = nodes.get(i);
                        String nodeId = clusterId + ":node:" + node.getFullHost();
                        String nodeLabel = "Node " + (i + 1) + ": " + node.getFullHost() + "  [" + node.status().name() + "] (" + node.latencyMs() + "ms)";
                        TuiTreeNode nodeItem = new TuiTreeNode(NodeType.SERVER_NODE, nodeId, nodeLabel, 3);
                        nodeItem.setData(node);
                        clusterNode.addChild(nodeItem);
                    }
                }
            }
        }

        // Add orphan clusters not assigned to any gateway
        java.util.Collection<Cluster> allClusters = ClusterRegistry.getInstance().getClusters();
        for (Cluster c : allClusters) {
            if (!processedClusters.contains(c.getClusterName())) {
                String clusterId = "cluster:" + c.getClusterName();
                TuiTreeNode clNode = new TuiTreeNode(NodeType.CLUSTER, clusterId, "TARGET CLUSTER: " + c.getClusterName() + " (Mode: " + c.getRoutingMode().name() + ")", 0);
                clNode.setData(c.getClusterName());
                if (!isInitial) {
                    clNode.setExpanded(expandedIds.contains(clusterId));
                }
                state.rootTreeNodes.add(clNode);

                List<ServerNode> nodes = c.getCluster();
                for (int i = 0; i < nodes.size(); i++) {
                    ServerNode node = nodes.get(i);
                    String nodeId = clusterId + ":node:" + node.getFullHost();
                    String nodeLabel = "Node " + (i + 1) + ": " + node.getFullHost() + "  [" + node.status().name() + "] (" + node.latencyMs() + "ms)";
                    TuiTreeNode nodeItem = new TuiTreeNode(NodeType.SERVER_NODE, nodeId, nodeLabel, 1);
                    nodeItem.setData(node);
                    clNode.addChild(nodeItem);
                }
            }
        }
    }

    private void collectExpandedIds(List<TuiTreeNode> nodes, Set<String> expandedIds) {
        if (nodes == null) return;
        for (TuiTreeNode node : nodes) {
            if (node.isExpanded()) {
                expandedIds.add(node.getId());
            }
            collectExpandedIds(node.getChildren(), expandedIds);
        }
    }

    public void draw() {
        int W = NativeTerminal.getTerminalWidth();
        int H = NativeTerminal.getTerminalHeight();
        TuiFrameBuffer frameBuffer = new TuiFrameBuffer(W, H);
        frameBuffer.beginFrame();
        draw(frameBuffer);
        frameBuffer.flushToTerminal();
    }

    public void draw(TuiFrameBuffer frameBuffer) {
        TuiState state = tui.state();
        int W = NativeTerminal.getTerminalWidth();
        int H = NativeTerminal.getTerminalHeight();
        if (W < 110) W = 110; // Hard minimum
        if (H < 24) H = 24;   // Hard minimum

        updateTreeNodes(state);
        List<TuiTreeNode> visibleNodes = flattenVisibleNodes(state.rootTreeNodes);

        // Synchronize selected tree index bounds
        if (visibleNodes.isEmpty()) {
            state.selectedTreeIndex = 0;
        } else {
            if (state.selectedTreeIndex < 0) state.selectedTreeIndex = 0;
            if (state.selectedTreeIndex >= visibleNodes.size()) state.selectedTreeIndex = visibleNodes.size() - 1;

            // Sync state indices with currently selected node
            TuiTreeNode selectedNode = visibleNodes.get(state.selectedTreeIndex);
            if (selectedNode.getType() == NodeType.GATEWAY && selectedNode.getData() instanceof TuiState.GatewayConfig) {
                TuiState.GatewayConfig gw = (TuiState.GatewayConfig) selectedNode.getData();
                state.selectedGatewayIndex = state.gateways.indexOf(gw);
                if (gw != null && gw.clusterName != null && !gw.clusterName.isEmpty()) {
                    state.selectedClusterName = gw.clusterName;
                }
            } else if (selectedNode.getType() == NodeType.CLUSTER && selectedNode.getData() instanceof String) {
                state.selectedClusterName = (String) selectedNode.getData();
            } else if (selectedNode.getType() == NodeType.SERVER_NODE && selectedNode.getData() instanceof ServerNode) {
                ServerNode n = (ServerNode) selectedNode.getData();
                if (state.nodes != null) {
                    int nIdx = state.nodes.indexOf(n);
                    if (nIdx != -1) state.selectedNodeIndex = nIdx;
                }
            }
        }

        // 1. Draw top panel boxes: Hierarchical Tree and Live Metrics
        mainRenderer.drawBox(frameBuffer, 2, 5, W - 31, 14, "GATEWAYS & CLUSTERS HIERARCHY", true);
        mainRenderer.drawBox(frameBuffer, W - 29, 5, W, 14, "GATEWAYS & SYSTEM", false);

        // 2. Draw bottom panel boxes: Logs and Events
        mainRenderer.drawBox(frameBuffer, 2, 15, W / 2, H - 2, "RECENT SYSTEM LOGS [L: Full Logs]", false);
        mainRenderer.drawBox(frameBuffer, W / 2 + 2, 15, W, H - 2, "RECENT EVENTS", false);

        // 3. Render Tree Nodes into top box
        int visibleCount = 8; // Available rows inside Y=6..13
        int viewportStart = 0;
        if (state.selectedTreeIndex >= visibleCount) {
            viewportStart = state.selectedTreeIndex - visibleCount + 1;
        }
        int maxStart = Math.max(0, visibleNodes.size() - visibleCount);
        if (viewportStart > maxStart) viewportStart = maxStart;

        int treeWidth = (W - 31) - 4;
        if (treeWidth < 40) treeWidth = 40;

        if (visibleNodes.isEmpty()) {
            frameBuffer.printAt(4, 6, RED + "No gateways or clusters registered." + RESET);
            for (int r = 7; r <= 13; r++) {
                frameBuffer.printAt(4, r, StrUtils.repeat(" ", treeWidth));
            }
        } else {
            for (int i = 0; i < visibleCount; i++) {
                int nodeIdx = viewportStart + i;
                int y = 6 + i;
                if (nodeIdx < visibleNodes.size()) {
                    TuiTreeNode node = visibleNodes.get(nodeIdx);
                    boolean isSelected = (nodeIdx == state.selectedTreeIndex);

                    String prefix = isSelected ? CYAN + "➔ " + RESET : "  ";
                    String treeBranch = buildBranchPrefix(node, visibleNodes, nodeIdx);
                    String formattedLabel = formatNodeLabel(node, isSelected);

                    String lineText = prefix + treeBranch + formattedLabel;
                    String clearedLine = lineText + StrUtils.repeat(" ", Math.max(0, treeWidth - stripAnsi(lineText).length()));
                    if (stripAnsi(clearedLine).length() > treeWidth) {
                        clearedLine = truncateAnsi(clearedLine, treeWidth);
                    }

                    frameBuffer.printAt(4, y, clearedLine);
                } else {
                    frameBuffer.printAt(4, y, StrUtils.repeat(" ", treeWidth));
                }
            }

            if (viewportStart > 0) {
                frameBuffer.printAt(W - 33, 5, WHITE_BOLD + "▲" + RESET);
            }
            if (viewportStart + visibleCount < visibleNodes.size()) {
                frameBuffer.printAt(W - 33, 14, WHITE_BOLD + "▼" + RESET);
            }
        }

        // 4. Render GATEWAYS & SYSTEM Live Metrics
        int xMetrics = W - 27;
        frameBuffer.printAt(xMetrics, 6, WHITE_BOLD + "SYSTEM RESOURCES" + RESET);
        long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        long allocatedMem = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        
        frameBuffer.printAt(xMetrics, 7, "RAM Used:   " + CYAN + usedMem + " MB" + RESET);
        frameBuffer.printAt(xMetrics, 8, "RAM Alloc:  " + CYAN + allocatedMem + " MB" + RESET);
        frameBuffer.printAt(xMetrics, 9, "RAM Max:    " + CYAN + maxMem + " MB" + RESET);

        double cpu = -1;
        try {
            java.lang.management.OperatingSystemMXBean osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            cpu = Casts.<Double>matchValue(osBean)
                .when(com.sun.management.OperatingSystemMXBean.class, sunBean -> sunBean.getProcessCpuLoad() * 100)
                .orElse(cpu);
        } catch (Throwable t) {
            if (!cpuErrorLogged) {
                DebugUtils.error("TUI", null, "Failed to retrieve CPU load metrics", t);
                cpuErrorLogged = true;
            }
        }
        String cpuStr = cpu >= 0 ? String.format("%.1f %%", cpu) : "N/A";
        frameBuffer.printAt(xMetrics, 10, "CPU Load:   " + YELLOW + cpuStr + RESET);

        int threads = java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount();
        int appThreads = 0;
        try {
            java.util.Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            for (Thread thread : threadSet) {
                String name = thread.getName();
                if (!(thread.isDaemon() && (name.contains("ForkJoinPool") || name.contains("VirtualThread-unblocker") ||
                    name.equals("Reference Handler") || name.equals("Finalizer") || 
                    name.equals("Signal Dispatcher") || name.equals("Notification Thread") || 
                    name.equals("Common-Cleaner") || name.equals("Attach Listener")))) {
                    appThreads++;
                }
            }
        } catch (Throwable t) {
            if (!threadErrorLogged) {
                DebugUtils.error("TUI", null, "Failed to retrieve OS threads metrics", t);
                threadErrorLogged = true;
            }
            appThreads = 1;
        }
        frameBuffer.printAt(xMetrics, 11, "OS Threads: " + CYAN + threads + RESET + " (App: " + CYAN + appThreads + RESET + ")");

        int gwCount = tui.activeGateways().size();
        String gwSummary = "Gateways:   " + (gwCount == 0 ? RED + "None" + RESET : GREEN + String.valueOf(gwCount) + RESET);
        if (gwCount > 0) {
            int firstPort = tui.activeGateways().values().iterator().next().getPort();
            gwSummary += " (:" + firstPort + ")";
        }
        String summaryPadding = StrUtils.repeat(" ", Math.max(0, 26 - stripAnsi(gwSummary).length()));
        frameBuffer.printAt(xMetrics, 12, gwSummary + summaryPadding);

        // 5. Render RECENT SYSTEM LOGS
        int yLog = 16;
        int maxLogWidth = (W / 2) - 4;
        int logsLimit = (H - 2) - 15 - 1;
        List<DebugUtils.LogEntry> dashboardLogs = DebugUtils.getDashboardLogs();
        if (dashboardLogs.isEmpty()) {
            frameBuffer.printAt(4, yLog, "No logs recorded yet.");
            yLog++;
        } else {
            int startIdx = Math.max(0, dashboardLogs.size() - logsLimit);
            for (int i = startIdx; i < dashboardLogs.size(); i++) {
                DebugUtils.LogEntry entry = dashboardLogs.get(i);
                String logLine = entry.toString();
                StringBuilder clearedLine = new StringBuilder(logLine);
                while (clearedLine.length() < maxLogWidth) clearedLine.append(" ");
                String outputLine = clearedLine.substring(0, maxLogWidth);

                if (entry.getLevel() == DebugUtils.LogLevel.ERROR) {
                    frameBuffer.printAt(4, yLog, RED + outputLine + RESET);
                } else if (entry.getLevel() == DebugUtils.LogLevel.INFO) {
                    frameBuffer.printAt(4, yLog, CYAN + outputLine + RESET);
                } else {
                    frameBuffer.printAt(4, yLog, outputLine);
                }
                yLog++;
            }
        }
        for (int r = yLog; r < H - 2; r++) {
            frameBuffer.printAt(4, r, StrUtils.repeat(" ", maxLogWidth));
        }

        // 6. Render RECENT EVENTS
        int eventY = 16;
        int xEvents = W / 2 + 4;
        int maxEventWidth = W - xEvents - 2;
        if (state.recentEvents.isEmpty()) {
            frameBuffer.printAt(xEvents, eventY, GRAY + "No recent events." + RESET);
            eventY++;
        } else {
            for (TuiEvent event : state.recentEvents) {
                if (eventY >= H - 2) break;

                long diffMs = System.currentTimeMillis() - event.timestamp();
                String timeAgo;
                if (diffMs < 1000) {
                    timeAgo = "now";
                } else if (diffMs < 60000) {
                    timeAgo = (diffMs / 1000) + "s";
                } else if (diffMs < 3600000) {
                    timeAgo = (diffMs / 60000) + "m";
                } else {
                    timeAgo = (diffMs / 3600000) + "h";
                }

                String timeStr = "[" + timeAgo + "] ";
                int remaining = maxEventWidth - timeStr.length();
                
                String shortName;
                switch (event.type()) {
                    case "NodeStatusChanged": shortName = "Status"; break;
                    case "NodeTelemetryUpdated": shortName = "Telemetry"; break;
                    case "NodeEventSubmitted": shortName = "NodeEvent"; break;
                    case "NodeRegistered": shortName = "NodeReg"; break;
                    case "NodeDeregistered": shortName = "NodeDereg"; break;
                    case "ClusterRegistered": shortName = "ClusterReg"; break;
                    case "DeveloperCustomEvent":
                    case "UserCustomEvent": shortName = "CustomEvent"; break;
                    default: 
                        shortName = event.type();
                        if (shortName.length() > 15) shortName = shortName.substring(0, 15);
                }

                String eventText = shortName + (event.detail().isEmpty() ? "" : ": " + event.detail());
                if (eventText.length() > remaining) {
                    eventText = eventText.substring(0, remaining - 3) + "...";
                }

                String color = YELLOW;
                if (event.type().contains("Deregistered") || event.type().contains("Dereg")) {
                    color = RED;
                } else if (event.type().contains("Registered") || event.type().contains("Reg")) {
                    color = GREEN;
                } else if (event.type().contains("Custom")) {
                    color = MAGENTA;
                } else if (event.type().contains("NodeEvent")) {
                    color = MAGENTA;
                } else if (event.type().contains("Telemetry")) {
                    color = CYAN;
                }

                String colorized = timeStr + color + eventText + RESET;
                int printedLen = timeStr.length() + eventText.length();
                String padding = StrUtils.repeat(" ", Math.max(0, maxEventWidth - printedLen));
                frameBuffer.printAt(xEvents, eventY, colorized + padding);
                eventY++;
            }
        }
        for (int row = eventY; row < H - 2; row++) {
            frameBuffer.printAt(xEvents, row, StrUtils.repeat(" ", maxEventWidth));
        }

        // 7. Render bottom controls
        StringBuilder controlsStr = new StringBuilder();
        controlsStr.append("  [▲/▼] Navigate  [Space/Enter] Expand/Collapse");
        if (!tui.readOnly()) {
            controlsStr.append("  [G] Toggle GW  [N] Add Node");
            if (tui.clusterManagementEnabled()) controlsStr.append("  [C] New Cluster");
        }
        controlsStr.append("  [L] Logs  [Q] Exit");

        frameBuffer.printAt(2, H - 1, StrUtils.repeat(" ", W - 4));
        frameBuffer.printAt(2, H - 1, WHITE_BOLD + "Controls:" + RESET + controlsStr.toString());
    }

    private String buildBranchPrefix(TuiTreeNode node, List<TuiTreeNode> visibleNodes, int index) {
        int depth = node.getDepth();
        boolean hasChildren = !node.getChildren().isEmpty();
        boolean isExpanded = node.isExpanded();
        String expandIcon = hasChildren ? (isExpanded ? "▼ " : "▶ ") : "";

        if (depth == 0) {
            return hasChildren ? expandIcon : "  ";
        }

        boolean isLastChild = checkIsLastSibling(node, visibleNodes, index);
        String connector = isLastChild ? "└─ " : "├─ ";

        if (depth == 1) {
            return "  " + connector + expandIcon;
        } else if (depth == 2) {
            return "      " + connector + expandIcon;
        } else {
            return "          " + connector + expandIcon;
        }
    }

    private boolean checkIsLastSibling(TuiTreeNode node, List<TuiTreeNode> visibleNodes, int index) {
        if (index >= visibleNodes.size() - 1) return true;
        TuiTreeNode next = visibleNodes.get(index + 1);
        return next.getDepth() < node.getDepth();
    }

    private String formatNodeLabel(TuiTreeNode node, boolean isSelected) {
        String rawLabel = node.getLabel();
        if (node.getType() == NodeType.GATEWAY && node.getData() instanceof TuiState.GatewayConfig) {
            TuiState.GatewayConfig gw = (TuiState.GatewayConfig) node.getData();
            String dataStatusStr = gw.running ? GREEN + "ONLINE" + RESET : RED + "OFFLINE" + RESET;
            String mgmtStatusStr = gw.running ? GREEN + "ONLINE" + RESET : RED + "OFFLINE" + RESET;
            String dataBadge = "[Data: :" + gw.port + " " + dataStatusStr + "]";
            String mgmtBadge = "[Mgmt: :" + gw.adminPort + " " + mgmtStatusStr + "]";
            String gwNameStr = isSelected ? WHITE_BOLD + gw.gatewayName + RESET : gw.gatewayName;
            return WHITE_BOLD + "GATEWAY: " + RESET + gwNameStr + "  " + dataBadge + " " + mgmtBadge;
        } else if (node.getType() == NodeType.ROUTE) {
            return CYAN + rawLabel + RESET;
        } else if (node.getType() == NodeType.CLUSTER) {
            if (isSelected) {
                return WHITE_BOLD + rawLabel + RESET;
            }
            return rawLabel;
        } else if (node.getType() == NodeType.SERVER_NODE) {
            if (node.getData() instanceof ServerNode) {
                ServerNode sn = (ServerNode) node.getData();
                String statusStr = sn.status().name();
                String colorStr = statusStr.equals("ONLINE") ? GREEN : (statusStr.equals("UNSTABLE") ? YELLOW : RED);
                String labelStr = rawLabel.replace("[" + statusStr + "]", "[" + colorStr + statusStr + RESET + "]");
                return isSelected ? WHITE_BOLD + labelStr + RESET : labelStr;
            }
        }
        return isSelected ? WHITE_BOLD + rawLabel + RESET : rawLabel;
    }

    private static String stripAnsi(String text) {
        if (text == null) return "";
        return text.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    private static String truncateAnsi(String text, int maxWidth) {
        if (stripAnsi(text).length() <= maxWidth) return text;
        StringBuilder sb = new StringBuilder();
        int visibleLen = 0;
        boolean inAnsi = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\033') {
                inAnsi = true;
                sb.append(c);
            } else if (inAnsi) {
                sb.append(c);
                if (c == 'm') inAnsi = false;
            } else {
                if (visibleLen < maxWidth) {
                    sb.append(c);
                    visibleLen++;
                } else {
                    break;
                }
            }
        }
        sb.append(RESET);
        return sb.toString();
    }
}
