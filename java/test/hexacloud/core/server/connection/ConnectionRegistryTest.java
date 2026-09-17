package hexacloud.core.server.connection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionRegistryTest {

    private ConnectionRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new ConnectionRegistry();
    }

    @Test
    public void testRegisterConnectionAndOnConnectCallback() {
        List<String> connectedIds = new ArrayList<>();
        registry.addLifecycleListener(new ConnectionLifecycleListener() {
            @Override
            public void onConnect(ConnectionContext context) {
                connectedIds.add(context.getConnectionId());
            }

            @Override
            public void onHeartbeat(ConnectionContext context) {}

            @Override
            public void onDisconnect(ConnectionContext context) {}

            @Override
            public void onError(ConnectionContext context, Throwable cause) {}
        });

        ConnectionContext ctx = new ConnectionContextImpl("conn-1", "HTTP", "127.0.0.1:8080");
        registry.registerConnection(ctx);

        assertTrue(registry.containsConnection("conn-1"));
        assertEquals(1, registry.getActiveConnectionCount());
        assertSame(ctx, registry.getConnection("conn-1"));
        assertEquals(List.of("conn-1"), connectedIds);
    }

    @Test
    public void testTouchConnectionAndOnHeartbeatCallback() throws InterruptedException {
        List<String> heartbeats = new ArrayList<>();
        registry.addLifecycleListener(new ConnectionLifecycleListener() {
            @Override
            public void onConnect(ConnectionContext context) {}

            @Override
            public void onHeartbeat(ConnectionContext context) {
                heartbeats.add(context.getConnectionId());
            }

            @Override
            public void onDisconnect(ConnectionContext context) {}

            @Override
            public void onError(ConnectionContext context, Throwable cause) {}
        });

        ConnectionContext ctx = new ConnectionContextImpl("conn-touch", "TCP", "10.0.0.1:9090");
        registry.registerConnection(ctx);

        long initialActive = ctx.getLastActiveMs();
        Thread.sleep(10);

        registry.touchConnection("conn-touch");

        assertEquals(List.of("conn-touch"), heartbeats);
        assertTrue(ctx.getLastActiveMs() > initialActive);
    }

    @Test
    public void testUnregisterConnectionAndOnDisconnectCallback() {
        AtomicBoolean closed = new AtomicBoolean(false);
        List<String> disconnectedIds = new ArrayList<>();

        registry.addLifecycleListener(new ConnectionLifecycleListener() {
            @Override
            public void onConnect(ConnectionContext context) {}

            @Override
            public void onHeartbeat(ConnectionContext context) {}

            @Override
            public void onDisconnect(ConnectionContext context) {
                disconnectedIds.add(context.getConnectionId());
            }

            @Override
            public void onError(ConnectionContext context, Throwable cause) {}
        });

        ConnectionContext ctx = new ConnectionContextImpl("conn-unreg", "WS", "192.168.1.10:8000", () -> closed.set(true));
        registry.registerConnection(ctx);

        assertTrue(ctx.isAlive());
        ConnectionContext removed = registry.unregisterConnection("conn-unreg");

        assertSame(ctx, removed);
        assertFalse(ctx.isAlive());
        assertTrue(closed.get(), "onClose hook should be invoked on unregister");
        assertFalse(registry.containsConnection("conn-unreg"));
        assertEquals(0, registry.getActiveConnectionCount());
        assertEquals(List.of("conn-unreg"), disconnectedIds);
    }

    @Test
    public void testReclaimIdleConnections() {
        List<String> disconnected = new ArrayList<>();
        registry.addLifecycleListener(new ConnectionLifecycleListener() {
            @Override
            public void onConnect(ConnectionContext context) {}

            @Override
            public void onHeartbeat(ConnectionContext context) {}

            @Override
            public void onDisconnect(ConnectionContext context) {
                disconnected.add(context.getConnectionId());
            }

            @Override
            public void onError(ConnectionContext context, Throwable cause) {}
        });

        long now = System.currentTimeMillis();
        // conn-old created 10 seconds ago
        ConnectionContext oldConn = new ConnectionContextImpl("conn-old", "TCP", "127.0.0.1:1111", null, now - 10000);
        // conn-new created just now
        ConnectionContext newConn = new ConnectionContextImpl("conn-new", "TCP", "127.0.0.1:2222", null, now);

        registry.registerConnection(oldConn);
        registry.registerConnection(newConn);
        assertEquals(2, registry.getActiveConnectionCount());

        // Reclaim with 5000ms idle threshold: oldConn should be reclaimed, newConn retained
        int reclaimed = registry.reclaimIdleConnections(5000);

        assertEquals(1, reclaimed);
        assertEquals(1, registry.getActiveConnectionCount());
        assertFalse(registry.containsConnection("conn-old"));
        assertTrue(registry.containsConnection("conn-new"));
        assertEquals(List.of("conn-old"), disconnected);
    }

    @Test
    public void testNotifyError() {
        List<Throwable> errors = new ArrayList<>();
        registry.addLifecycleListener(new ConnectionLifecycleListener() {
            @Override
            public void onConnect(ConnectionContext context) {}

            @Override
            public void onHeartbeat(ConnectionContext context) {}

            @Override
            public void onDisconnect(ConnectionContext context) {}

            @Override
            public void onError(ConnectionContext context, Throwable cause) {
                errors.add(cause);
            }
        });

        ConnectionContext ctx = new ConnectionContextImpl("conn-err", "TELNET", "127.0.0.1:23");
        registry.registerConnection(ctx);

        RuntimeException ex = new RuntimeException("socket error");
        registry.notifyError("conn-err", ex);

        assertEquals(1, errors.size());
        assertSame(ex, errors.get(0));
    }

    @Test
    public void testListenerFaultTolerance() {
        // Failing listener should not stop other listeners
        AtomicBoolean secondListenerCalled = new AtomicBoolean(false);

        registry.addLifecycleListener(new ConnectionLifecycleListener() {
            @Override
            public void onConnect(ConnectionContext context) {
                throw new RuntimeException("Simulated listener failure");
            }

            @Override
            public void onHeartbeat(ConnectionContext context) {}

            @Override
            public void onDisconnect(ConnectionContext context) {}

            @Override
            public void onError(ConnectionContext context, Throwable cause) {}
        });

        registry.addLifecycleListener(new ConnectionLifecycleListener() {
            @Override
            public void onConnect(ConnectionContext context) {
                secondListenerCalled.set(true);
            }

            @Override
            public void onHeartbeat(ConnectionContext context) {}

            @Override
            public void onDisconnect(ConnectionContext context) {}

            @Override
            public void onError(ConnectionContext context, Throwable cause) {}
        });

        ConnectionContext ctx = new ConnectionContextImpl("conn-resilient", "HTTP", "127.0.0.1:80");
        assertDoesNotThrow(() -> registry.registerConnection(ctx));
        assertTrue(secondListenerCalled.get());
    }

    @Test
    public void testConnectionContextCloseIdempotency() {
        AtomicInteger closeCount = new AtomicInteger(0);
        ConnectionContext ctx = new ConnectionContextImpl("conn-close", "TCP", "127.0.0.1:9999", closeCount::incrementAndGet);

        assertTrue(ctx.isAlive());
        ctx.close();
        assertFalse(ctx.isAlive());
        assertEquals(1, closeCount.get());

        // Second close should be no-op
        ctx.close();
        assertFalse(ctx.isAlive());
        assertEquals(1, closeCount.get());
    }

    @Test
    public void testRemoveLifecycleListener() {
        ConnectionLifecycleListener listener = new ConnectionLifecycleListener() {
            @Override public void onConnect(ConnectionContext context) {}
            @Override public void onHeartbeat(ConnectionContext context) {}
            @Override public void onDisconnect(ConnectionContext context) {}
            @Override public void onError(ConnectionContext context, Throwable cause) {}
        };

        registry.addLifecycleListener(listener);
        assertEquals(1, registry.getLifecycleListeners().size());

        registry.removeLifecycleListener(listener);
        assertEquals(0, registry.getLifecycleListeners().size());
    }

    @Test
    public void testCloseAll() {
        ConnectionContext ctx1 = new ConnectionContextImpl("c1", "TCP", "127.0.0.1:1");
        ConnectionContext ctx2 = new ConnectionContextImpl("c2", "TCP", "127.0.0.1:2");
        registry.registerConnection(ctx1);
        registry.registerConnection(ctx2);
        assertEquals(2, registry.getActiveConnectionCount());

        registry.closeAll();
        assertEquals(0, registry.getActiveConnectionCount());
        assertFalse(ctx1.isAlive());
        assertFalse(ctx2.isAlive());
    }
}
