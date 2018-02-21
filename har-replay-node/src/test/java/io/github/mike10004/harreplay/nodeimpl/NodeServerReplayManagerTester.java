package io.github.mike10004.harreplay.nodeimpl;

import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.tests.ReplayManagerTester;

import java.io.File;
import java.nio.file.Path;

class NodeServerReplayManagerTester extends ReplayManagerTester {

    private final ReplayServerConfig config;

    public NodeServerReplayManagerTester(Path tempDir, File harFile) {
        this(tempDir, harFile, ReplayServerConfig.empty());
    }

    public NodeServerReplayManagerTester(Path tempDir, File harFile, ReplayServerConfig config) {
        super(tempDir, harFile);
        this.config = config;
    }

    @Override
    protected ReplayManager createReplayManager() {
        return new NodeServerReplayManager(createReplayManagerConfigBuilder().build());
    }

    @Override
    protected ReplayServerConfig configureReplayModule() {
        return config;
    }

    private NodeServerReplayManagerConfig.Builder createReplayManagerConfigBuilder() {
        // NodeServerReplayManagerConfig replayManagerConfig = createReplayManagerConfigBuilder().build();
        NodeServerReplayManagerConfig.Builder builder = NodeServerReplayManagerConfig.builder()
                .addOutputEchoes();
        String nodeExecutablePath = System.getProperty(NodeServerReplayManagerTest.SYSPROP_NODE_EXECUTABLE, "");
        if (!nodeExecutablePath.isEmpty()) {
            builder.nodeExecutable(new File(nodeExecutablePath));
        }
        return builder;
    }

}
