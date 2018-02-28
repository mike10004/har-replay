package io.github.mike10004.harreplay;

import com.google.common.net.HostAndPort;

import java.io.IOException;

/**
 * Interface that defines methods to interrogate and control a replay server session.
 */
public interface ReplaySessionControl extends java.io.Closeable {

    /**
     * Gets the port the proxy server is listening on.
     * @return the port
     */
    int getListeningPort();

    /**
     * Gets the socket address the proxy server is listening on
     * @return the socket address
     */
    default HostAndPort getSocketAddress() {
        return HostAndPort.fromParts("localhost", getListeningPort());
    }

    /**
     * Checks whether the server is still alive.
     * @return true iff the server is still alive
     */
    boolean isAlive();

    /**
     * Ends the session by stopping the proxy server.
     */
    void stop();

    /**
     * Invokes {@link #stop()}.
     * @throws IOException if an I/O error occurs
     */
    @Override
    default void close() throws IOException {
        stop();
    }
}
