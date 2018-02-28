package io.github.mike10004.harreplay;

import io.github.mike10004.harreplay.ReplayServerConfig.MappingMatch;
import io.github.mike10004.harreplay.ReplayServerConfig.StringLiteral;
import org.apache.commons.io.FilenameUtils;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class ReplayServerConfig_MappingPathTest {

    @Test
    public void mappingPathResolution() {
        Path root = getRoot();
        String relativePath = "a/relative/path";
        File file = StringLiteral.of(relativePath).resolveFile(root, MOCKMATCH, MOCKURL);
        assertTrue("absolute", file.isAbsolute());
        assertEquals("path", root.resolve(relativePath), file.toPath());
    }

    private static Path getRoot() {
        return new File(System.getProperty("java.io.tmpdir")).toPath();
    }

    @Test
    public void mappingPathResolution_absolutePathSpecified() {
        Path root = getRoot();
        String absolutePath = constructPlatformDependentAbsolutePath("/usr/lib/library.so");
        File absfile = StringLiteral.of(absolutePath).resolveFile(root, MOCKMATCH, MOCKURL);
        assertEquals("abs", new File(absolutePath).toPath(), absfile.toPath());
    }

    @SuppressWarnings("SameParameterValue")
    private static String constructPlatformDependentAbsolutePath(String unixNormalizedAbsPath) {
        boolean windows = org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
        if (windows) {
            File cwd = new File(System.getProperty("user.dir")).getAbsoluteFile();
            String prefix = FilenameUtils.getPrefix(cwd.getAbsolutePath());
            return FilenameUtils.normalizeNoEndSeparator(prefix + unixNormalizedAbsPath);
        } else {
            return unixNormalizedAbsPath;
        }
    }

    private static final MappingMatch MOCKMATCH = url -> {
        throw new UnsupportedOperationException();
    };

    private static final String MOCKURL = "";
}