package hexacloud.core.server.connection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import hexacloud.core.utils.common.DebugUtils;

/**
 * Thread-safe registry for managing active transport connections and notifying lifecycle listeners.
 */
public class ConnectionRegistry {

    private final ConcurrentMap<String, ConnectionContext> connections = new ConcurrentHashMap<>();
    private final List<ConnectionLifecycleListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Adds a connection lifecycle listener.
     *
     * @param listener the lifecycle listener to add
     */
    public void addLifecycleListener(ConnectionLifecycleListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a connection lifecycle listener.
     *
     * @param listener the lifecycle listener to remove
     */
    public void removeLifecycleListener(ConnectionLifecycleListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Gets all registered lifecycle listeners.
     *
     * @return unmodifiable list of lifecycle listeners
     */
    public List<ConnectionLifecycleListener> getLifecycleListeners() {
        return Collections.unmodifiableList(listeners);
    }

    /**
     * Registers a new connection and dispatches {@link ConnectionLifecycleListener#onConnect(ConnectionContext)}.
     *
     * @param context the connection context to register
     */
    public void registerConnection(ConnectionContext context) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(context.getConnectionId(), "connectionId must not be null");

        connections.put(context.getConnectionId(), context);

        for (ConnectionLifecycleListener listener : listeners) {
            try {
                listener.onConnect(context);
            } catch (Throwable t) {
                DebugUtils.error("Error dispatching onConnect for connection " + context.getConnectionId(), t);
            }
        }
    }

    /**
     * Unregisters a connection by its ID, closes it, and dispatches {@link ConnectionLifecycleListener#onDisconnect(ConnectionContext)}.
     *
     * @param connectionId the ID of the connection to unregister
     * @return the removed connection context, or {@code null} if not found
     */
    public ConnectionContext unregisterConnection(String connectionId) {
        if (connectionId == null) {
            return null;
        }

        ConnectionContext removed = connections.remove(connectionId);
        if (removed != null) {
            try {
                removed.close();
            } catch (Throwable t) {
                DebugUtils.error("Error closing connection " + connectionId, t);
            }

            for (ConnectionLifecycleListener listener : listeners) {
                try {
                    listener.onDisconnect(removed);
                } catch (Throwable t) {
                    DebugUtils.error("Error dispatching onDisconnect for connection " + connectionId, t);
                }
            }
        }
        return removed;
    }

    /**
     * Unregisters a connection, closes it, and dispatches {@link ConnectionLifecycleListener#onDisconnect(ConnectionContext)}.
     *
     * @param context the connection context to unregister
     * @return the removed connection context, or {@code null} if not found
     */
    public ConnectionContext unregisterConnection(ConnectionContext context) {
        return context != null ? unregisterConnection(context.getConnectionId()) : null;
    }

    /**
     * Touches an active connection to refresh its last active timestamp and dispatches {@link ConnectionLifecycleListener#onHeartbeat(ConnectionContext)}.
     *
     * @param connectionId the ID of the connection to touch
     */
    public void touchConnection(String connectionId) {
        if (connectionId == null) {
            return;
        }

        ConnectionContext ctx = connections.get(connectionId);
        if (ctx != null) {
            ctx.touch();
            for (ConnectionLifecycleListener listener : listeners) {
                try {
                    listener.onHeartbeat(ctx);
                } catch (Throwable t) {
                    DebugUtils.error("Error dispatching onHeartbeat for connection " + connectionId, t);
                }
            }
        }
    }

    /**
     * Touches an active connection to refresh its last active timestamp and dispatches {@link ConnectionLifecycleListener#onHeartbeat(ConnectionContext)}.
     *
     * @param context the connection context to touch
     */
    public void touchConnection(ConnectionContext context) {
        if (context != null) {
            touchConnection(context.getConnectionId());
        }
    }

    /**
     * Dispatches an error notification for a connection by ID.
     *
     * @param connectionId the ID of the connection
     * @param cause the error cause
     */
    public void notifyError(String connectionId, Throwable cause) {
        if (connectionId == null) {
            return;
        }
        ConnectionContext ctx = connections.get(connectionId);
        if (ctx != null) {
            notifyError(ctx, cause);
        }
    }

    /**
     * Dispatches an error notification for a connection to all registered listeners.
     *
     * @param context the connection context
     * @param cause the error cause
     */
    public void notifyError(ConnectionContext context, Throwable cause) {
        if (context == null) {
            return;
        }
        for (ConnectionLifecycleListener listener : listeners) {
            try {
                listener.onError(context, cause);
            } catch (Throwable t) {
                DebugUtils.error("Error dispatching onError for connection " + context.getConnectionId(), t);
            }
        }
    }

    /**
     * Reclaims connections that have been idle for longer than the specified timeout.
     *
     * @param timeoutMs maximum allowed idle time in milliseconds
     * @return count of reclaimed connections
     */
    public int reclaimIdleConnections(long timeoutMs) {
        if (timeoutMs <= 0) {
            return 0;
        }

        long now = System.currentTimeMillis();
        List<String> idleIds = new ArrayList<>();
        for (ConnectionContext ctx : connections.values()) {
            if (now - ctx.getLastActiveMs() >= timeoutMs) {
                idleIds.add(ctx.getConnectionId());
            }
        }

        int reclaimed = 0;
        for (String id : idleIds) {
            if (unregisterConnection(id) != null) {
                reclaimed++;
            }
        }
        return reclaimed;
    }

    /**
     * Gets a connection context by its ID.
     *
     * @param connectionId the connection ID
     * @return the connection context, or {@code null} if not found
     */
    public ConnectionContext getConnection(String connectionId) {
        return connections.get(connectionId);
    }

    /**
     * Checks if a connection ID is currently registered.
     *
     * @param connectionId the connection ID
     * @return true if registered, false otherwise
     */
    public boolean containsConnection(String connectionId) {
        return connectionId != null && connections.containsKey(connectionId);
    }

    /**
     * Returns an unmodifiable view of all registered connections.
     *
     * @return collection of active connection contexts
     */
    public Collection<ConnectionContext> getAllConnections() {
        return Collections.unmodifiableCollection(connections.values());
    }

    /**
     * Gets the total number of active registered connections.
     *
     * @return active connection count
     */
    public int getActiveConnectionCount() {
        return connections.size();
    }

    /**
     * Closes and unregisters all active connections.
     */
    public void closeAll() {
        List<String> ids = new ArrayList<>(connections.keySet());
        for (String id : ids) {
            unregisterConnection(id);
        }
    }
}
