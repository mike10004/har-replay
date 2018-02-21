package io.github.mike10004.harreplay.tests;

import io.github.mike10004.harreplay.ModifiedSwitcheroo;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
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
import java.util.List;

public interface ChromeOptionsProducer {

    String SYSPROP_CHROME_ARGUMENTS = "har-replay.chromedriver.chrome.arguments";

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
            List<String> moreArgs = getAdditionalChromeArgs();
            options.addArguments(moreArgs);
            return options;
        };
    }

    static List<String> getAdditionalChromeArgs() {
        String tokens = System.getProperty(SYSPROP_CHROME_ARGUMENTS, "");
        return Splitter.on(CharMatcher.whitespace()).omitEmptyStrings().splitToList(tokens);
    }
}
