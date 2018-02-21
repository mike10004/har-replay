package io.github.mike10004.harreplay.nodeimpl;

import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.tests.ReplayManagerTest;
import io.github.mike10004.harreplay.tests.ReplayManagerTester;

import java.io.File;
import java.nio.file.Path;

public class NodeServerReplayManagerTest extends ReplayManagerTest {

    static final String SYSPROP_NODE_EXECUTABLE = "har-replay.node.executable";

    @Override
    protected ReplayManagerTester createTester(Path tempDir, File harFile, ReplayServerConfig config) {
        return new NodeServerReplayManagerTester(tempDir, harFile, config);
    }

}
