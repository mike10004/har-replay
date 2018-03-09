package io.github.mike10004.harreplay.tests;

import com.github.mike10004.xvfbtesting.XvfbRule;
import com.google.common.collect.Multimap;
import io.github.mike10004.harreplay.tests.Fixtures.Fixture;
import io.github.mike10004.harreplay.tests.Fixtures.FixturesRule;
import org.apache.commons.lang3.StringUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;

import java.io.File;
import java.net.URI;
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
        ChromeOptionsProducer optionsProducer = ChromeOptionsProducer.withSwitcheroo(tempDir);
        Multimap<URI, String> results = tester.exercise(new XvfbChromeDriverReplayClient(xvfb, optionsProducer, fixture.startUrl()), ReplayManagerTester.findOpenPort());
        assertEquals("results map size", 1, results.size());
        String pageSource = results.values().iterator().next();
        System.out.println(StringUtils.abbreviate(pageSource, 256));
        assertTrue("expected page source to contain title '" + fixture.title() + "'", pageSource.contains(fixture.title()));
    }

}
