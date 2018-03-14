package io.github.mike10004.harreplay;

import java.io.IOException;

/**
 * Interface that represents a manager of a server replay process.
 */
public interface ReplayManager {

    /**
     * Starts a server replay session.
     * @param sessionConfig the configuration
     * @return a session control instance
     * @throws IOException if starting fails due to I/O error
     */
    ReplaySessionControl start(ReplaySessionConfig sessionConfig) throws IOException;

}
