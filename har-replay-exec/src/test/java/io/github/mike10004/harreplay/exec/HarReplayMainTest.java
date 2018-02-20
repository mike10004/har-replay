package io.github.mike10004.harreplay.exec;

import static org.junit.Assert.*;

public class HarReplayMainTest {

    @org.junit.Test
    public void main0() throws Exception {
        assertEquals("exit code", 1, new HarReplayMain().main0(new String[]{}));
    }
}