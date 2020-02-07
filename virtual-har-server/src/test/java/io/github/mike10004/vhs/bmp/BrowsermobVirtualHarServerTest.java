package io.github.mike10004.vhs.bmp;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.BaseEncoding;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.browserup.harreader.model.HarContent;
import com.browserup.harreader.model.HarEntry;
import com.browserup.harreader.model.HarHeader;
import com.browserup.harreader.model.HarRequest;
import com.browserup.harreader.model.HarResponse;
import com.browserup.harreader.model.HttpMethod;
import io.github.mike10004.vhs.BasicHeuristic;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.HeuristicEntryMatcher;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.bmp.ResponseManufacturingFiltersSource.PassthruPredicate;
import io.github.mike10004.vhs.testsupport.VhsTests;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BrowsermobVirtualHarServerTest extends BrowsermobVirtualHarServerTestBase {

    @Test
    public void basicTest() throws Exception {
        super.doBasicTest();
    }

    @Test
    public void httpsTest() throws Exception {
        boolean clean = false;
        try {
            super.doHttpsTest();
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
            b.setSSLHostnameVerifier(VhsTests.blindHostnameVerifier());
        }
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
        File harFile = VhsTests.getHttpsExampleHarFile(temporaryDirectory);
        EntryMatcherFactory entryMatcherFactory = HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
        int port = VhsTests.findOpenPort();
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