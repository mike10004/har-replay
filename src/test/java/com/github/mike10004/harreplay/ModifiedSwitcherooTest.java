package com.github.mike10004.harreplay;

import com.github.mike10004.harreplay.Fixtures.Fixture;
import com.github.mike10004.harreplay.ReplayManagerTester.ReplayClient;
import com.github.mike10004.nativehelper.Platforms;
import com.github.mike10004.xvfbselenium.WebDriverSupport;
import com.github.mike10004.xvfbtesting.XvfbRule;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import io.github.bonigarcia.wdm.ChromeDriverManager;
import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class ModifiedSwitcherooTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public final XvfbRule xvfb = XvfbRule.builder().disabled(!Platforms.getPlatform().isLinux()).build();

    @BeforeClass
    public static void initChromeDriver() {
        ChromeDriverManager.getInstance().setup(Fixtures.RECOMMENDED_CHROME_DRIVER_VERSION);
    }

    @Test
    public void testExtensionWithSelenium_https() throws Exception {
        System.out.println("testExtensionWithSelenium_https");
        testExtensionWithSelenium(Fixtures.https());
    }

    @Test
    public void testExtensionWithSelenium_httpsRedirect() throws Exception {
        System.out.println("testExtensionWithSelenium_httpsRedirect");
        testExtensionWithSelenium(Fixtures.httpsRedirect());
    }

    private void testExtensionWithSelenium(Fixture fixture) throws Exception {
        ReplayManagerTester tester = new ReplayManagerTester(temporaryFolder.getRoot().toPath(), fixture.harFile());
        Multimap<URI, String> results = tester.exercise(new XvfbChromeDriverReplayClient(fixture.startUrl()), ReplayManagerTester.findHttpPortToUse());
        assertEquals("results map size", 1, results.size());
        String pageSource = results.values().iterator().next();
        System.out.println(StringUtils.abbreviate(pageSource, 256));
        assertTrue("expected page source to contain title '" + fixture.title() + "'", pageSource.contains(fixture.title()));
    }

    private class XvfbChromeDriverReplayClient implements ReplayClient<Multimap<URI, String>> {

        private final ImmutableList<URI> urisToGet;

        public XvfbChromeDriverReplayClient(URI firstUriToGet, URI... otherUrisToGet) {
            super();
            this.urisToGet = ImmutableList.copyOf(Lists.asList(firstUriToGet, otherUrisToGet));
        }

        @Override
        public Multimap<URI, String> useReplayServer(Path tempDir, HostAndPort proxy, Future<?> programFuture) throws Exception {
            ChromeOptions options = ChromeOptionsProducer.getDefault(tempDir).produceOptions(proxy);
            ChromeDriver driver = WebDriverSupport.chromeInEnvironment(xvfb.getController().newEnvironment()).create(options);
            Multimap<URI, String> pageSources = ArrayListMultimap.create();
            for (URI uri : urisToGet) {
                driver.get(uri.toString());
                String pageSource = driver.getPageSource();
                pageSources.put(uri, pageSource);
            }
            return pageSources;
        }
    }


    public static class ModifiedSwitcheroo_NoXvfbTest {

        @Rule
        public final TemporaryFolder temporaryFolder = new TemporaryFolder();

        @Test
        public void getExtensionCrxByteSource() throws Exception {
            ByteSource bs = ModifiedSwitcheroo.getExtensionCrxByteSource();
            File outfile = temporaryFolder.newFile();
            bs.copyTo(Files.asByteSink(outfile));
            assertTrue("outfile nonempty", outfile.length() > 0);
        }
    }

}