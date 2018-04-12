package io.github.mike10004.harreplay.nodeimpl;

import com.google.common.net.HostAndPort;
import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.tests.BrowseHarWithChromeExample;
import io.github.mike10004.harreplay.tests.ReadmeExample;
import org.openqa.selenium.chrome.ChromeOptions;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public class Examples {

    public static class NodeBrowseHarWithApacheHttpClientExample extends ReadmeExample {
        @Override
        protected ReplayManager createReplayManager() {
            return doCreateReplayManager();
        }

        public static void main(String[] args) throws Exception {
            File harFile = new File(ReadmeExample.class.getResource("/http.www.example.com.har").toURI());
            new NodeBrowseHarWithApacheHttpClientExample().execute(harFile);
        }
    }

    public static class NodeBrowseHarWithChromeExample extends BrowseHarWithChromeExample {
        @Override
        protected ReplayManager createReplayManager() {
            return doCreateReplayManager();
        }

        @Override
        protected ChromeOptions createChromeOptions(Path tempDir, HostAndPort proxy) throws IOException {
            return ModifiedSwitcherooTestBase.withSwitcheroo(tempDir).produceOptions(proxy);
        }

        public static void main(String[] args) throws Exception {
            File defaultFile = new File(ReadmeExample.class.getResource("/https.www.example.com.har").toURI());
            File startDir = defaultFile.getParentFile();
            AtomicReference<File> selectedFileHolder = new AtomicReference<>(defaultFile);
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                JFileChooser chooser = new JFileChooser(startDir);
                FileNameExtensionFilter filter = new FileNameExtensionFilter(
                        "HAR files", "har");
                chooser.setFileFilter(filter);
                int returnVal = chooser.showOpenDialog(null);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = chooser.getSelectedFile();
                    selectedFileHolder.set(selectedFile);
                }
            });
            File selectedFile = selectedFileHolder.get();
            System.out.format("browsing har: %s%n", selectedFile);
            new NodeBrowseHarWithChromeExample().execute(selectedFile);
        }

    }

    private static ReplayManager doCreateReplayManager() {
        NodeServerReplayManagerConfig replayManagerConfig = NodeServerReplayManagerConfig.auto();
        return new NodeServerReplayManager(replayManagerConfig);
    }
}
