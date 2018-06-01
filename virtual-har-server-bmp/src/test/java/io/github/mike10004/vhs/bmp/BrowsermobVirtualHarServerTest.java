package io.github.mike10004.vhs.bmp;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.machinepublishers.jbrowserdriver.JBrowserDriver;
import com.machinepublishers.jbrowserdriver.ProxyConfig;
import com.machinepublishers.jbrowserdriver.Settings;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import de.sstoehr.harreader.model.HttpMethod;
import io.github.mike10004.vhs.BasicHeuristic;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.HeuristicEntryMatcher;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.bmp.ResponseManufacturingFiltersSource.PassthruPredicate;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import io.github.mike10004.vhs.testsupport.Tests;
import io.github.mike10004.vhs.testsupport.VirtualHarServerTestBase;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BrowsermobVirtualHarServerTest extends VirtualHarServerTestBase {

    private static final int REDIRECT_WAIT_TIMEOUT_SECONDS = 10; // JBrowserDriver can be a tad slow to execute JavaScript
    private static final String EXPECTED_FINAL_REDIRECT_TEXT = "This is the redirect destination page";

    private List<String> requests;
    private List<String> customValues;

    @Before
    public void setUp() throws IOException {
        customValues = Collections.synchronizedList(new ArrayList<>());
        requests = Collections.synchronizedList(new ArrayList<>());
    }

    private static final String CUSTOM_HEADER_NAME = "X-Virtual-Har-Server-Unit-Test";

    @Override
    protected VirtualHarServer createServer(int port, File harFile, EntryMatcherFactory entryMatcherFactory, TestContext context) throws IOException {
        BrowsermobVhsConfig config = createServerConfig(port, harFile, entryMatcherFactory, context);
        return new BrowsermobVirtualHarServer(config);
    }

    protected BrowsermobVhsConfig createServerConfig(int port, File harFile, EntryMatcherFactory entryMatcherFactory, TestContext context) throws IOException {
        List<HarEntry> entries;
        try {
            entries = new de.sstoehr.harreader.HarReader().readFromFile(harFile).getLog().getEntries();
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
        EntryParser<HarEntry> parser = HarBridgeEntryParser.withPlainEncoder(new SstoehrHarBridge());
        EntryMatcher entryMatcher = entryMatcherFactory.createEntryMatcher(entries, parser);
        HarReplayManufacturer responseManufacturer = new HarReplayManufacturer(entryMatcher, Collections.emptyList()) {
            @Override
            public ResponseCapture manufacture(RequestCapture capture) {
                requests.add(String.format("%s %s", capture.request.method, capture.request.url));
                return super.manufacture(capture);
            }
        };
        Path scratchParent = temporaryFolder.getRoot().toPath();
        BmpResponseListener responseFilter = new HeaderAddingFilter(CUSTOM_HEADER_NAME, () -> {
            String value = UUID.randomUUID().toString();
            customValues.add(value);
            return value;
        });
        BrowsermobVhsConfig.Builder configBuilder = BrowsermobVhsConfig.builder(responseManufacturer)
                    .port(port)
                    .responseListener(responseFilter)
                    .scratchDirProvider(ScratchDirProvider.under(scratchParent));
        TlsMode tlsMode = context.get(KEY_TLS_MODE);
        if (tlsMode == TlsMode.SUPPORT_REQUIRED || tlsMode == TlsMode.PREDEFINED_CERT_SUPPORT) {
            try {
                String commonName = "localhost";
                KeystoreData keystoreData = BmpTests.generateKeystoreForUnitTest(commonName);
                NanohttpdTlsEndpointFactory tlsEndpointFactory = NanohttpdTlsEndpointFactory.create(keystoreData, null);
                configBuilder.tlsEndpointFactory(tlsEndpointFactory);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }
        if (tlsMode == TlsMode.PREDEFINED_CERT_SUPPORT) {
            KeystoreData keystoreData = context.get(KEY_KEYSTORE_DATA);
            configBuilder.certificateAndKeySource(keystoreData.asCertificateAndKeySource());
        }
        return configBuilder.build();
    }

    @Test
    @Override
    public void httpsTest() throws Exception {
        boolean clean = false;
        try {
            super.httpsTest();
            clean = true;
        } finally {
            System.out.format("%s requests handled%n", requests.size());
            requests.forEach(request -> {
                System.out.format("%s%n", request);
            });
            if (clean) {
                assertEquals("num requests", 1, requests.size());
                assertEquals("num responses", 1, customValues.size());
            }
        }
    }

    private static final String KEY_KEYSTORE_DATA = "keystoreData";

    @Test
    public void httpsTest_pregeneratedMitmCertificate() throws Exception {
        KeystoreData keystoreData = BmpTests.generateKeystoreForUnitTest(null);
        CertAwareClient client = new CertAwareClient(keystoreData);
        checkSelfSignedRequiresTrustConfig(client);
        TestContext context = new TestContext();
        context.put(KEY_CLIENT_SUPPLIER, Suppliers.ofInstance(client));
        context.put(KEY_TLS_MODE, TlsMode.PREDEFINED_CERT_SUPPORT);
        context.put(KEY_KEYSTORE_DATA, keystoreData);
        doHttpsTest(context);
    }

    private static final String SELF_SIGNED_URL_STR = "https://self-signed.badssl.com/";

    private void checkSelfSignedRequiresTrustConfig(ApacheRecordingClient client) throws Exception {
        try {
            client.collectResponses(Collections.singleton(URI.create(SELF_SIGNED_URL_STR)), null);
            throw new IllegalStateException("if we're here it means the client wrongly trusted an unknown self-signed certificate");
        } catch (javax.net.ssl.SSLHandshakeException ignore) {
        }
    }

    private static class CertAwareClient extends ApacheRecordingClient {

        private final KeystoreData keystoreData;

        public CertAwareClient(KeystoreData keystoreData) {
            super(false);
            this.keystoreData = requireNonNull(keystoreData);
        }

        @Override
        protected void configureHttpClientBuilder(HttpClientBuilder b, HostAndPort proxy) throws Exception {
            super.configureHttpClientBuilder(b, proxy);
            KeyStore keyStore = keystoreData.loadKeystore();
            SSLContext customSslContext = SSLContexts.custom()
                    .loadTrustMaterial(keyStore, null)
                    .build();
            b.setSSLContext(customSslContext);
            b.setSSLHostnameVerifier(Tests.blindHostnameVerifier());
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

    private static class ErrorNoticeListener implements BmpResponseListener {
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
            ProxyConfig proxyConfig = new ProxyConfig(ProxyConfig.Type.HTTP, "localhost", address.getPort());
            Settings settings = Settings.builder()
                    .hostnameVerification(false)
                    .ssl("trustanything")
                    .proxy(proxyConfig)
                    .build();
            WebDriver driver = new JBrowserDriver(settings);
            try {
                driver.get(startUrl.toString());
                System.out.println(driver.getPageSource());
                try {
                    new WebDriverWait(driver, REDIRECT_WAIT_TIMEOUT_SECONDS).until(ExpectedConditions.urlToBe(finalUrl.toString()));
                    finalPageSource = driver.getPageSource();
                } catch (org.openqa.selenium.TimeoutException e) {
                    System.err.format("timed out while waiting for URL to change to %s%n", finalUrl);
                }
            } finally {
                driver.quit();
            }
        }
        System.out.format("final page source:%n%s%n", finalPageSource);
        errorResponseAccumulator.errorNotices.forEach(System.out::println);
        assertNotNull("final page source", finalPageSource);
        assertTrue("redirect text", finalPageSource.contains(EXPECTED_FINAL_REDIRECT_TEXT));
        assertEquals("error notices", ImmutableList.of(), errorResponseAccumulator.errorNotices);
    }

    private static BmpResponseListener newLoggingResponseListener() {
        return new BmpResponseListener() {
            @Override
            public void responding(RequestCapture requestCapture, ResponseCapture responseCapture) {
                System.out.format("responding with status %s to %s %s", responseCapture.response.getStatus(), requestCapture.request.method, requestCapture.request.url);
            }
        };
    }

    private static String getHost(HttpRequest request) {
        String host = request.headers().get(HttpHeaders.HOST);
        if (host == null) {
            throw new IllegalArgumentException(String.format("no Host header in request %s %s", request.getMethod(), request.getUri()));
        }
        return HostAndPort.fromString(host).getHost(); // clean port value if it exists
    }

    @Test
    public void https_rejectUpstreamBadCertificate() throws Exception {
        System.out.println("https_rejectUpstreamBadCertificate");
        System.out.println("expect some big exception stack traces in the logs for this one");
        Path temporaryDirectory = temporaryFolder.newFolder().toPath();
        File harFile = Tests.getHttpsExampleHarFile(temporaryDirectory);
        EntryMatcherFactory entryMatcherFactory = HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
        int port = Tests.findOpenPort();
        TestContext context = new TestContext()
                .put(KEY_TLS_MODE, TlsMode.SUPPORT_REQUIRED)
                .put(KEY_CLIENT_SUPPLIER, BlindlyTrustingClient.supplier());
        BrowsermobVhsConfig config = createServerConfig(port, harFile, entryMatcherFactory, context);
        URI goodUrl = URI.create("https://sha256.badssl.com/"), selfSignedUrl = URI.create(SELF_SIGNED_URL_STR);
        List<URI> urls = Arrays.asList(goodUrl, selfSignedUrl);
        PassthruPredicate passthruPredicate = new PassthruPredicate() {
            @Override
            public boolean isForwardable(HttpRequest originalRequest, @Nullable ChannelHandlerContext ctx) {
                String host = getHost(originalRequest);
                return urls.stream().map(URI::getHost).anyMatch(host::equals);
            }
        };
        VirtualHarServer server = new BrowsermobVirtualHarServer(config) {
            @Override
            protected PassthruPredicate createPassthruPredicate() {
                return passthruPredicate;
            }
        };
        Multimap<URI, ResponseSummary> responses;
        try (VirtualHarServerControl ctrl = server.start()) {
            Supplier<ApacheRecordingClient> clientFactory = context.get(KEY_CLIENT_SUPPLIER);
            ApacheRecordingClient client = clientFactory.get();
            responses = client.collectResponses(urls, ctrl.getSocketAddress());
        }
        assertEquals("expect 1 response per URL in " + urls, urls.size(), responses.size());
        assertTrue("all URLs represented in response map", responses.keySet().containsAll(urls));
        ResponseSummary badSslResponse = responses.get(selfSignedUrl).iterator().next();
        assertEquals("status " + badSslResponse.statusLine, HttpStatus.SC_BAD_GATEWAY, badSslResponse.statusLine.getStatusCode());
        ResponseSummary goodResponse = responses.get(goodUrl).iterator().next();
        assertEquals("status from good URL response", HttpStatus.SC_OK, goodResponse.statusLine.getStatusCode());
    }

    @Test
    public void httpsWithoutCustomTlsEndpointFails() throws Exception {
        URI httpsUri = new URIBuilder(getBasicUri1()).setScheme("https").build();
        Multimap<URI, ResponseSummary> responses = doBasicTest(Collections.singleton(httpsUri));
        assertEquals("num responses", 1, responses.size());
        ResponseSummary response = responses.values().iterator().next();
        assertEquals("response status", HttpStatus.SC_BAD_GATEWAY, response.statusLine.getStatusCode());
    }

    @Test
    public void unspecifiedEncoding() throws Exception {
        String HEX_BYTES = "E29C93";
        byte[] bytes = BaseEncoding.base16().decode(HEX_BYTES);
        String text = new String(bytes, StandardCharsets.UTF_8);
        System.out.format("text: %s%n", text);
        String url = "http://www.example.com/gimme-difficult-text";
        HarRequest request = BmpTests.buildHarRequest(HttpMethod.GET, url, ImmutableList.of());
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8.withoutParameters();
        System.out.format("content-type: %s%n", contentType);
        HarContent responseContent = BmpTests.buildHarContent(text, contentType);
        List<HarHeader> responseHeaders = BmpTests.buildHarHeaders(HttpHeaders.CONTENT_TYPE, contentType.toString());
        HarResponse response = BmpTests.buildHarResponse(200, responseHeaders, responseContent);
        HarEntry entry = BmpTests.buildHarEntry(request, response);
        List<HarEntry> entries = ImmutableList.of(entry);
        BmpResponseManufacturer responseManufacturer = BmpTests.createManufacturer(entries, Collections.emptyList());
        BrowsermobVhsConfig config = BrowsermobVhsConfig.builder(responseManufacturer)
                .scratchDirProvider(ScratchDirProvider.under(temporaryFolder.getRoot().toPath()))
                .responseListener(newLoggingResponseListener())
                .build();
        VirtualHarServer server = new BrowsermobVirtualHarServer(config);
        Multimap<URI, byte[]> responses;
        try (VirtualHarServerControl ctrl = server.start()) {
            ApacheRawClient<byte[]> client = new ApacheRawClient<byte[]>() {
                @Override
                protected byte[] transform(URI requestUrl, org.apache.http.HttpResponse response) throws IOException {
                    return EntityUtils.toByteArray(response.getEntity());
                }
            };
            responses = client.collectResponses(Collections.singleton(URI.create(url)), ctrl.getSocketAddress());
        }
        assertEquals("num responses", 1, responses.size());
        byte[] actual = responses.values().iterator().next();
        System.out.format("expecting %s, actual = %s%n", HEX_BYTES, BaseEncoding.base16().encode(actual));
        assertArrayEquals("response data", bytes, actual);
    }
}