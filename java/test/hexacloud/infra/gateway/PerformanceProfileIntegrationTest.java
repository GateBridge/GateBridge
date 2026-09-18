package hexacloud.infra.gateway;

import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.ports.GatewayBuilderPort;
import hexacloud.core.server.PerformanceProfile;
import hexacloud.core.server.ServerManager;
import hexacloud.infra.server.HttpTransport;
import hexacloud.infra.server.UndertowHttpTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PerformanceProfileIntegrationTest {

    @BeforeEach
    public void setUp() {
        ClusterRegistry.getInstance().clear();
    }

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            return 9090;
        }
    }

    @Test
    public void testGatewayBuilderAppliesPerformanceProfile() {
        GatewayBuilderPort builder = GatewayFactory.createGateway("test-gw", "test-cluster")
            .port(4999)
            .enableHttp(true)
            .performanceProfile(PerformanceProfile.BALANCED_1GB);

        LocalGatewayAdapter adapter = (LocalGatewayAdapter) builder;
        assertEquals(PerformanceProfile.BALANCED_1GB, adapter.getPerformanceProfile());
    }

    @Test
    public void testProfileSwitchingUpdatesServerManagerAndHttpTransport() throws Exception {
        int basePort = findFreePort();
        LocalGatewayAdapter adapter = (LocalGatewayAdapter) GatewayFactory.createGateway("test-gw", "test-cluster");
        adapter.port(basePort)
               .enableHttp(true)
               .performanceProfile(PerformanceProfile.BALANCED_1GB);

        try {
            adapter.listen();

            // Verify adapter profile
            assertEquals(PerformanceProfile.BALANCED_1GB, adapter.getPerformanceProfile());

            // Access ServerManager via reflection
            Field serverManagerField = LocalGatewayAdapter.class.getDeclaredField("serverManager");
            serverManagerField.setAccessible(true);
            ServerManager serverManager = (ServerManager) serverManagerField.get(adapter);
            assertNotNull(serverManager);
            assertEquals(PerformanceProfile.BALANCED_1GB, serverManager.getPerformanceProfile());

            // Access activeTransports via reflection
            Field activeTransportsField = ServerManager.class.getDeclaredField("activeTransports");
            activeTransportsField.setAccessible(true);
            List<?> activeTransports = (List<?>) activeTransportsField.get(serverManager);
            assertFalse(activeTransports.isEmpty(), "Active transports list should not be empty");

            for (Object transport : activeTransports) {
                if (transport instanceof HttpTransport) {
                    assertEquals(PerformanceProfile.BALANCED_1GB, ((HttpTransport) transport).getPerformanceProfile());
                } else if (transport instanceof UndertowHttpTransport) {
                    assertEquals(PerformanceProfile.BALANCED_1GB, ((UndertowHttpTransport) transport).getPerformanceProfile());
                }
            }

            // Perform profile switching to RESILIENT
            adapter.performanceProfile(PerformanceProfile.RESILIENT);
            assertEquals(PerformanceProfile.RESILIENT, adapter.getPerformanceProfile());
            assertEquals(PerformanceProfile.RESILIENT, serverManager.getPerformanceProfile());

            for (Object transport : activeTransports) {
                if (transport instanceof HttpTransport) {
                    assertEquals(PerformanceProfile.RESILIENT, ((HttpTransport) transport).getPerformanceProfile());
                } else if (transport instanceof UndertowHttpTransport) {
                    assertEquals(PerformanceProfile.RESILIENT, ((UndertowHttpTransport) transport).getPerformanceProfile());
                }
            }

            // Perform profile switching to MAX_PERFORMANCE
            adapter.performanceProfile(PerformanceProfile.MAX_PERFORMANCE);
            assertEquals(PerformanceProfile.MAX_PERFORMANCE, adapter.getPerformanceProfile());
            assertEquals(PerformanceProfile.MAX_PERFORMANCE, serverManager.getPerformanceProfile());

            for (Object transport : activeTransports) {
                if (transport instanceof HttpTransport) {
                    assertEquals(PerformanceProfile.MAX_PERFORMANCE, ((HttpTransport) transport).getPerformanceProfile());
                } else if (transport instanceof UndertowHttpTransport) {
                    assertEquals(PerformanceProfile.MAX_PERFORMANCE, ((UndertowHttpTransport) transport).getPerformanceProfile());
                }
            }
        } finally {
            adapter.stop();
        }
    }
}
