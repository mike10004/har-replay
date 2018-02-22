package io.github.mike10004.harreplay.vhsimpl;

import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.tests.ReplayManagerTester;

import java.io.File;
import java.nio.file.Path;

class VhsReplayManagerTester extends ReplayManagerTester {

    private final ReplayServerConfig config;

    public VhsReplayManagerTester(Path tempDir, File harFile) {
        this(tempDir, harFile, ReplayServerConfig.empty());
    }

    public VhsReplayManagerTester(Path tempDir, File harFile, ReplayServerConfig config) {
        super(tempDir, harFile);
        this.config = config;
    }

    @Override
    protected ReplayManager createReplayManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ReplayServerConfig configureReplayModule() {
        return config;
    }

}
