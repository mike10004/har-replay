package io.github.mike10004.harreplay;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ServerSocket;

/**
 * Static utility methods relating to replay sessions.
 */
public class ReplaySessions {

    private ReplaySessions() {}

    /**
     * Gets the port specified in the config or finds an open port. Be careful with this; if the config
     * did not have an explicit port, the newly found port is not subsequently associated with the config
     * object, so calling this again will return a different new port value. Once a replay server is started,
     * you can obtain the port value with {@link ReplaySessionControl#getListeningPort()}.
     * @param config the session config
     * @return the port value
     * @throws IOException on I/O error
     */
    public static int getPortOrFindOpenPort(ReplaySessionConfig config) throws IOException {
        return getPortOrFindOpenPort(config.getPort());
    }

    public static int getPortOrFindOpenPort(@Nullable Integer port) throws IOException {
        if (port != null) {
            return port;
        }
        return findOpenPort();
    }

    /**
     * Finds an open port. A server socket will be opened on some port and then closed, so
     * this presents a race condition, because by the time you try to listen on the returned
     * port, value, somebody else could already be occupying it.
     * @return the number of a port on localhost (that was open very recently)
     * @throws IOException if opening the socket threw an exception (probably because all ports were occupied)
     */
    public static int findOpenPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
