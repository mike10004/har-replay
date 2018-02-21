package io.github.mike10004.harreplay.tests;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;

import static org.junit.Assert.*;

public class HarExploderTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void explode() throws Exception {
        File harFile = Fixtures.inDirectory(temporaryFolder.getRoot().toPath()).http().harFile();
        Path outputRoot = temporaryFolder.newFolder().toPath();
        new HarExploder().explode(Files.asCharSource(harFile, StandardCharsets.UTF_8), outputRoot);
        Collection<File> exploded = FileUtils.listFiles(outputRoot.toFile(), null, true);
        exploded.forEach(System.out::println);
        assertEquals("num files", 2 * 2, exploded.size());
    }
}