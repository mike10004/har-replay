package io.github.mike10004.harreplay.nodeimpl;

import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.tests.ReplayManagerTestBase;
import io.github.mike10004.harreplay.tests.ReplayManagerTester;

import java.io.File;
import java.nio.file.Path;

public class NodeServerReplayManagerTest extends ReplayManagerTestBase {

    static final String SYSPROP_NODE_EXECUTABLE = "har-replay.node.executable";
    static final String SYSPROP_SERVER_REPLAY_HTTP_PORT = "server-replay.port"; // see pom.xml build-helper-plugin
    static final String SYSPROP_SERVER_READINESS_TIMEOUT_MILLIS = "hr.node.serverReadinessTimeoutMillis";

    @Override
    protected ReplayManagerTester createTester(Path tempDir, File harFile, ReplayServerConfig config) {
        return new NodeServerReplayManagerTester(tempDir, harFile, config);
    }

    @Override
    protected String getReservedPortSystemPropertyName() {
        return SYSPROP_SERVER_REPLAY_HTTP_PORT;
    }

}
