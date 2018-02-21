package com.github.mike10004.harreplay;

public interface ReplaySessionControl extends java.io.Closeable {
    boolean isAlive();
    void stop();
}
