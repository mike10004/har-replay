package com.github.mike10004.harreplay;

import com.github.mike10004.harreplay.ReplayManagerTester.ReplayClient;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ReplayManagerTest {

    private static final String SYSPROP_SERVER_REPLAY_PORT = "server-replay.port"; // see pom.xml build-helper-plugin

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static int reservedPort = 0;

    @BeforeClass
    public static void findReservedPort() throws IOException {
        String portStr = System.getProperty(SYSPROP_SERVER_REPLAY_PORT);
        if (Strings.isNullOrEmpty(portStr)) { // probably running with IDE test runner, not Maven
            System.err.println("unit test port not reserved by build process; will try to find open port");
            try (ServerSocket socket = new ServerSocket(0)) {
                reservedPort = socket.getLocalPort();
                System.out.format("found open port %d by opening socket %s%n", reservedPort, socket);
            }
        } else {
            reservedPort = Integer.parseInt(portStr);
        }
    }

    @Test
    public void startAsync_http() throws Exception {
        Path tempDir = temporaryFolder.getRoot().toPath();
        File harFile = new File(getClass().getResource("/http.www.example.com.har").toURI());
        ResponseSummary response = new ReplayManagerTester(tempDir, harFile)
                .exercise(newApacheClient(URI.create("http://www.example.com/")), reservedPort)
                .values().iterator().next();
        System.out.format("response: %s%n", response.statusLine);
        assertEquals("status", HttpStatus.SC_OK, response.statusLine.getStatusCode());
        Har har = HarIO.fromFile(harFile);
        HarEntry basicEntry = har.getLog().getEntries().stream().filter(entry -> "/".equals(URI.create(entry.getRequest().getUrl()).getPath())).findFirst().get();
        String expectedText = basicEntry.getResponse().getContent().getText();
        System.out.println(response.entity);
        assertEquals("response content", expectedText, response.entity);
    }

    @Test
    public void startAsync_http_unmatchedReturns404() throws Exception {
        Path tempDir = temporaryFolder.getRoot().toPath();
        File harFile = new File(getClass().getResource("/http.www.example.com.har").toURI());
        ResponseSummary response = new ReplayManagerTester(tempDir, harFile)
                .exercise(newApacheClient(URI.create("http://www.google.com/")), reservedPort)
                .values().iterator().next();
        System.out.format("response: %s%n", response.statusLine);
        System.out.format("response text:%n%s%n", response.entity);
        assertEquals("status", HttpStatus.SC_NOT_FOUND, response.statusLine.getStatusCode());
    }

    private static class ApacheRecordingClient implements ReplayClient<Map<URI, ResponseSummary>> {
        private final ImmutableList<URI> urisToGet;

        public ApacheRecordingClient(Iterable<URI> urisToGet) {
            this.urisToGet = ImmutableList.copyOf(urisToGet);
        }

        @Override
        public Map<URI, ResponseSummary> useReplayServer(Path tempDir, HostAndPort proxy) throws Exception {
            Map<URI, ResponseSummary> result = new LinkedHashMap<>();
            try (CloseableHttpClient client = HttpClients.custom()
                    .setProxy(new HttpHost(proxy.getHost(), proxy.getPort()))
                    .build()) {
                for (URI uri : urisToGet) {
                    HttpGet get = new HttpGet(uri);
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

    private ReplayClient<Map<URI, ResponseSummary>> newApacheClient(URI uri) {
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