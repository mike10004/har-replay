package com.github.mike10004.harreplay;

import com.github.mike10004.harreplay.ReplayManagerTester.ReplayClient;
import com.github.mike10004.harreplay.ServerReplayConfig.Mapping;
import com.github.mike10004.harreplay.ServerReplayConfig.Replacement;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ReplayManagerTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

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

    @Test
    public void literalMappingToCustomFile() throws Exception {
        File customContentFile = temporaryFolder.newFile();
        String customContent = "my custom string";
        Files.write(customContent, customContentFile, UTF_8);
        URI uri = URI.create("http://www.example.com/");
        ServerReplayConfig config = ServerReplayConfig.builder()
                .map(Mapping.literalToFile(uri.toString(), customContentFile))
                .build();
        File harFile = new File(getClass().getResource("/http.www.example.com.har").toURI());
        testStartAsync(harFile, uri, config, customContent::equals);
    }

    @Test
    public void literalReplacement() throws Exception {
        String replacementText = "I Eat Your Brain";
        File harFile = ReplayManagerTester.getHttpsExampleFile();
        URI uri = URI.create("https://www.example.com/");
        ServerReplayConfig config = ServerReplayConfig.builder()
                .replace(Replacement.literal(ReplayManagerTester.getHttpsExamplePageTitle(), replacementText))
                .build();
        testStartAsync(harFile, uri, config, responseContent -> responseContent.contains(replacementText));
    }

    private static Predicate<String> matchHarResponse(Har har, URI uri) {
        HarEntry basicEntry = har.getLog().getEntries().stream().filter(entry -> uri.getPath().equals(URI.create(entry.getRequest().getUrl()).getPath())).findFirst().get();
        String expectedText = basicEntry.getResponse().getContent().getText();
        return expectedText::equals;
    }

    private void testStartAsync(File harFile, URI uri) throws Exception {
        Har har = HarIO.readFile(harFile);
        Predicate<String> checker = matchHarResponse(har, uri);
        testStartAsync(harFile, uri, ServerReplayConfig.empty(), checker);
    }

    private void testStartAsync(File harFile, URI uri, ServerReplayConfig config, Predicate<? super String> responseContentChecker) throws Exception {
        Path tempDir = temporaryFolder.getRoot().toPath();
        Multimap<URI, ResponseSummary> responses = new ReplayManagerTester(tempDir, harFile) {
            @Override
            protected ServerReplayConfig configureReplayModule() {
                return config;
            }
        }.exercise(newApacheClient(uri), ReplayManagerTester.findPortToUse());
        Collection<ResponseSummary> responsesForUri = responses.get(uri);
        assertFalse("no response for uri " + uri, responsesForUri.isEmpty());
        ResponseSummary response = responsesForUri.iterator().next();
        System.out.format("response: %s%n", response.statusLine);
        assertEquals("status", HttpStatus.SC_OK, response.statusLine.getStatusCode());
        System.out.println(StringUtils.abbreviate(response.entity, 128));
        assertEquals("response content", true, responseContentChecker.test(response.entity));
    }

    @Test
    public void startAsync_http_unmatchedReturns404() throws Exception {
        System.out.println("\n\nstartAsync_http_unmatchedReturns404\n");
        Path tempDir = temporaryFolder.getRoot().toPath();
        File harFile = ReplayManagerTester.getHttpExampleFile();
        ResponseSummary response = new ReplayManagerTester(tempDir, harFile)
                .exercise(newApacheClient(URI.create("http://www.google.com/")), ReplayManagerTester.findPortToUse())
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