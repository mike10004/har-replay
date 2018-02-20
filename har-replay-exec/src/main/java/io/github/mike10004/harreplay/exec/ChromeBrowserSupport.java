package io.github.mike10004.harreplay.exec;

import com.github.mike10004.harreplay.ModifiedSwitcheroo;
import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import io.github.mike10004.crxtool.CrxMetadata;
import io.github.mike10004.crxtool.CrxParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class ChromeBrowserSupport implements BrowserSupport {
    @Override
    public LaunchableBrowser prepare(Path scratchDir) throws IOException {
        Path userDataDir = java.nio.file.Files.createTempDirectory(scratchDir, "chrome-user-data");
        File modifiedSwitcherooFile = File.createTempFile("modified-switcheroo", ".crx", scratchDir.toFile());
        ModifiedSwitcheroo.getExtensionCrxByteSource().copyTo(Files.asByteSink(modifiedSwitcherooFile));
        File modifiedSwitcherooUnpackDir = java.nio.file.Files.createTempDirectory(scratchDir, "modified-switcheroo").toFile();
        try (InputStream crxInputStream = new FileInputStream(modifiedSwitcherooFile)) {
            CrxMetadata crxMetadata = CrxParser.getDefault().parseMetadata(crxInputStream);
            Unzippage unzippage = Unzippage.unzip(crxInputStream);
            unzippage.copyTo(modifiedSwitcherooUnpackDir.toPath());
        }
        return new LaunchableChrome(userDataDir, modifiedSwitcherooUnpackDir);
    }

    private static class LaunchableChrome implements LaunchableBrowser {

        private final Path userDataDir;
        private final File modifiedSwitcherooCrxFile;

        private LaunchableChrome(Path userDataDir, File modifiedSwitcherooCrxFile) {
            this.userDataDir = userDataDir;
            this.modifiedSwitcherooCrxFile = modifiedSwitcherooCrxFile;
        }

        @Override
        public ProcessMonitor<?, ?> launch(HostAndPort replayServerAddress, ProcessTracker processTracker) {
            Subprocess subprocess = runningChromeOrChromium()
                    .arg("--no-first-run")
                    .arg("--proxy-server=" + replayServerAddress.toString())
                    .arg("--user-data-dir=" + userDataDir.toFile().getAbsolutePath())
                    .arg("--load-extension=" + modifiedSwitcherooCrxFile.getAbsolutePath())
                    .arg("data:,")
                    .build();
            ProcessMonitor<?, ?> monitor = subprocess.launcher(processTracker)
                    .inheritOutputStreams()
                    .launch();
            return monitor;
        }

        protected Subprocess.Builder runningChromeOrChromium() {
            return Subprocess.running("google-chrome");
        }
    }


}
