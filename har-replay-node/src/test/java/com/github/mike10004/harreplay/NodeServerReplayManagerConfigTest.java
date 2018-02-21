package com.github.mike10004.harreplay;

import com.github.mike10004.harreplay.NodeServerReplayManagerConfig.EmbeddedClientDirProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

public class NodeServerReplayManagerConfigTest {

    private static final boolean verbose = false;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();


    @Test
    public void auto() throws Exception {
        Path serverReplayDir = NodeServerReplayManagerConfig.auto().harReplayProxyDirProvider.provide(temporaryFolder.getRoot().toPath());
        Collection<File> files = FileUtils.listFiles(serverReplayDir.toFile(), null, true);
        for (File f : files) {
            if (verbose) System.out.println(f);
        }
        String[] requiredRelativePaths = {"cli.js", "index.js", "parse-config.js", "node_modules/yargs/index.js"};
        for (String requiredPath : requiredRelativePaths) {
            File file = serverReplayDir.resolve(requiredPath).toFile();
            if (!file.isFile() || file.length() <= 0) {
                System.out.format("not found or empty: %s%n", file);
            }
            assertTrue("not a file: " + file, file.isFile());
            assertTrue("empty: " + file, file.length() > 0);
        }
    }

    public static class EmbeddedClientDirProviderTest {
        @Test
        public void zipContainsRequiredFiles() throws Exception {
            URL zipResource = ((EmbeddedClientDirProvider)EmbeddedClientDirProvider.getInstance()).getZipResource();
            assertNotNull(zipResource);
            File file = new File(zipResource.toURI());
            List<ZipEntry> entries;
            try (ZipFile zipFile = new ZipFile(file)) {
                entries = ImmutableList.copyOf(Iterators.forEnumeration(zipFile.entries()));
            }
            String[] requiredEntryNames = {
                    "cli.js",
                    "node_modules/yargs/index.js"
            };
            Stream.of(requiredEntryNames).map(name -> EmbeddedClientDirProvider.ZIP_ROOT + "/" + name).forEach(required -> {
                Optional<String> found = entries.stream().map(ZipEntry::getName).filter(required::equals).findFirst();
                assertTrue("not found: " + required, found.isPresent());
            });
        }
    }
}