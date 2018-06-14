package io.github.mike10004.harreplay.dist;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;

public class ExecScriptIT {

    @Test
    public void checkDeb() throws Exception {
        File debFile = Tests.getDebFile();
        System.out.format("%s (%d bytes)%n", debFile, debFile.length());
        assertTrue(debFile.length() > 0);
    }

}
