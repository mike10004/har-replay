package io.github.mike10004.harreplay.exec;

import io.github.mike10004.harreplay.nodeimpl.ModifiedSwitcheroo;
import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import io.github.mike10004.crxtool.CrxParser;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public class ChromeBrowserSupport implements BrowserSupport {

    private final SwitcherooMode switcherooMode;
    private final OutputDestination outputDestination;

    public ChromeBrowserSupport(SwitcherooMode switcherooMode, OutputDestination outputDestination) {
        this.switcherooMode = switcherooMode;
        this.outputDestination = outputDestination;
    }

    public enum SwitcherooMode {
        NOT_ADDED, ENABLED
    }

    public enum OutputDestination {
        FILES, CONSOLE
    }

    @Override
    public LaunchableBrowser prepare(Path scratchDir) throws IOException {
        Path userDataDir = java.nio.file.Files.createTempDirectory(scratchDir, "chrome-user-data");
        File modifiedSwitcherooFile = File.createTempFile("modified-switcheroo", ".crx", scratchDir.toFile());
        ModifiedSwitcheroo.getExtensionCrxByteSource().copyTo(Files.asByteSink(modifiedSwitcherooFile));
        @Nullable Path modifiedSwitcherooUnpackDir = null;
        if (switcherooMode == SwitcherooMode.ENABLED) {
            modifiedSwitcherooUnpackDir = java.nio.file.Files.createTempDirectory(scratchDir, "modified-switcheroo");
            try (InputStream crxInputStream = new FileInputStream(modifiedSwitcherooFile)) {
                CrxParser.getDefault().parseMetadata(crxInputStream);
                Unzippage unzippage = Unzippage.unzip(crxInputStream);
                unzippage.copyTo(modifiedSwitcherooUnpackDir);
            }
        }
        @Nullable Path outputDir = null;
        if (outputDestination == OutputDestination.FILES) {
            outputDir = java.nio.file.Files.createTempDirectory(scratchDir, "chrome-output");
        }
        return new LaunchableChrome(userDataDir, modifiedSwitcherooUnpackDir, outputDir);
    }

    private static class LaunchableChrome implements LaunchableBrowser {

        private final Path userDataDir;
        @Nullable
        private final Path unpackedModifiedSwitcherooCrxDir;
        @Nullable
        private final Path outputDir;

        private LaunchableChrome(Path userDataDir, @Nullable Path unpackedModifiedSwitcherooCrxDir, @Nullable Path outputDir) {
            this.userDataDir = requireNonNull(userDataDir);
            this.unpackedModifiedSwitcherooCrxDir = unpackedModifiedSwitcherooCrxDir;
            this.outputDir = outputDir;
        }

        @Override
        public ProcessMonitor<?, ?> launch(HostAndPort replayServerAddress, ProcessTracker processTracker) {
            Subprocess.Builder sb = runningChromeOrChromium()
                    .arg("--no-first-run")
                    .arg("--ignore-certificate-errors")
                    .arg("--proxy-server=" + replayServerAddress.toString())
                    .arg("--user-data-dir=" + userDataDir.toFile().getAbsolutePath());
            if (unpackedModifiedSwitcherooCrxDir != null) {
                sb.arg("--load-extension=" + unpackedModifiedSwitcherooCrxDir.toFile().getAbsolutePath());
            }
            sb.arg("data:,"); // start URL
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


}
