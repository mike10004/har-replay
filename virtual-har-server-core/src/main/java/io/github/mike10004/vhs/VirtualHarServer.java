package io.github.mike10004.vhs;

import java.io.IOException;

/**
 * Interface that represents a server that replays responses from a HAR.
 */
public interface VirtualHarServer {

    /**
     * Starts the server.
     * @return the server control
     * @throws IOException on I/O error, such as an occupied port
     */
    VirtualHarServerControl start() throws IOException;

}
