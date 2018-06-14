package io.github.mike10004.harreplay.dist;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;

public class RequiredFilesInDebTest {

    @Test
    public void checkBinScript() throws Exception {
        checkFileExists("usr/bin/har-replay");
    }

    @Test
    public void checkClasspathArgFile() throws Exception {
        checkFileExists("usr/share/har-replay/classpath-arg.txt");
    }

    private void checkFileExists(String relativePath) {
        File file = DistTests.getBuildDir().toPath().resolve("deb").resolve(relativePath).toFile();
        assertTrue("exists: " + file, file.isFile());
    }
}
