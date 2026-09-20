package hexacloud.infra.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.server.route.ClusterController;
import hexacloud.core.server.route.RouteRegistry;

public class UndertowManagementTransportTest {

    private UndertowManagementTransport managementTransport;
    private RouteRegistry routeRegistry;
    private Cluster testCluster;

    @BeforeEach
    void setUp() throws Exception {
        routeRegistry = new RouteRegistry();
        testCluster = new Cluster("test-mgmt-cluster");
        routeRegistry.registerController(new ClusterController(testCluster));

        managementTransport = new UndertowManagementTransport("127.0.0.1", 9099, routeRegistry);
        managementTransport.start();
    }

    @AfterEach
    void tearDown() {
        if (managementTransport != null) {
            managementTransport.stop();
        }
    }

    @Test
    void testManagementEndpointRespondsOnDedicatedPort() throws Exception {
        URL url = new URL("http://127.0.0.1:9099/v1/get_nodes_json");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String response = reader.readLine();
            assertNotNull(response);
            assertTrue(response.startsWith("["));
        }
    }
}
