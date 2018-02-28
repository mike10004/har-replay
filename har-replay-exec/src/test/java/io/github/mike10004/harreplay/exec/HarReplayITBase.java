package io.github.mike10004.harreplay.exec;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.util.Arrays;

public class HarReplayITBase {

    private static boolean listedDirectoryAlready = false;

    protected File findJarFile() throws FileNotFoundException {
        String artifactId = ExecTests.getTestProperty("project.artifactId");
        String version = ExecTests.getTestProperty("project.version");
        String filename = String.format("%s-%s-jar-with-dependencies.jar", artifactId, version);
        File targetDir = new File(ExecTests.getTestProperty("project.build.directory"));
        File jarFile = new File(targetDir, filename);
        if (!jarFile.isFile()) {
            if (!listedDirectoryAlready) {
                System.out.println("target: " + targetDir);
                FileUtils.listFiles(targetDir, null, false).forEach(System.out::println);
                listedDirectoryAlready = true;
            }
            throw new FileNotFoundException(jarFile.getAbsolutePath());
        }
        return jarFile;
    }

    protected ProcessMonitor<String, String> execute(ProcessTracker processTracker, String...args) throws FileNotFoundException {
        return execute(processTracker, Arrays.asList(args));
    }

    protected ProcessMonitor<String, String> execute(ProcessTracker processTracker, Iterable<String> args) throws FileNotFoundException {
        File jarFile = findJarFile();
        Subprocess subprocess = Subprocess.running("java")
                .args("-jar", jarFile.getAbsolutePath())
                .args(args)
                .build();
        return subprocess.launcher(processTracker)
                .outputStrings(Charset.defaultCharset())
                .launch();
    }
}
