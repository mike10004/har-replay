package com.github.mike10004.harreplay;

import java.io.File;
import java.nio.file.Path;

public class NodeServerReplayManagerTest extends com.github.mike10004.harreplay.ReplayManagerTest {

    static final String SYSPROP_NODE_EXECUTABLE = "har-replay.node.executable";

    @Override
    protected ReplayManagerTester createTester(Path tempDir, File harFile, ReplayServerConfig config) {
        return new NodeServerReplayManagerTester(tempDir, harFile, config);
    }

}
