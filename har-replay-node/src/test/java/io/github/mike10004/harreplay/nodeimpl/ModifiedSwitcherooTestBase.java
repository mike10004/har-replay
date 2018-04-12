package io.github.mike10004.harreplay.nodeimpl;

import com.github.mike10004.xvfbtesting.XvfbRule;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import io.github.mike10004.harreplay.tests.ChromeDriverSetupRule;
import io.github.mike10004.harreplay.tests.ChromeOptionsProducer;
import io.github.mike10004.harreplay.tests.Fixtures;
import io.github.mike10004.harreplay.tests.Fixtures.Fixture;
import io.github.mike10004.harreplay.tests.Fixtures.FixturesRule;
import io.github.mike10004.harreplay.tests.ReplayManagerTester;
import io.github.mike10004.harreplay.tests.XvfbChromeDriverReplayClient;
import org.apache.commons.lang3.StringUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class ModifiedSwitcherooTestBase {

    @ClassRule
    public static FixturesRule fixturesRule = Fixtures.asRule();

    @ClassRule
    public static ChromeDriverSetupRule chromeDriverSetupRule = new ChromeDriverSetupRule();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public final XvfbRule xvfb = XvfbRule.builder().build();

    @Rule
    public final Timeout timeout = new Timeout(60, TimeUnit.SECONDS);

    @Test
    public void testExtensionWithSelenium_https() throws Exception {
        System.out.println("testExtensionWithSelenium_https");
        testExtensionWithSelenium(fixturesRule.getFixtures().https());
    }

    @Test
    public void testExtensionWithSelenium_httpsRedirect() throws Exception {
        System.out.println("testExtensionWithSelenium_httpsRedirect");
        testExtensionWithSelenium(fixturesRule.getFixtures().httpsRedirect());
    }

    protected abstract ReplayManagerTester createTester(Path tempRoot, File harFile);

    private void testExtensionWithSelenium(Fixture fixture) throws Exception {
        Path tempDir = temporaryFolder.getRoot().toPath();
        ReplayManagerTester tester = createTester(tempDir, fixture.harFile());
        ChromeOptionsProducer optionsProducer = withSwitcheroo(tempDir);
        Multimap<URI, String> results = tester.exercise(new XvfbChromeDriverReplayClient(xvfb, optionsProducer, fixture.startUrl()), ReplayManagerTester.findOpenPort());
        assertEquals("results map size", 1, results.size());
        String pageSource = results.values().iterator().next();
        System.out.println(StringUtils.abbreviate(pageSource, 256));
        assertTrue("expected page source to contain title '" + fixture.title() + "'", pageSource.contains(fixture.title()));
    }

    /**
     * Gets a producer instance that configures Chrome to use the Switcheroo extension.
     * @param temporaryDirectory the temp dir to copy the extension archive to
     * @return the producer
     */
    static ChromeOptionsProducer withSwitcheroo(Path temporaryDirectory) {
        return (proxy) -> {
            ChromeOptions options = new ChromeOptions();
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
            options.addExtensions(switcherooExtensionFile);
            options.addArguments(ChromeOptionsProducer.populateArgsList(proxy));
            return options;
        };
    }

}
