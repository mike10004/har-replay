package io.github.mike10004.harreplay.tests;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.net.HostAndPort;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public interface ChromeOptionsProducer {

    String SYSPROP_CHROME_ARGUMENTS = "har-replay.chromedriver.chrome.arguments";
    String SYSPROP_CHROME_EXEC_PATH = "har-replay.chromedriver.chrome.executablePath";

    default ChromeOptions produceOptions(@Nullable HostAndPort proxy) {
        List<String> args = new ArrayList<>();
        if (proxy != null) {
            args.add("--proxy-server=" + proxy);
        }
        return produceOptions(args);
    }

    ChromeOptions produceOptions(Iterable<String> arguments);

    class StandardChromeOptionsProducer implements ChromeOptionsProducer {

        @Nullable
        private static String getBinaryPathOverride() {
            return Strings.emptyToNull(System.getProperty(SYSPROP_CHROME_EXEC_PATH));
        }

        private static List<String> getAdditionalChromeArgs() {
            String tokens = System.getProperty(SYSPROP_CHROME_ARGUMENTS, "");
            return Splitter.on(CharMatcher.whitespace()).omitEmptyStrings().splitToList(tokens);
        }

        @Override
        public ChromeOptions produceOptions(Iterable<String> arguments) {
            ChromeOptions options = new ChromeOptions();
            String binaryPath = getBinaryPathOverride();
            if (binaryPath != null) {
                options.setBinary(binaryPath);
            }
            arguments = ImmutableList.copyOf(Iterables.concat(getAdditionalChromeArgs(), arguments));
            LoggerFactory.getLogger(getClass()).debug("produced ChromeOptions with binary {} and args {}", binaryPath, arguments);
            options.addArguments(ImmutableList.copyOf(arguments));
            return options;
        }
    }

    static ChromeOptionsProducer standard() {
        return new StandardChromeOptionsProducer();
    }

}
