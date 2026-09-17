package hexacloud.core.server.connection;

/**
 * Listener interface for observing connection lifecycle events across server transports.
 */
public interface ConnectionLifecycleListener {

    /**
     * Invoked when a new connection is registered.
     *
     * @param context the connection context
     */
    void onConnect(ConnectionContext context);

    /**
     * Invoked when a connection receives heartbeat or active traffic.
     *
     * @param context the connection context
     */
    void onHeartbeat(ConnectionContext context);

    /**
     * Invoked when a connection disconnects or is unregistered.
     *
     * @param context the connection context
     */
    void onDisconnect(ConnectionContext context);

    /**
     * Invoked when an error occurs on the connection.
     *
     * @param context the connection context
     * @param cause the error cause
     */
    void onError(ConnectionContext context, Throwable cause);
}
