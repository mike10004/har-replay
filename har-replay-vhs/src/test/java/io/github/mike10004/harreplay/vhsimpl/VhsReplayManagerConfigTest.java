package io.github.mike10004.harreplay.vhsimpl;

import org.junit.Test;

import static org.junit.Assert.*;

public class VhsReplayManagerConfigTest {

    @Test
    public void getDefault() {
        // this is not a great test, but we have to link up the tests we have for reading HARs with the actual calls to construct HarReader instances
        assertTrue(VhsReplayManagerConfig.getDefault().harReaderFactory instanceof EasierHarReaderFactory);
    }
}