package hexacloud.infra.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DualListenerIntegrationTest {

    private LocalGatewayAdapter gateway;
    private final int dataPort = 4091;
    private final int adminPort = 9091;

    @BeforeEach
    void setUp() {
        gateway = LocalGatewayAdapter.builder()
                .port(dataPort)
                .adminPort(adminPort)
                .adminHost("127.0.0.1")
                .build();
        gateway.start();
    }

    @AfterEach
    void tearDown() {
        if (gateway != null) {
            gateway.stop();
        }
    }

    @Test
    void testAdminEndpointAccessibleOnAdminPortAndBlockedOnDataPort() throws Exception {
        // Admin Port check -> 200 OK
        URL adminUrl = new URL("http://127.0.0.1:" + adminPort + "/v1/get_nodes_json");
        HttpURLConnection adminConn = (HttpURLConnection) adminUrl.openConnection();
        adminConn.setRequestMethod("GET");
        assertEquals(200, adminConn.getResponseCode());

        // Data Port check -> 404 Not Found
        URL dataUrl = new URL("http://127.0.0.1:" + dataPort + "/v1/get_nodes_json");
        HttpURLConnection dataConn = (HttpURLConnection) dataUrl.openConnection();
        dataConn.setRequestMethod("GET");
        assertEquals(404, dataConn.getResponseCode());
    }
}
