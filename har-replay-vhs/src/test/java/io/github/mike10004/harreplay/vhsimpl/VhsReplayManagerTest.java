package io.github.mike10004.harreplay.vhsimpl;

import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.tests.ReplayManagerTestBase;
import io.github.mike10004.harreplay.tests.ReplayManagerTester;

import java.io.File;
import java.nio.file.Path;

public class VhsReplayManagerTest extends ReplayManagerTestBase {

    private static final String SYSPROP_RESERVED_PORT = "har-replay.unit-tests.reservedPort";

    @Override
    protected ReplayManagerTester createTester(Path tempDir, File harFile, ReplayServerConfig config) {
        return new VhsReplayManagerTester(tempDir, harFile, config);
    }

    @Override
    protected String getReservedPortSystemPropertyName() {
        return SYSPROP_RESERVED_PORT;
    }
}
