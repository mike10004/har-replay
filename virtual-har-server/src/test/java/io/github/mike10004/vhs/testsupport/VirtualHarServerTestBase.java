package io.github.mike10004.vhs.testsupport;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.HostAndPort;
import io.github.mike10004.vhs.BasicHeuristic;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.HeuristicEntryMatcher;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class VirtualHarServerTestBase {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    public enum TlsMode {
        SUPPORT_REQUIRED,
        NO_SUPPORT_REQUIRED,
        PREDEFINED_CERT_SUPPORT
    }

    protected abstract VirtualHarServer createServer(int port, File harFile, EntryMatcherFactory entryMatcherFactory, TestContext context) throws IOException;

    protected static URI getBasicUri1() {
        return oneUri;
    }

    private static final URI oneUri = URI.create("http://example.com/one"),
            twoUri = URI.create("http://example.com/two"),
            notFoundUri = URI.create("http://example.com/not-found");

    protected static final String KEY_TLS_MODE = "tlsMode";
    protected static final String KEY_CLIENT_SUPPLIER = "clientSupplier";

    protected static class TestContext {

        private final Map<String, Object> items;

        public TestContext() {
            items = new HashMap<>();
        }

        public TestContext put(String key, Object value) {
            items.put(key, value);
            return this;
        }

        public <T> T get(String key) {
            //noinspection unchecked
            return (T) items.get(key);
        }
    }

    @Test
    public void basicTest() throws Exception {
        List<URI> uris = Arrays.asList(
                oneUri, twoUri, notFoundUri
        );
        Multimap<URI, ResponseSummary> responses = doBasicTest(uris);
        examineResponses(uris, responses);
    }

    protected Multimap<URI, ResponseSummary> doBasicTest(Iterable<URI> uris) throws Exception {
        Path temporaryDirectory = temporaryFolder.newFolder().toPath();
        File harFile = Tests.getReplayTest1HarFile(temporaryDirectory);
        EntryMatcherFactory entryMatcherFactory = HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
        int port = Tests.findOpenPort();
        VirtualHarServer server = createServer(port, harFile, entryMatcherFactory, new TestContext().put(KEY_TLS_MODE, TlsMode.NO_SUPPORT_REQUIRED));
        try (VirtualHarServerControl ctrl = server.start()) {
            ApacheRecordingClient client = new ApacheRecordingClient(false);
            return client.collectResponses(uris, ctrl.getSocketAddress());
        }
    }

    @Test
    public void httpsTest() throws Exception {
        TestContext context = new TestContext();
        context.put(KEY_TLS_MODE, TlsMode.SUPPORT_REQUIRED);
        context.put(KEY_CLIENT_SUPPLIER, BlindlyTrustingClient.supplier());
        doHttpsTest(context);
    }

    protected void doHttpsTest(TestContext context) throws Exception {
        Path temporaryDirectory = temporaryFolder.newFolder().toPath();
        File harFile = Tests.getHttpsExampleHarFile(temporaryDirectory);
        EntryMatcherFactory entryMatcherFactory = HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
        int port = Tests.findOpenPort();
        VirtualHarServer server = createServer(port, harFile, entryMatcherFactory, context);
        URI uri = URI.create("https://www.example.com/");
        Multimap<URI, ResponseSummary> responses;
        try (VirtualHarServerControl ctrl = server.start()) {
            Supplier<ApacheRecordingClient> clientFactory = context.get(KEY_CLIENT_SUPPLIER);
            ApacheRecordingClient client = clientFactory.get();
            responses = client.collectResponses(Collections.singleton(uri), ctrl.getSocketAddress());
            httpsTest_ctrlNotYetClosed(ctrl, client, responses);
        }
        assertEquals("num responses", 1, responses.size());
        ResponseSummary summary = responses.values().iterator().next();
        boolean containsTitle = summary.entity.contains("Example Domain over HTTPS");
        if (!containsTitle) {
            System.out.format("about to fail on this:%n%s%n", summary.entity);
        }
        assertTrue("contains correct title", containsTitle);
    }

    @SuppressWarnings("unused")
    protected void httpsTest_ctrlNotYetClosed(VirtualHarServerControl ctrl, ApacheRecordingClient client, Multimap<URI, ResponseSummary> responses) {
        // override to do some checks before the proxy server has stopped
    }

    protected void examineResponses(List<URI> attempted, Multimap<URI, ResponseSummary> responses) {
        assertTrue("attempted all urls", responses.keySet().containsAll(attempted));
        ResponseSummary oneResponse = responses.get(oneUri).iterator().next();
        System.out.format("'%s' fetched from %s%n", oneResponse.entity, oneUri);
        assertEquals("response to " + oneUri, "one", oneResponse.entity);
    }

    protected static abstract class ApacheRawClient<T> {

        public ApacheRawClient() {
        }

        protected void configureHttpClientBuilder(HttpClientBuilder b, @Nullable HostAndPort proxy) throws Exception {
            if (proxy != null) {
                b.setProxy(new HttpHost(proxy.getHost(), proxy.getPort()));
            }
            b.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
        }

        protected abstract T transform(URI requestUrl, HttpResponse response) throws IOException;

        public Multimap<URI, T> collectResponses(Iterable<URI> urisToGet, @Nullable HostAndPort proxy) throws Exception {
            Multimap<URI, T> result = ArrayListMultimap.create();
            HttpClientBuilder clientBuilder = HttpClients.custom();
            configureHttpClientBuilder(clientBuilder, proxy);
            try (CloseableHttpClient client = clientBuilder.build()) {
                for (URI uri : urisToGet) {
                    System.out.format("fetching %s%n", uri);
                    HttpGet get = new HttpGet(transformUri(uri));
                    try (CloseableHttpResponse response = client.execute(get)) {
                        T responseSummary = transform(uri, response);
                        result.put(uri, responseSummary);
                    }
                }
            }
            return result;
        }

        protected URI transformUri(URI uri) {
            return uri;
        }
    }

    protected static class ApacheRecordingClient extends ApacheRawClient<ResponseSummary> {

        private final boolean transformHttpsToHttp;

        public ApacheRecordingClient(boolean transformHttpsToHttp) {
            this.transformHttpsToHttp = transformHttpsToHttp;
        }

        @Override
        protected URI transformUri(URI uri) { // equivalent of switcheroo extension
            if (transformHttpsToHttp && "https".equals(uri.getScheme())) {
                try {
                    return new URIBuilder(uri).setScheme("http").build();
                } catch (URISyntaxException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                return uri;
            }
        }

        @Override
        protected ResponseSummary transform(URI requestUrl, HttpResponse response) throws IOException {
            StatusLine statusLine = response.getStatusLine();
            System.out.format("received %s from %s%n", statusLine, requestUrl);
            String entity = EntityUtils.toString(response.getEntity());
            return new ResponseSummary(statusLine, entity);
        }
    }

    protected static class ResponseSummary {
        public final StatusLine statusLine;
        public final String entity;

        private ResponseSummary(StatusLine statusLine, String entity) {
            this.statusLine = statusLine;
            this.entity = entity;
        }
    }

    public static class BlindlyTrustingClient extends ApacheRecordingClient {

        public BlindlyTrustingClient() {
            super(false);
        }

        public static Supplier<ApacheRecordingClient> supplier() {
            return BlindlyTrustingClient::new;
        }

        @Override
        protected void configureHttpClientBuilder(HttpClientBuilder b, HostAndPort proxy) throws Exception {
            super.configureHttpClientBuilder(b, proxy);
            try {
                Tests.configureClientToTrustBlindly(b);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }

    }

}
