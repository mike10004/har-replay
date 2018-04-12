package io.github.mike10004.harreplay.nodeimpl;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.Files;
import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.tests.ReplayManagerTester;
import io.github.mike10004.harreplay.tests.Tests;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.charset.Charset;
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
                .addOutputEchoes()
                .readinessCheckEcho((line, signal) -> {
                    System.out.format("readiness check %s: %s%n", signal, StringUtils.abbreviate(line, 64));
                });
        String nodeExecutablePath = System.getProperty(NodeServerReplayManagerTest.SYSPROP_NODE_EXECUTABLE, "");
        if (!nodeExecutablePath.isEmpty()) {
            builder.nodeExecutable(new File(nodeExecutablePath));
        }
        String timeoutStr = System.getProperty(NodeServerReplayManagerTest.SYSPROP_SERVER_READINESS_TIMEOUT_MILLIS);
        if (timeoutStr != null) {
            builder.serverReadinessPolling(Integer.parseInt(timeoutStr), NodeServerReplayManagerConfig.DEFAULT_SERVER_READINESS_POLL_INTERVAL_MILLIS);
        }
        return builder;
    }

    @Override
    protected void exerciseAbortedAbnormally(Exception e) {
        if (e instanceof NodeServerReplayManager.ServerFailedToStartException) {
            NodeServerReplayManager.ServerFailedToStartException sfse = (NodeServerReplayManager.ServerFailedToStartException) e;
            Tests.dump(ImmutableMultimap.of("stdout", Files.asCharSource(sfse.stdoutFile, Charset.defaultCharset()),
                    "stderr", Files.asCharSource(sfse.stderrFile, Charset.defaultCharset())), System.out);
        }
    }
}
