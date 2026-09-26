package hexacloud.infra.gateway;

import hexacloud.core.ports.GatewayBuilderPort;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GatewayBuilderThresholdIntegrationTest {

    @Test
    void testBuilderExposesThresholdConfiguration() {
        LocalGatewayAdapter adapter = (LocalGatewayAdapter) GatewayFactory.createGateway("builder-test-gateway")
                .pingFailureThreshold(5)
                .pingRecoveryThreshold(3);

        assertEquals(5, adapter.getSchedulerPing().getFailureThreshold());
        assertEquals(3, adapter.getSchedulerPing().getRecoveryThreshold());
    }

    @Test
    void testBuilderValidatesThresholds() {
        GatewayBuilderPort builder = GatewayFactory.createGateway("builder-validation-test");

        assertThrows(IllegalArgumentException.class, () -> builder.pingFailureThreshold(0));
        assertThrows(IllegalArgumentException.class, () -> builder.pingFailureThreshold(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.pingRecoveryThreshold(0));
        assertThrows(IllegalArgumentException.class, () -> builder.pingRecoveryThreshold(-2));
    }
}
