package hexacloud.core.tui;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.RunningGatewayPort;
import hexacloud.core.utils.terminal.NativeTerminal;
import hexacloud.core.utils.terminal.TerminalScanner;
import static hexacloud.core.tui.TuiConstants.*;

/**
 * Handles all interactive CLI prompts and input reading for the Terminal UI.
 * Replaces nested loops and Arrow anti-patterns with a flat, exception-based cancellation flow.
 */
public class TuiPrompts {

    private final TerminalUI tui;

    private static class CancellationException extends Exception {}

    public TuiPrompts(TerminalUI tui) {
        this.tui = tui;
    }

    private void askPressEnterToContinue() {
        java.io.PrintStream out = hexacloud.core.utils.common.DebugUtils.getOriginalOut();
        out.print("\n" + WHITE_BOLD + "Press Enter to continue..." + RESET);
        out.flush();
        TerminalScanner.readLine();
    }

    private String readInput(String prompt) throws CancellationException {
        java.io.PrintStream out = hexacloud.core.utils.common.DebugUtils.getOriginalOut();
        out.print(prompt);
        out.flush();
        String input = TerminalScanner.readLine();
        if (input != null && input.equalsIgnoreCase("/cancel")) {
            throw new CancellationException();
        }
        return input;
    }

    public void createNewClusterPrompt() {
        TuiState state = tui.state();
        NativeTerminal.resetTerminal();
        try {
            String name = readInput("\n" + CYAN + ">> Enter unique name for new cluster (or /cancel to abort): " + RESET);
            if (name.isEmpty()) {
                System.out.println(YELLOW + "Operation cancelled." + RESET);
            } else if (ClusterRegistry.getInstance().getCluster(name) != null) {
                System.out.println(RED + "ERROR: Cluster '" + name + "' already exists." + RESET);
            } else {
                ClusterRegistry.getInstance().createCluster(name);
                System.out.println(GREEN + "SUCCESS: Created cluster '" + name + "'" + RESET);
                if (tui.gatewayManagementEnabled() && !tui.isGatewayActive(name)) {
                    System.out.print("Do you want to configure and start a gateway for this cluster now? (y/n): ");
                    String ans = TerminalScanner.readLine();
                    if (ans.equalsIgnoreCase("y")) {
                        state.selectedClusterName = name;
                        manageGatewayPrompt();
                        return;
                    }
                }
            }
        } catch (CancellationException e) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        }
        askPressEnterToContinue();
        NativeTerminal.initTerminal();
        tui.fetchClusterNames();
        state.selectedClusterIndex = 0;
    }

    public void manageGatewayPrompt() {
        TuiState state = tui.state();
        if (state.selectedClusterName.isEmpty()) {
            NativeTerminal.resetTerminal();
            System.out.println(YELLOW + "Please select or create a cluster first." + RESET);
            askPressEnterToContinue();
            NativeTerminal.initTerminal();
            return;
        }

        RunningGatewayPort currentGw = tui.activeGateways().get(state.selectedClusterName);
        NativeTerminal.resetTerminal();
        System.out.println("\n" + WHITE_BOLD + "=== Gateway Setup for Cluster: " + state.selectedClusterName + " ===" + RESET);
        boolean didOperation = false;
        if (currentGw != null) {
            System.out.println("Status: " + GREEN + "ONLINE" + RESET);
            try {
                String ans = readInput("Do you want to STOP the gateway? (y/n) [/cancel]: ");
                if (ans.equalsIgnoreCase("y")) {
                    currentGw.stop();
                    tui.activeGateways().remove(state.selectedClusterName);
                    System.out.println(GREEN + "SUCCESS: Gateway stopped." + RESET);
                } else {
                    System.out.println(YELLOW + "Operation cancelled." + RESET);
                }
                didOperation = true;
            } catch (CancellationException e) {
                System.out.println(YELLOW + "Operation cancelled." + RESET);
                didOperation = true;
            }
        } else {
            System.out.println("Status: " + RED + "OFFLINE" + RESET);
            try {
                String ans = readInput("Do you want to START the gateway? (y/n) [/cancel]: ");
                if (ans.equalsIgnoreCase("y")) {
                    String portStr = readInput("Enter base port (default 3000) [/cancel]: ");
                    int port = portStr.isEmpty() ? 3000 : java.lang.Integer.parseInt(portStr);

                    String gwNameInput = readInput("Enter gateway name (default gw-" + port + ") [/cancel]: ");
                    String gwName = gwNameInput.isEmpty() ? "gw-" + port : gwNameInput;

                    String intStr = readInput("Enter ping check interval in seconds (default 5) [/cancel]: ");
                    int pingInt = intStr.isEmpty() ? 5 : java.lang.Integer.parseInt(intStr);

                    String telnetStr = readInput("Enable Telnet? (y/n, default y) [/cancel]: ");
                    boolean telnet = !telnetStr.equalsIgnoreCase("n");

                    String httpStr = readInput("Enable HTTP? (y/n, default y) [/cancel]: ");
                    boolean http = !httpStr.equalsIgnoreCase("n");

                    String wsStr = readInput("Enable WS? (y/n, default y) [/cancel]: ");
                    boolean ws = !wsStr.equalsIgnoreCase("n");

                    RunningGatewayPort newGw = hexacloud.infra.gateway.GatewayFactory.createGateway(state.selectedClusterName)
                        .gatewayName(gwName)
                        .port(port)
                        .pingInterval(pingInt)
                        .enableTelnet(telnet)
                        .enableHttp(http)
                        .enableWs(ws)
                        .listen()
                        .startPingScheduler();

                    tui.activeGateways().put(state.selectedClusterName, newGw);
                    System.out.println(GREEN + "SUCCESS: Gateway started successfully." + RESET);
                } else {
                    System.out.println(YELLOW + "Operation cancelled." + RESET);
                }
                didOperation = true;
            } catch (CancellationException e) {
                System.out.println(YELLOW + "Operation cancelled." + RESET);
                didOperation = true;
            } catch (Exception e) {
                System.out.println(RED + "ERROR: Failed to start gateway: " + e.getMessage() + RESET);
                didOperation = true;
            }
        }
        if (didOperation) {
            tui.fetchClusterNames();
            askPressEnterToContinue();
        }
        NativeTerminal.initTerminal();
        tui.fetchNodeStatus();
    }

    public void addNewNodePrompt() {
        TuiState state = tui.state();
        if (state.selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        try {
            String input = readInput("\n" + CYAN + ">> Enter port to register new service node in " + state.selectedClusterName + " (or /cancel to abort): " + RESET);
            if (input.isEmpty()) {
                System.out.println(YELLOW + "Operation cancelled." + RESET);
            } else {
                int port = Integer.parseInt(input);
                Cluster c = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
                if (c != null) {
                    c.registerServer(port);
                    System.out.println(GREEN + "SUCCESS: Registered service node." + RESET);
                }
            }
        } catch (CancellationException e) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        } catch (NumberFormatException e) {
            System.out.println(RED + "Invalid port format." + RESET);
        }
        askPressEnterToContinue();
        NativeTerminal.initTerminal();
        tui.fetchNodeStatus();
    }

    public void changeAllowedIpsPrompt() {
        TuiState state = tui.state();
        if (state.selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        try {
            String ips = readInput("\n" + CYAN + ">> Enter Allowed IPs (comma-separated, or /cancel to abort): " + RESET);
            Cluster c = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
            if (c != null) {
                c.setAllowedIps(ips);
                System.out.println(GREEN + "SUCCESS: Allowed IPs updated." + RESET);
            }
        } catch (CancellationException e) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        }
        askPressEnterToContinue();
        NativeTerminal.initTerminal();
        tui.fetchClusterConfig(state.selectedClusterName);
    }

    public void changeTimeoutPrompt() {
        TuiState state = tui.state();
        if (state.selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        try {
            String input = readInput("\n" + CYAN + ">> Enter Timeout in ms (or /cancel to abort): " + RESET);
            if (input.isEmpty()) {
                System.out.println(YELLOW + "Operation cancelled." + RESET);
            } else {
                int timeout = Integer.parseInt(input);
                Cluster c = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
                if (c != null) {
                    c.setTimeoutMs(timeout);
                    System.out.println(GREEN + "SUCCESS: Timeout updated." + RESET);
                }
            }
        } catch (CancellationException e) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        } catch (NumberFormatException e) {
            System.out.println(RED + "Invalid timeout format." + RESET);
        }
        askPressEnterToContinue();
        NativeTerminal.initTerminal();
        tui.fetchClusterConfig(state.selectedClusterName);
    }

    public void changeRateLimitPrompt() {
        TuiState state = tui.state();
        if (state.selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        try {
            String line = readInput("\n" + CYAN + ">> Enter Rate Limit (format: <requests> <durationSeconds>, or /cancel to abort): " + RESET);
            if (line.isEmpty()) {
                System.out.println(YELLOW + "Operation cancelled." + RESET);
            } else {
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    int requests = Integer.parseInt(parts[0]);
                    int duration = Integer.parseInt(parts[1]);
                    Cluster c = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
                    if (c != null) {
                        c.setRateLimit(requests, duration);
                        System.out.println(GREEN + "SUCCESS: Rate limit updated." + RESET);
                    }
                } else {
                    System.out.println(RED + "Invalid format. Expected: <requests> <durationSeconds>" + RESET);
                }
            }
        } catch (CancellationException e) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        } catch (Exception e) {
            System.out.println(RED + "ERROR: Update failed." + RESET);
        }
        askPressEnterToContinue();
        NativeTerminal.initTerminal();
        tui.fetchClusterConfig(state.selectedClusterName);
    }

    public void changeNodePingPathPrompt(Cluster cluster, ServerNode node) {
        NativeTerminal.resetTerminal();
        try {
            String path = readInput("\n" + CYAN + ">> Enter new ping endpoint route (or /cancel to abort): " + RESET);
            ServerNode updated = new ServerNode(
                node.host(), node.port(), node.status(), node.isExternal(),
                node.pingEnabled(), path, node.pingHeaderName(), node.pingHeaderValue()
            );
            cluster.updateServerNode(updated);
            System.out.println(GREEN + "SUCCESS: Ping endpoint path updated." + RESET);
        } catch (CancellationException e) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        }
        askPressEnterToContinue();
        NativeTerminal.initTerminal();
        tui.fetchNodeStatus();
    }

    public void changeNodePingHeaderNamePrompt(Cluster cluster, ServerNode node) {
        NativeTerminal.resetTerminal();
        try {
            String name = readInput("\n" + CYAN + ">> Enter new ping auth header name (or /cancel to abort, empty for None): " + RESET);
            String val = name.isEmpty() ? null : node.pingHeaderValue();
            ServerNode updated = new ServerNode(
                node.host(), node.port(), node.status(), node.isExternal(),
                node.pingEnabled(), node.pingPath(), name.isEmpty() ? null : name, val
            );
            cluster.updateServerNode(updated);
            System.out.println(GREEN + "SUCCESS: Ping header name updated." + RESET);
        } catch (CancellationException e) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        }
        askPressEnterToContinue();
        NativeTerminal.initTerminal();
        tui.fetchNodeStatus();
    }

    public void changeNodePingHeaderValuePrompt(Cluster cluster, ServerNode node) {
        NativeTerminal.resetTerminal();
        try {
            String val = readInput("\n" + CYAN + ">> Enter new ping header token value (or /cancel to abort, empty for None): " + RESET);
            ServerNode updated = new ServerNode(
                node.host(), node.port(), node.status(), node.isExternal(),
                node.pingEnabled(), node.pingPath(), node.pingHeaderName(), val.isEmpty() ? null : val
            );
            cluster.updateServerNode(updated);
            System.out.println(GREEN + "SUCCESS: Ping header value updated." + RESET);
        } catch (CancellationException e) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        }
        askPressEnterToContinue();
        NativeTerminal.initTerminal();
        tui.fetchNodeStatus();
    }

    public void changeSecretPrompt() {
        TuiState state = tui.state();
        if (state.selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        try {
            String secret = readInput("\n" + CYAN + ">> Enter new cluster secret API token (or /cancel to abort): " + RESET);
            Cluster c = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
            if (c != null) {
                c.setSecret(secret);
                System.out.println(GREEN + "SUCCESS: Cluster secret API token updated." + RESET);
            }
        } catch (CancellationException e) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        }
        askPressEnterToContinue();
        NativeTerminal.initTerminal();
        tui.fetchClusterConfig(state.selectedClusterName);
    }

    public void allocateClusterToGatewayPrompt() {
        TuiState state = tui.state();
        if (state.gateways.isEmpty() || state.selectedGatewayIndex >= state.gateways.size()) {
            NativeTerminal.resetTerminal();
            System.out.println(YELLOW + "Please configure a gateway first." + RESET);
            askPressEnterToContinue();
            NativeTerminal.initTerminal();
            return;
        }

        TuiState.GatewayConfig gw = state.gateways.get(state.selectedGatewayIndex);
        NativeTerminal.resetTerminal();
        System.out.println("\n" + WHITE_BOLD + "=== Allocate Cluster to Gateway (Port " + gw.port + ") ===" + RESET);
        System.out.println("Current Target Cluster: " + (gw.clusterName.isEmpty() ? "None" : gw.clusterName));
        System.out.println("Available Clusters:");
        for (int i = 0; i < state.clusterNames.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + state.clusterNames.get(i));
        }
        System.out.println("  [0] None / Deallocate");

        try {
            String ans = readInput("Select cluster number (1-" + state.clusterNames.size() + ") or 0 to deallocate [/cancel]: ");
            int option = java.lang.Integer.parseInt(ans);
            if (option == 0) {
                if (gw.running) {
                    RunningGatewayPort activeGw = tui.activeGateways().remove(gw.clusterName);
                    if (activeGw != null) {
                        activeGw.stop();
                    }
                }
                gw.clusterName = "";
                System.out.println(GREEN + "SUCCESS: Cluster deallocated from gateway." + RESET);
            } else if (option > 0 && option <= state.clusterNames.size()) {
                String targetCluster = state.clusterNames.get(option - 1);
                
                if (gw.running) {
                    RunningGatewayPort oldGw = tui.activeGateways().remove(gw.clusterName);
                    if (oldGw != null) {
                        oldGw.stop();
                    }
                    
                    RunningGatewayPort newGw = hexacloud.infra.gateway.GatewayFactory.createGateway(targetCluster)
                        .port(gw.port)
                        .pingInterval(gw.pingInterval)
                        .enableTelnet(gw.telnetEnabled)
                        .enableHttp(gw.httpEnabled)
                        .enableWs(gw.wsEnabled)
                        .listen()
                        .startPingScheduler();
                    tui.activeGateways().put(targetCluster, newGw);
                }
                
                gw.clusterName = targetCluster;
                System.out.println(GREEN + "SUCCESS: Gateway now routes to cluster '" + targetCluster + "'." + RESET);
            } else {
                System.out.println(RED + "Invalid option." + RESET);
            }
        } catch (CancellationException e) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        } catch (Exception e) {
            System.out.println(RED + "ERROR: Failed to allocate cluster: " + e.getMessage() + RESET);
        }
        tui.fetchClusterNames();
        askPressEnterToContinue();
        NativeTerminal.initTerminal();
    }
}
