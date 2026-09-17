package hexacloud.core.server.connection;

/**
 * Represents the context and state of an active client network connection.
 */
public interface ConnectionContext {

    /**
     * Unique identifier for this connection.
     *
     * @return unique connection ID
     */
    String getConnectionId();

    /**
     * Transport protocol name (e.g. "TCP", "HTTP", "WS", "TELNET").
     *
     * @return protocol name
     */
    String getProtocol();

    /**
     * Remote peer address (e.g. "127.0.0.1:54321").
     *
     * @return remote address
     */
    String getRemoteAddress();

    /**
     * Epoch timestamp in milliseconds when connection was established.
     *
     * @return connection timestamp in milliseconds
     */
    long getConnectedAtMs();

    /**
     * Epoch timestamp in milliseconds of last active traffic or heartbeat.
     *
     * @return last active timestamp in milliseconds
     */
    long getLastActiveMs();

    /**
     * Whether the connection is currently alive and active.
     *
     * @return true if alive, false if closed
     */
    boolean isAlive();

    /**
     * Updates the last active timestamp to current time.
     */
    void touch();

    /**
     * Closes the connection and invokes any registered close actions.
     */
    void close();
}
