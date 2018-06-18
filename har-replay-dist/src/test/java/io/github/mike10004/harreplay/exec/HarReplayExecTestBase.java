package io.github.mike10004.harreplay.exec;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import io.github.mike10004.harreplay.dist.DistTests;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class HarReplayExecTestBase {

    private String buildClasspathArg(File libsDir) {
        Collection<File> libFiles = FileUtils.listFiles(libsDir, new String[]{"jar"}, false);
        String classpathArg = libFiles.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
        return classpathArg;
    }

    private String constructFilename() {
        String artifactId = "har-replay-exec";
        String version = DistTests.getTestProperty("project.version");
        String filename = String.format("%s-%s.jar", artifactId, version);
        return filename;
    }

    protected Subprocess.Builder buildSubprocess() throws FileNotFoundException {
        File targetDir = new File(DistTests.getTestProperty("project.build.directory"));
        File libsDir = targetDir.toPath().resolve("deb/usr/share/har-replay/lib").toFile();
        String classpathArg = buildClasspathArg(libsDir);
        File jarFile = new File(libsDir, constructFilename());
        if (!jarFile.isFile()) {
            System.out.format("target: %s%n", targetDir);
            FileUtils.listFiles(targetDir, null, false).forEach(System.out::println);
            throw new FileNotFoundException(jarFile.getAbsolutePath());
        }
        return Subprocess.running("java")
                .args("-classpath", classpathArg)
                .arg(HarReplayMain.class.getName());
    }

    protected ProcessMonitor<String, String> execute(ProcessTracker processTracker, String...args) throws FileNotFoundException {
        return execute(processTracker, Arrays.asList(args));
    }

    protected ProcessMonitor<String, String> execute(ProcessTracker processTracker, Iterable<String> args) throws FileNotFoundException {
        Subprocess subprocess = buildSubprocess()
                .args(args)
                .build();
        return subprocess.launcher(processTracker)
                .outputStrings(Charset.defaultCharset())
                .launch();
    }
}
