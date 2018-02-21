package io.github.mike10004.harreplay;

import java.io.IOException;

/**
 * Interface that represents a manager of a server replay process.
 */
public interface ReplayManager {

    ReplaySessionControl start(ReplaySessionConfig sessionConfig) throws IOException;
}
