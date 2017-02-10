package com.github.mike10004.harreplay;

import java.nio.file.Path;

public class ReplayManagerConfig {

    public final Path serverReplayDir;

    public ReplayManagerConfig(Path serverReplayDir) {
        this.serverReplayDir = serverReplayDir;
    }
}
