package io.github.mike10004.harreplay.exec;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.net.HostAndPort;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public class ChromeBrowserSupport implements BrowserSupport {

    private final OutputDestination outputDestination;

    public ChromeBrowserSupport(OutputDestination outputDestination) {
        this.outputDestination = outputDestination;
    }

    public enum OutputDestination {
        FILES, CONSOLE
    }

    @Override
    public LaunchableBrowser prepare(Path scratchDir) throws IOException {
        Path userDataDir = java.nio.file.Files.createTempDirectory(scratchDir, "chrome-user-data");
        @Nullable Path outputDir = null;
        if (outputDestination == OutputDestination.FILES) {
            outputDir = java.nio.file.Files.createTempDirectory(scratchDir, "chrome-output");
        }
        return new LaunchableChrome(userDataDir, outputDir);
    }

    private static class LaunchableChrome implements LaunchableBrowser {

        private final Path userDataDir;
        @Nullable
        private final Path outputDir;

        private LaunchableChrome(Path userDataDir, @Nullable Path outputDir) {
            this.userDataDir = requireNonNull(userDataDir);
            this.outputDir = outputDir;
        }

        @Override
        public ProcessMonitor<?, ?> launch(HostAndPort replayServerAddress, Iterable<String> moreArguments, ProcessTracker processTracker) {
            Subprocess.Builder sb = runningChromeOrChromium()
                    .arg("--no-first-run")
                    .arg("--ignore-certificate-errors")
                    .arg("--proxy-server=" + replayServerAddress.toString())
                    .arg("--user-data-dir=" + userDataDir.toFile().getAbsolutePath());
            sb.arg("data:,"); // start URL
            sb.args(moreArguments);
            Subprocess subprocess = sb.build();
            Subprocess.Launcher<?, ?> launcher = subprocess.launcher(processTracker);
            if (outputDir != null) {
                launcher.outputTempFiles(outputDir);
            } else {
                launcher.inheritOutputStreams();
            }
            ProcessMonitor<?, ?> monitor = launcher.launch();
            return monitor;
        }

        // TODO detect whether chrome or chromium is installed and where
        protected Subprocess.Builder runningChromeOrChromium() {
            return Subprocess.running("google-chrome");
        }
    }

    @Override
    public String toString() {
        return "ChromeBrowserSupport{" +
                "outputDestination=" + outputDestination +
                '}';
    }
}
