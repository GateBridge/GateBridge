package hexacloud.core.server.connection;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import hexacloud.core.utils.common.DebugUtils;

/**
 * Thread-safe default implementation of {@link ConnectionContext}.
 */
public class ConnectionContextImpl implements ConnectionContext {

    private final String connectionId;
    private final String protocol;
    private final String remoteAddress;
    private final long connectedAtMs;
    private final AtomicLong lastActiveMs;
    private final AtomicBoolean alive;
    private final Runnable onClose;

    public ConnectionContextImpl(String connectionId, String protocol, String remoteAddress, Runnable onClose) {
        this(connectionId, protocol, remoteAddress, onClose, System.currentTimeMillis());
    }

    public ConnectionContextImpl(String connectionId, String protocol, String remoteAddress) {
        this(connectionId, protocol, remoteAddress, null, System.currentTimeMillis());
    }

    public ConnectionContextImpl(String connectionId, String protocol, String remoteAddress, Runnable onClose, long connectedAtMs) {
        this.connectionId = Objects.requireNonNull(connectionId, "connectionId must not be null");
        this.protocol = protocol != null ? protocol : "UNKNOWN";
        this.remoteAddress = remoteAddress != null ? remoteAddress : "";
        this.onClose = onClose;
        this.connectedAtMs = connectedAtMs;
        this.lastActiveMs = new AtomicLong(connectedAtMs);
        this.alive = new AtomicBoolean(true);
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public String getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public long getConnectedAtMs() {
        return connectedAtMs;
    }

    @Override
    public long getLastActiveMs() {
        return lastActiveMs.get();
    }

    @Override
    public boolean isAlive() {
        return alive.get();
    }

    @Override
    public void touch() {
        if (alive.get()) {
            lastActiveMs.set(System.currentTimeMillis());
        }
    }

    @Override
    public void close() {
        if (alive.compareAndSet(true, false)) {
            if (onClose != null) {
                try {
                    onClose.run();
                } catch (Throwable t) {
                    DebugUtils.error("Error executing onClose callback for connection " + connectionId, t);
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectionContext)) return false;
        ConnectionContext that = (ConnectionContext) o;
        return Objects.equals(connectionId, that.getConnectionId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId);
    }

    @Override
    public String toString() {
        return "ConnectionContext{" +
                "id='" + connectionId + '\'' +
                ", protocol='" + protocol + '\'' +
                ", remoteAddress='" + remoteAddress + '\'' +
                ", connectedAtMs=" + connectedAtMs +
                ", lastActiveMs=" + getLastActiveMs() +
                ", alive=" + isAlive() +
                '}';
    }
}
