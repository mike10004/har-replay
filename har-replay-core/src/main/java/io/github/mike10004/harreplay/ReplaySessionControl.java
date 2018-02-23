package io.github.mike10004.harreplay;

import com.google.common.net.HostAndPort;

import java.io.IOException;

public interface ReplaySessionControl extends java.io.Closeable {

    int getListeningPort();
    default HostAndPort getSocketAddress() {
        return HostAndPort.fromParts("localhost", getListeningPort());
    }
    boolean isAlive();
    void stop();

    @Override
    default void close() throws IOException {
        stop();
    }
}
