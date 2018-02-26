package io.github.mike10004.harreplay;

import io.github.mike10004.harreplay.ReplayServerConfig.MappingMatch;
import io.github.mike10004.harreplay.ReplayServerConfig.StringLiteral;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class ReplayServerConfig_MappingPathTest {

    @Test
    public void mappingPathResolution() {
        Path root = new File(System.getProperty("java.io.tmpdir")).toPath();
        String relativePath = "a/relative/path";
        File file = StringLiteral.of(relativePath).resolveFile(root, MOCKMATCH, MOCKURL);
        assertTrue("absolute", file.isAbsolute());
        assertEquals("path", root.resolve(relativePath), file.toPath());
        String absolutePath = "/usr/lib/library.so";
        File absfile = StringLiteral.of(absolutePath).resolveFile(root, MOCKMATCH, MOCKURL);
        assertEquals("abs", new File(absolutePath).toPath(), absfile.toPath());
    }

    private static final MappingMatch MOCKMATCH = url -> {
        throw new UnsupportedOperationException();
    };

    private static final String MOCKURL = "";
}