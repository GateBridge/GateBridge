package hexacloud.infra.server;

import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UndertowHttpTransportTest {

    private String originalGeneratorProp;

    @BeforeEach
    public void setUp() {
        originalGeneratorProp = System.getProperty("gatebridge.connection.id.generator");
    }

    @AfterEach
    public void tearDown() {
        if (originalGeneratorProp != null) {
            System.setProperty("gatebridge.connection.id.generator", originalGeneratorProp);
        } else {
            System.clearProperty("gatebridge.connection.id.generator");
        }
    }

    @Test
    public void testDefaultConnectionIdGeneratorIsAtomic() {
        System.clearProperty("gatebridge.connection.id.generator");
        UndertowHttpTransport transport = new UndertowHttpTransport();
        String id1 = transport.generateConnectionId();
        String id2 = transport.generateConnectionId();
        
        assertNotNull(id1);
        assertNotNull(id2);
        // Atomic counter returns numeric strings
        assertTrue(id1.matches("\\d+"), "Default connection ID should be numeric atomic counter, got: " + id1);
        assertTrue(id2.matches("\\d+"), "Default connection ID should be numeric atomic counter, got: " + id2);
        assertNotEquals(id1, id2, "Connection IDs should increment");
    }

    @Test
    public void testExplicitAtomicConnectionIdGenerator() {
        System.setProperty("gatebridge.connection.id.generator", "atomic");
        UndertowHttpTransport transport = new UndertowHttpTransport();
        String id = transport.generateConnectionId();
        
        assertNotNull(id);
        assertTrue(id.matches("\\d+"), "Connection ID when 'atomic' should be numeric, got: " + id);
    }

    @Test
    public void testExplicitUuidConnectionIdGenerator() {
        System.setProperty("gatebridge.connection.id.generator", "uuid");
        UndertowHttpTransport transport = new UndertowHttpTransport();
        String id = transport.generateConnectionId();
        
        assertNotNull(id);
        // UUID regex pattern
        assertTrue(id.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"),
                "Connection ID when 'uuid' should be UUID format, got: " + id);
    }

    @Test
    public void testByteBufferPoolConfiguration() {
        UndertowHttpTransport transport = new UndertowHttpTransport();
        ByteBufferPool pool = transport.createByteBufferPool();
        
        assertNotNull(pool);
        assertTrue(pool instanceof DefaultByteBufferPool, "Pool should be an instance of DefaultByteBufferPool");
        DefaultByteBufferPool defaultPool = (DefaultByteBufferPool) pool;
        assertEquals(4096, defaultPool.getBufferSize(), "ByteBufferPool slice size should be 4096 bytes");
        assertFalse(defaultPool.isDirect(), "ByteBufferPool should use heap buffers (direct=false)");
    }
}
