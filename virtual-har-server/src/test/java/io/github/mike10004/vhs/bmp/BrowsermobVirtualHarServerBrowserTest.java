package io.github.mike10004.vhs.bmp;

import com.github.mike10004.seleniumhelp.FirefoxWebDriverFactory;
import com.github.mike10004.seleniumhelp.WebDriverFactory;
import com.github.mike10004.seleniumhelp.WebdrivingConfig;
import com.github.mike10004.seleniumhelp.WebdrivingSession;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.apache.http.client.utils.URIBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BrowsermobVirtualHarServerBrowserTest extends BrowsermobVirtualHarServerTestBase {

    private static final int REDIRECT_WAIT_TIMEOUT_SECONDS = 10;
    private static final int BODY_WAIT_TIMEOUT_SECONDS = 1;
    private static final String EXPECTED_FINAL_REDIRECT_TEXT = "This is the redirect destination page";

    @BeforeClass
    public static void setUpClass() {
        WebDriverManager.firefoxdriver().setup();
    }

    @Test
    public void javascriptRedirect() throws Exception {
        org.apache.http.client.utils.URLEncodedUtils.class.getName();
        org.littleshoot.proxy.impl.ClientToProxyConnection.class.getName();
        io.github.mike10004.vhs.harbridge.Hars.class.getName();
        File harFile = File.createTempFile("javascript-redirect", ".har", temporaryFolder.getRoot());
        Resources.asByteSource(getClass().getResource("/javascript-redirect.har")).copyTo(Files.asByteSink(harFile));
        URI startUrl = URI.create("https://www.redi123.com/");
        URI finalUrl = new URIBuilder(startUrl).setPath("/other.html").build();
        ErrorNoticeListener errorResponseAccumulator = new ErrorNoticeListener();
        HarReplayManufacturer manufacturer = BmpTests.createManufacturer(harFile, Collections.emptyList());
        KeystoreData keystoreData = BmpTests.generateKeystoreForUnitTest("localhost");
        NanohttpdTlsEndpointFactory tlsEndpointFactory = NanohttpdTlsEndpointFactory.create(keystoreData, null);
        BrowsermobVhsConfig config = BrowsermobVhsConfig.builder(manufacturer)
                .scratchDirProvider(ScratchDirProvider.under(temporaryFolder.getRoot().toPath()))
                .tlsEndpointFactory(tlsEndpointFactory)
                .responseListener(errorResponseAccumulator)
                .build();
        VirtualHarServer server = new BrowsermobVirtualHarServer(config);
        String finalPageSource = null;
        try (VirtualHarServerControl ctrl = server.start()) {
            HostAndPort address = ctrl.getSocketAddress();
            try (WebdrivingSession session = createWebDriverFactory().startWebdriving(createWebdrivingConfig(address))) {
                WebDriver driver = session.getWebDriver();
                try {
                    driver.get(startUrl.toString());
                    System.out.println(driver.getPageSource());
                    try {
                        new WebDriverWait(driver, BODY_WAIT_TIMEOUT_SECONDS).until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
                        new WebDriverWait(driver, REDIRECT_WAIT_TIMEOUT_SECONDS).until(ExpectedConditions.urlToBe(finalUrl.toString()));
                        new WebDriverWait(driver, BODY_WAIT_TIMEOUT_SECONDS).until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
                        finalPageSource = driver.getPageSource();
                    } catch (org.openqa.selenium.TimeoutException e) {
                        System.err.format("timed out while waiting for body to load or URL to change to %s%n", finalUrl);
                    }
                } finally {
                    driver.quit();
                }
            }
        }
        System.out.format("final page source:%n%s%n", finalPageSource);
        errorResponseAccumulator.errorNotices.forEach(System.out::println);
        assertNotNull("final page source", finalPageSource);
        assertTrue("redirect text", finalPageSource.contains(EXPECTED_FINAL_REDIRECT_TEXT));
        assertEquals("error notices", ImmutableList.of(), errorResponseAccumulator.errorNotices);
    }

    static class ErrorNoticeListener implements BmpResponseListener {
        public final Collection<ErrorResponseNotice> errorNotices;

        public ErrorNoticeListener() {
            this(new ArrayList<>());
        }

        public ErrorNoticeListener(Collection<ErrorResponseNotice> errorNotices) {
            this.errorNotices = errorNotices;
        }

        @Override
        public void responding(RequestCapture requestCapture, ResponseCapture responseCapture) {
            int status = responseCapture.response.getStatus().code();
            if (status >= 400) {
                System.out.format("responding %s to %s %s%n", status, requestCapture.request.method, requestCapture.request.url);
                errorNotices.add(new ErrorResponseNotice(requestCapture.request, status));
            }
        }

    }

    private static class ErrorResponseNotice {

        public final ParsedRequest request;
        public final int status;

        private ErrorResponseNotice(ParsedRequest request, int status) {
            this.request = request;
            this.status = status;
        }

        @Override
        public String toString() {
            return "ErrorResponseNotice{" +
                    "request=" + request +
                    ", status=" + status +
                    '}';
        }
    }

    private WebdrivingConfig createWebdrivingConfig(HostAndPort httpProxyAddress) {
        URI httpProxyUri = URI.create("http://" + httpProxyAddress.toString());
        WebdrivingConfig config = WebdrivingConfig.builder()
                .proxy(httpProxyUri)
                .build();
        return config;
    }

    private WebDriverFactory createWebDriverFactory() {
        FirefoxWebDriverFactory factory = FirefoxWebDriverFactory.builder()
                .headless()
                .preference("browser.chrome.favicons", false)
                .preference("browser.chrome.site_icons", false)
                .build();
        return factory;
    }
}
