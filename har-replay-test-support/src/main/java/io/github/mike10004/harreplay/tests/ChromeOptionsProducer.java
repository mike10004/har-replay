package io.github.mike10004.harreplay.tests;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import org.openqa.selenium.chrome.ChromeOptions;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public interface ChromeOptionsProducer {

    String SYSPROP_CHROME_ARGUMENTS = "har-replay.chromedriver.chrome.arguments";

    ChromeOptions produceOptions(HostAndPort proxy) throws IOException;

    static List<String> populateArgsList(@Nullable HostAndPort proxy) {
        List<String> args = new ArrayList<>();
        if (proxy != null) {
            args.add("--proxy-server=" + proxy);
        }
        args.addAll(getAdditionalChromeArgs());
        return args;
    }

    static ChromeOptionsProducer standard() {
        return proxy -> {
            ChromeOptions options = new ChromeOptions();
            options.addArguments(populateArgsList(proxy));
            return options;
        };
    }

    static List<String> getAdditionalChromeArgs() {
        String tokens = System.getProperty(SYSPROP_CHROME_ARGUMENTS, "");
        return Splitter.on(CharMatcher.whitespace()).omitEmptyStrings().splitToList(tokens);
    }
}
