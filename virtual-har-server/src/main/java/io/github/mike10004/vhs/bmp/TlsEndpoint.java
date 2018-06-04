package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import net.lightbody.bmp.mitm.TrustSource;

import java.io.IOException;

/**
 * Interface that provides access to the socket address where TLS
 * connections are to be forwarded.
 */
public interface TlsEndpoint extends java.io.Closeable {

    /**
     * Gets the socket address.
     * @return the socket address
     */
    HostAndPort getSocketAddress();

    /**
     * Gets the trust source the proxy can use so that the "remote" connection
     * handshake succeeds.
     * @return the trust source
     */
    TrustSource getTrustSource();

    static TlsEndpoint createDefault() throws IOException {
        return new BrokenTlsEndpoint();
    }
}
