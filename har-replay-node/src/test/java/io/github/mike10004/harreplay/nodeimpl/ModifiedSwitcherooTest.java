package io.github.mike10004.harreplay.nodeimpl;

import io.github.mike10004.harreplay.tests.ReplayManagerTester;

import java.io.File;
import java.nio.file.Path;

public class ModifiedSwitcherooTest extends ModifiedSwitcherooTestBase {

    @Override
    protected ReplayManagerTester createTester(Path tempRoot, File harFile) {
        return new NodeServerReplayManagerTester(tempRoot, harFile);
    }
}