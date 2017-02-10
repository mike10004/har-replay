package com.github.mike10004.harreplay;

import com.github.mike10004.harreplay.ReplayManagerTester.ReplayClient;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.net.HostAndPort;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ReplayManagerTest {

    private static final Logger log = LoggerFactory.getLogger(ReplayManagerTest.class);

    private static final String SYSPROP_SERVER_REPLAY_PORT = "server-replay.port"; // see pom.xml build-helper-plugin

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder() {
        @Override
        protected void before() throws Throwable {
            super.before();
        }

        @Override
        protected void after() {
            File root = getRoot();
            System.out.format("TemporaryFolder.after() about to execute: %s (exists? %s)%n", root.getName(), root.exists());
            super.after();
        }
    };

    private static int reservedPort = 0;

    @After
    public void tearDown() {
        File root = temporaryFolder.getRoot();
        System.out.format("tearDown: temp root is %s (exists? %s)%n", root.getName(), root.exists());
        System.out.println();
    }

    @BeforeClass
    public static void findReservedPort() throws IOException {
        String portStr = System.getProperty(SYSPROP_SERVER_REPLAY_PORT);
        if (Strings.isNullOrEmpty(portStr)) { // probably running with IDE test runner, not Maven
            log.trace("unit test port not reserved by build process; will try to find open port");
            try (ServerSocket socket = new ServerSocket(0)) {
                reservedPort = socket.getLocalPort();
                log.debug("found open port {} by opening socket %s%n", reservedPort, socket);
            }
        } else {
            reservedPort = Integer.parseInt(portStr);
        }
    }

    @Test
    public void startAsync_http() throws Exception {
        System.out.println("\n\nstartAsync_http\n");
        File harFile = new File(getClass().getResource("/http.www.example.com.har").toURI());
        testStartAsync(harFile, URI.create("http://www.example.com/"));
    }

    @Test
    public void startAsync_https() throws Exception {
        System.out.println("\n\nstartAsync_https\n");
        File harFile = new File(getClass().getResource("/https.www.example.com.har").toURI());
        testStartAsync(harFile, URI.create("https://www.example.com/"));
    }

    private void testStartAsync(File harFile, URI uri) throws Exception {
        Path tempDir = temporaryFolder.getRoot().toPath();
        Multimap<URI, ResponseSummary> responses = new ReplayManagerTester(tempDir, harFile)
                .exercise(newApacheClient(uri), reservedPort);
        Collection<ResponseSummary> responsesForUri = responses.get(uri);
        assertFalse("no response for uri " + uri, responsesForUri.isEmpty());
        ResponseSummary response = responsesForUri.iterator().next();
        System.out.format("response: %s%n", response.statusLine);
        assertEquals("status", HttpStatus.SC_OK, response.statusLine.getStatusCode());
        Har har = HarIO.fromFile(harFile);
        HarEntry basicEntry = har.getLog().getEntries().stream().filter(entry -> "/".equals(URI.create(entry.getRequest().getUrl()).getPath())).findFirst().get();
        String expectedText = basicEntry.getResponse().getContent().getText();
        System.out.println(StringUtils.abbreviate(response.entity, 128));
        assertEquals("response content", expectedText, response.entity);
    }

    @Test
    public void startAsync_http_unmatchedReturns404() throws Exception {
        System.out.println("\n\nstartAsync_http_unmatchedReturns404\n");
        Path tempDir = temporaryFolder.getRoot().toPath();
        File harFile = new File(getClass().getResource("/http.www.example.com.har").toURI());
        ResponseSummary response = new ReplayManagerTester(tempDir, harFile)
                .exercise(newApacheClient(URI.create("http://www.google.com/")), reservedPort)
                .values().iterator().next();
        System.out.format("response: %s%n", response.statusLine);
        System.out.format("response text:%n%s%n", response.entity);
        assertEquals("status", HttpStatus.SC_NOT_FOUND, response.statusLine.getStatusCode());
    }

    private static class ApacheRecordingClient implements ReplayClient<Multimap<URI, ResponseSummary>> {
        private final ImmutableList<URI> urisToGet;

        public ApacheRecordingClient(Iterable<URI> urisToGet) {
            this.urisToGet = ImmutableList.copyOf(urisToGet);
        }

        private URI transformUri(URI uri) { // equivalent of switcheroo extension
            if ("https".equals(uri.getScheme())) {
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
        public Multimap<URI, ResponseSummary> useReplayServer(Path tempDir, HostAndPort proxy, Future<?> programFuture) throws Exception {
            Multimap<URI, ResponseSummary> result = ArrayListMultimap.create();
            try (CloseableHttpClient client = HttpClients.custom()
                    .setProxy(new HttpHost(proxy.getHost(), proxy.getPort()))
                    .build()) {
                for (URI uri : urisToGet) {
                    System.out.format("fetching %s%n", uri);
                    HttpGet get = new HttpGet(transformUri(uri));
                    if (programFuture.isDone() || programFuture.isCancelled()) {
                        throw new IllegalStateException("server no longer listening");
                    }
                    try (CloseableHttpResponse response = client.execute(get)) {
                        StatusLine statusLine = response.getStatusLine();
                        String entity = EntityUtils.toString(response.getEntity());
                        result.put(uri, new ResponseSummary(statusLine, entity));
                    }
                }
            }
            return result;
        }
    }

    private ReplayClient<Multimap<URI, ResponseSummary>> newApacheClient(URI uri) {
        return new ApacheRecordingClient(ImmutableList.of(uri));
    }

    private static class ResponseSummary {
        public final StatusLine statusLine;
        public final String entity;

        private ResponseSummary(StatusLine statusLine, String entity) {
            this.statusLine = statusLine;
            this.entity = entity;
        }
    }

}