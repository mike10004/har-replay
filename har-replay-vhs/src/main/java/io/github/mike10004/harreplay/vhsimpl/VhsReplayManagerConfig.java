package io.github.mike10004.harreplay.vhsimpl;

import java.io.File;
import java.nio.file.Path;

public class VhsReplayManagerConfig {

    public final Path mappedFileResolutionRoot;

    private VhsReplayManagerConfig() {
        mappedFileResolutionRoot = new File(System.getProperty("user.dir")).toPath();
    }

    private static final VhsReplayManagerConfig DEFAULT = new VhsReplayManagerConfig();

    public static VhsReplayManagerConfig getDefault() {
        return DEFAULT;
    }
}
