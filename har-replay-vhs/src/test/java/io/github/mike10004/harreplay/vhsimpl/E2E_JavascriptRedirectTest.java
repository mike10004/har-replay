package io.github.mike10004.harreplay.vhsimpl;

import com.github.mike10004.xvfbtesting.XvfbRule;
import com.google.common.net.HostAndPort;
import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import io.github.mike10004.harreplay.ReplaySessionControl;
import io.github.mike10004.harreplay.tests.ChromeDriverKillHook;
import io.github.mike10004.harreplay.tests.ChromeDriverSetupRule;
import io.github.mike10004.harreplay.tests.ChromeOptionsProducer;
import io.github.mike10004.harreplay.tests.Fixtures.Fixture;
import io.github.mike10004.harreplay.tests.ReplayManagerTestFoundation;
import io.github.mike10004.harreplay.tests.ReplayManagerTester;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

import static io.github.mike10004.harreplay.vhsimpl.VhsReplayManagerTest.SYSPROP_RESERVED_PORT;
import static org.junit.Assert.assertEquals;

public class E2E_JavascriptRedirectTest extends ReplayManagerTestFoundation {

    @ClassRule
    public static ChromeDriverSetupRule chromeDriverSetupRule = new ChromeDriverSetupRule();

    @Rule
    public XvfbRule xvfb = XvfbRule.builder()
            .build();

    @Override
    protected ReplayManagerTester createTester(Path tempDir, File harFile, ReplayServerConfig config) {
        throw new UnsupportedOperationException("not used by this test");
    }

    @Override
    protected String getReservedPortSystemPropertyName() {
        return SYSPROP_RESERVED_PORT;
    }

    private static final int DEFAULT_WAIT_TIMEOUT = 3; // seconds

    // @org.junit.Ignore
    @Test
    public void javascriptRedirect()  throws Exception {
        System.out.println("\n\njavascriptRedirect\n");
        Path tempDir = temporaryFolder.getRoot().toPath();
        Fixture fixture = fixturesRule.getFixtures().javascriptRedirect();
        File harFile = fixture.harFile();
        URI startUrl = fixture.startUrl();
        URI finalUrl = new URIBuilder(startUrl).setPath("/other.html").build();
        DriverClient<List<URI>> client = driver -> {
            driver.get("data:,");
//            new CountDownLatch(1).await();
            List<URI> urlsVisited = new ArrayList<>();
            urlsVisited.add(startUrl);
            driver.get(startUrl.toString());
            dumpPageSource(driver);
            try {
                new WebDriverWait(driver, DEFAULT_WAIT_TIMEOUT).until(ExpectedConditions.urlMatches(Pattern.quote(finalUrl.toString())));
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
    }

    private interface DriverClient<T> {
        T drive(WebDriver driver) throws Exception;
    }

    public <T> T useClient(ReplaySessionControl sessionControl, DriverClient<T> client) throws Exception {
        HostAndPort proxy = sessionControl.getSocketAddress();
        ChromeOptions options = ChromeOptionsProducer.standard().produceOptions(proxy);
        ChromeDriverService service = new ChromeDriverService.Builder()
                .usingAnyFreePort()
                .withEnvironment(xvfb.getController().newEnvironment())
                .build();
        ChromeDriver driver = new ChromeDriver(service, options);
        ChromeDriverKillHook.getInstance().add(driver);
        try {
            return client.drive(driver);
        } finally {
            driver.quit();
            ChromeDriverKillHook.getInstance().remove(driver);
        }
    }

    private void dumpPageSource(WebDriver driver) {
        String url = driver.getCurrentUrl();
        String source = driver.getPageSource();
        String title = driver.getTitle();
        System.out.format("==================================================================%n");
        System.out.format("======== start %s (%s)%n", url, title);
        System.out.format("==================================================================%n");
        System.out.print(StringUtils.abbreviateMiddle(source, "\n[...]\n", 1024));
        System.out.format("==================================================================%n");
        System.out.format("======== end %s (%s)%n", url, title);
        System.out.format("==================================================================%n");
    }
}
