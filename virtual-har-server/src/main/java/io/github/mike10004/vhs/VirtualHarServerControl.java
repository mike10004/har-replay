package io.github.mike10004.vhs;

import com.google.common.net.HostAndPort;

/**
 * Interface that represents a controller of a virtual HAR server that has been started.
 */
public interface VirtualHarServerControl extends java.io.Closeable {

    /**
     * Gets the socket address the server is listening on.
     * @return the socket address
     */
    HostAndPort getSocketAddress();

}
