package io.github.mike10004.harreplay.vhsimpl;

import com.github.mike10004.seleniumhelp.ChromeWebDriverFactory;
import com.github.mike10004.seleniumhelp.FirefoxWebDriverFactory;
import com.github.mike10004.seleniumhelp.UriProxySpecification;
import com.github.mike10004.seleniumhelp.WebDriverFactory;
import com.github.mike10004.seleniumhelp.WebdrivingConfig;
import com.github.mike10004.seleniumhelp.WebdrivingSession;
import com.github.mike10004.xvfbtesting.XvfbRule;
import com.google.common.net.HostAndPort;
import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import io.github.mike10004.harreplay.ReplaySessionControl;
import io.github.mike10004.harreplay.tests.ChromeDriverSetupRule;
import io.github.mike10004.harreplay.tests.ChromeOptionsProducer;
import io.github.mike10004.harreplay.tests.FirefoxDriverSetupRule;
import io.github.mike10004.harreplay.tests.Fixtures.Fixture;
import io.github.mike10004.harreplay.tests.ReplayManagerTestFoundation;
import io.github.mike10004.harreplay.tests.ReplayManagerTester;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static io.github.mike10004.harreplay.vhsimpl.VhsReplayManagerTest.SYSPROP_RESERVED_PORT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class E2E_JavascriptRedirectTest extends ReplayManagerTestFoundation {

    @ClassRule
    public static FirefoxDriverSetupRule firefoxDriverSetupRule = new FirefoxDriverSetupRule();

    @ClassRule
    public static ChromeDriverSetupRule chromeDriverSetupRule = new ChromeDriverSetupRule();

    @ClassRule
    public static final XvfbRule xvfb = XvfbRule.builder()
            .build();

    private final WebDriverFactory webDriverFactory;

    public E2E_JavascriptRedirectTest(WebDriverFactory webDriverFactory) {
        this.webDriverFactory = webDriverFactory;
    }

    @Override
    protected ReplayManagerTester createTester(Path tempDir, File harFile, ReplayServerConfig config) {
        throw new UnsupportedOperationException("not used by this test");
    }

    @Override
    protected String getReservedPortSystemPropertyName() {
        return SYSPROP_RESERVED_PORT;
    }

    private static final int DEFAULT_WAIT_TIMEOUT = 3; // seconds

    @Test
    public void javascriptRedirect()  throws Exception {
        System.out.println("\n\njavascriptRedirect\n");
        Path tempDir = temporaryFolder.getRoot().toPath();
        Fixture fixture = fixturesRule.getFixtures().javascriptRedirect();
        File harFile = fixture.harFile();
        URI startUrl = fixture.startUrl();
        URI finalUrl = new URIBuilder(startUrl).setPath("/other.html").build();
        AtomicReference<String> postRedirectPageSourceRef = new AtomicReference<>();
        DriverClient<List<URI>> client = driver -> {
            driver.get("data:,");
            List<URI> urlsVisited = new ArrayList<>();
            urlsVisited.add(startUrl);
            driver.get(startUrl.toString());
            dumpPageSource(driver);
            try {
                new WebDriverWait(driver, DEFAULT_WAIT_TIMEOUT).until(ExpectedConditions.urlMatches(Pattern.quote(finalUrl.toString())));
                postRedirectPageSourceRef.set(driver.getPageSource());
            } catch (org.openqa.selenium.TimeoutException e) {
                System.out.format("timed out while waiting for URL to change to %s%n", finalUrl);
            }
            URI nextUrl = URI.create(driver.getCurrentUrl());
            if (!urlsVisited.contains(nextUrl)) {
                urlsVisited.add(nextUrl);
            }
            return urlsVisited;
        };
        ReplayServerConfig serverConfig = ReplayServerConfig.builder()
                .build();
        ReplaySessionConfig sessionConfig = ReplaySessionConfig.builder(tempDir)
                .config(serverConfig)
                .build(harFile);
        VhsReplayManagerConfig vhsConfig = VhsReplayManagerConfig.getDefault();
        ReplayManager replayManager = new VhsReplayManager(vhsConfig);
        List<URI> urlsVisited;
        try (ReplaySessionControl ctrl = replayManager.start(sessionConfig)) {
            urlsVisited = useClient(ctrl, client);
        }
        assertEquals("urls visited", Arrays.asList(startUrl, finalUrl), urlsVisited);
        String expectedPageSourceSubstring = "This is the redirect destination page"; // actual source varies by browser
        assertTrue("expect page source contains " + expectedPageSourceSubstring, postRedirectPageSourceRef.get().contains(expectedPageSourceSubstring));
    }

    private interface DriverClient<T> {
        T drive(WebDriver driver) throws Exception;
    }

    private <T> T useClient(ReplaySessionControl sessionControl, DriverClient<T> client) throws Exception {
        HostAndPort proxy = sessionControl.getSocketAddress();
        try (WebdrivingSession session = webDriverFactory.startWebdriving(createWebdrivingConfig(proxy))) {
            WebDriver driver = session.getWebDriver();
            return client.drive(driver);
        }
    }

    private void dumpPageSource(WebDriver driver) {
        String url = driver.getCurrentUrl();
        String source = null;
        try {
            source = driver.getPageSource();
        } catch (Exception ignore) {
        }
        String title = driver.getTitle();
        System.out.format("==================================================================%n");
        System.out.format("======== start %s (%s)%n", url, title);
        System.out.format("==================================================================%n");
        System.out.print(StringUtils.abbreviateMiddle(source, "\n[...]\n", 1024));
        System.out.format("==================================================================%n");
        System.out.format("======== end %s (%s)%n", url, title);
        System.out.format("==================================================================%n");
    }

    private WebdrivingConfig createWebdrivingConfig(HostAndPort httpProxyAddress) {
        URI httpProxyUri = URI.create("http://" + httpProxyAddress.toString());
        WebdrivingConfig config = WebdrivingConfig.builder()
                .proxy(UriProxySpecification.of(httpProxyUri).toWebdrivingProxyDefinition())
                .build();
        return config;
    }

    @Parameterized.Parameters
    public static List<WebDriverFactory> createWebDriverFactories() {
        FirefoxWebDriverFactory firefoxFactory = FirefoxWebDriverFactory.builder()
                .preference("browser.chrome.favicons", false)
                .preference("browser.chrome.site_icons", false)
                .acceptInsecureCerts()
                .environment(() -> xvfb.getController().newEnvironment())
                .build();
        ChromeOptions chromeOptions = ChromeOptionsProducer.standard().produceOptions(Collections.emptyList());
        chromeOptions.setAcceptInsecureCerts(true);
        ChromeWebDriverFactory chromeFactory = ChromeWebDriverFactory.builder()
                .acceptInsecureCerts()
                .chromeOptions(chromeOptions)
                .environment(() -> xvfb.getController().newEnvironment())
                .build();
        return Arrays.asList(chromeFactory, firefoxFactory);
    }
}
