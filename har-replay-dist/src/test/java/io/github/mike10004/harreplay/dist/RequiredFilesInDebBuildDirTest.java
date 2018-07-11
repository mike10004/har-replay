package io.github.mike10004.harreplay.dist;

import com.google.common.base.Splitter;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.tika.io.FilenameUtils;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RequiredFilesInDebBuildDirTest {

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

    @Test
    public void filesInLibHaveUniqueGroupIdArtifactIdCouplets() throws Exception {
        Path debDir = DistTests.getBuildDir().toPath().resolve("deb");
        File cpArgFile = debDir.resolve("usr/share/har-replay/classpath-arg.txt").toFile();
        Path libDir = debDir.resolve("usr/share/har-replay/lib");
        String argTxt = Files.asCharSource(cpArgFile, UTF_8).read();
        List<String> libfiles = Splitter.on(File.pathSeparator).trimResults().omitEmptyStrings().splitToList(argTxt);
        assertFalse("classpath arg empty", libfiles.isEmpty());
        List<IOFileFilter> uniqueFilters = Arrays.asList(
                new WildcardFileFilter("har-replay-core-*.jar"),
                new WildcardFileFilter("har-replay-exec-*.jar"),
                new WildcardFileFilter("virtual-har-server-*.jar")
        );
        List<File> libFiles = new ArrayList<>();
        for (String pathname : libfiles) {
            String filename = FilenameUtils.getName(pathname);
            File libFile = libDir.resolve(filename).toFile().getAbsoluteFile();
            long length = libFile.length();
            System.out.format("%s (%s)%n", libFile, length);
            assertTrue("exists: " + libFile, libFile.isFile());
            libFiles.add(libFile);
        }
        uniqueFilters.forEach(filter -> {
            List<File> matching = libFiles.stream().filter(file -> {
                return filter.accept(file.getParentFile(), file.getName());
            }).collect(Collectors.toList());
            if (matching.size() != 1) {
                fail("incorrect number of matching lib files: " + matching.stream().map(File::getName).collect(Collectors.toList()));
            }
        });

    }
}
