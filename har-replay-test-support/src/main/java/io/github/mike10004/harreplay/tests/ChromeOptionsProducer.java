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
import java.util.function.Consumer;

public interface ChromeOptionsProducer {

    String SYSPROP_CHROME_ARGUMENTS = "har-replay.chromedriver.chrome.arguments";
    String SYSPROP_CHROME_EXEC_PATH = "har-replay.chromedriver.chrome.executablePath";

    default Consumer<? super ChromeOptions> produceOptions(@Nullable HostAndPort proxy) {
        List<String> args = new ArrayList<>();
        if (proxy != null) {
            args.add("--proxy-server=" + proxy);
        }
        return produceOptions(args);
    }

    Consumer<? super ChromeOptions> produceOptions(Iterable<String> arguments);

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
        public Consumer<? super ChromeOptions> produceOptions(Iterable<String> arguments) {
            return options -> {
                String binaryPath = getBinaryPathOverride();
                if (binaryPath != null) {
                    options.setBinary(binaryPath);
                }
                List<String> allArguments = ImmutableList.copyOf(Iterables.concat(getAdditionalChromeArgs(), arguments));
                LoggerFactory.getLogger(getClass()).debug("produced ChromeOptions with binary {} and args {}", binaryPath, allArguments);
                options.addArguments(ImmutableList.copyOf(allArguments));
            };
        }
    }

    static ChromeOptionsProducer standard() {
        return new StandardChromeOptionsProducer();
    }

}
