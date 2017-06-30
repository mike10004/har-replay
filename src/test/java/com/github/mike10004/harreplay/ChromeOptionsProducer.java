package com.github.mike10004.harreplay;

import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

interface ChromeOptionsProducer {

    ChromeOptions produceOptions(HostAndPort proxy) throws IOException;

    static ChromeOptionsProducer getDefault() {
        return getDefault(FileUtils.getTempDirectory().toPath());
    }

    /**
     * Gets a producer instance that will use the given temp directory if necessary.
     * @param temporaryDirectory the temp dir
     * @return the producer
     */
    static ChromeOptionsProducer getDefault(Path temporaryDirectory) {
        return proxy -> {
            File switcherooExtensionFile;
            URL resource = ModifiedSwitcheroo.getExtensionCrxResource();
            if ("file".equals(resource.getProtocol())) {
                try {
                    switcherooExtensionFile = new File(resource.toURI());
                } catch (URISyntaxException e) {
                    throw new IOException(e);
                }
            } else {
                switcherooExtensionFile = File.createTempFile("modified-switcheroo", ".crx", temporaryDirectory.toFile());
                Resources.asByteSource(resource).copyTo(Files.asByteSink(switcherooExtensionFile));
            }
            ChromeOptions options = new ChromeOptions();
            options.addExtensions(switcherooExtensionFile);
            options.addArguments("--proxy-server=" + proxy);
            return options;
        };
    }
}
