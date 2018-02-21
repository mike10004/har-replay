package com.github.mike10004.harreplay;

import com.github.mike10004.harreplay.ReplayManagerTester.ReplayClient;
import com.github.mike10004.harreplay.ReplayServerConfig.Mapping;
import com.github.mike10004.harreplay.ReplayServerConfig.RegexHolder;
import com.github.mike10004.harreplay.ReplayServerConfig.Replacement;
import com.github.mike10004.harreplay.ReplayServerConfig.ResponseHeaderTransform;
import com.github.mike10004.harreplay.ReplayServerConfig.StringLiteral;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.harreplay.tests.Fixtures;
import io.github.mike10004.harreplay.tests.Fixtures.Fixture;
import io.github.mike10004.harreplay.tests.Fixtures.FixturesRule;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Predicate;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public abstract class ReplayManagerTest {

    @ClassRule
    public static FixturesRule fixturesRule = Fixtures.asRule();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void startAsync_http() throws Exception {
        System.out.println("\n\nstartAsync_http\n");
        File harFile = fixturesRule.getFixtures().http().harFile();
        testStartAsync(harFile, fixturesRule.getFixtures().http().startUrl());
    }

    @Test
    public void startAsync_https() throws Exception {
        System.out.println("\n\nstartAsync_https\n");
        Fixture fixture = fixturesRule.getFixtures().https();
        File harFile = fixture.harFile();
        ReplayServerConfig config = ReplayServerConfig.builder()
                .build();
        URI uri = fixture.startUrl();
        ApacheRecordingClient client = newApacheClient(uri, true);
        testStartAsync(harFile, uri, client, config, content -> content.contains(fixture.title()));
    }

    @Test
    public void literalMappingToCustomFile() throws Exception {
        File customContentFile = temporaryFolder.newFile();
        String customContent = "my custom string";
        Files.asCharSink(customContentFile, UTF_8).write(customContent);
        Fixture fixture = fixturesRule.getFixtures().http();
        URI uri = fixture.startUrl();
        ReplayServerConfig config = ReplayServerConfig.builder()
                .map(Mapping.literalToFile(uri.toString(), customContentFile))
                .build();
        File harFile = fixture.harFile();
        ApacheRecordingClient client = newApacheClient(uri, false);
        testStartAsync(harFile, uri, client, config, customContent::equals);
    }

    @Test
    public void literalReplacement() throws Exception {
        String replacementText = "I Eat Your Brain";
        Fixture fixture = fixturesRule.getFixtures().https();
        File harFile = fixture.harFile();
        URI uri = fixture.startUrl();
        ReplayServerConfig config = ReplayServerConfig.builder()
                .replace(Replacement.literal(fixture.title(), replacementText))
                .build();
        ApacheRecordingClient client = newApacheClient(uri, true);
        testStartAsync(harFile, uri, client, config, responseContent -> responseContent.contains(replacementText));
    }

    private static Predicate<String> matchHarResponse(Har har, URI uri) {
        HarEntry basicEntry = har.getLog().getEntries().stream().filter(entry -> uri.getPath().equals(URI.create(entry.getRequest().getUrl()).getPath())).findFirst().get();
        String expectedText = basicEntry.getResponse().getContent().getText();
        return expectedText::equals;
    }

    private void testStartAsync(File harFile, URI uri) throws Exception {
        Har har = new HarReader().readFromFile(harFile);
        Predicate<String> checker = matchHarResponse(har, uri);
        testStartAsync(harFile, uri, newApacheClient(uri, false), ReplayServerConfig.empty(), checker);
    }

    protected abstract com.github.mike10004.harreplay.ReplayManagerTester createTester(Path tempDir, File harFile, ReplayServerConfig config) throws IOException;

    private void testStartAsync(File harFile, URI uri, ApacheRecordingClient client, ReplayServerConfig config, Predicate<? super String> responseContentChecker) throws Exception {
        Path tempDir = temporaryFolder.getRoot().toPath();
        Multimap<URI, ResponseSummary> responses = createTester(tempDir, harFile, config).exercise(client, com.github.mike10004.harreplay.ReplayManagerTester.findHttpPortToUse());
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
        File harFile = fixturesRule.getFixtures().http().harFile();
        ResponseSummary response = createTester(tempDir, harFile, ReplayServerConfig.empty())
                .exercise(newApacheClient(URI.create("http://www.google.com/"), false), com.github.mike10004.harreplay.ReplayManagerTester.findHttpPortToUse())
                .values().iterator().next();
        System.out.format("response: %s%n", response.statusLine);
        System.out.format("response text:%n%s%n", response.entity);
        assertEquals("status", HttpStatus.SC_NOT_FOUND, response.statusLine.getStatusCode());
    }

    private static ResponseHeaderTransform createLocationHttpsToHttpTransform() {
        return ResponseHeaderTransform.valueByNameAndValue(StringLiteral.of(org.apache.http.HttpHeaders.LOCATION),
                RegexHolder.of("^https://(.+)"), StringLiteral.of("http://$1"));
    }

    @Test
    public void startAsync_https_transformLocationResponseHeader() throws Exception {
        System.out.println("\n\nstartAsync_https_transformLocationResponseHeader\n");
        Fixture fixture = fixturesRule.getFixtures().httpsRedirect();
        File harFile = fixture.harFile();
        ReplayServerConfig config = ReplayServerConfig.builder()
                .transformResponse(createLocationHttpsToHttpTransform())
                .build();
        URI uri = fixture.startUrl();
        ApacheRecordingClient client = newApacheClient(uri, true);
        testStartAsync(harFile, uri, client, config, content -> content.contains(fixture.title()));
    }

    private static class ApacheRecordingClient implements ReplayClient<Multimap<URI, ResponseSummary>> {

        private final ImmutableList<URI> urisToGet;
        private final boolean transformHttpsToHttp;

        ApacheRecordingClient(Iterable<URI> urisToGet, boolean transformHttpsToHttp) {
            this.urisToGet = ImmutableList.copyOf(urisToGet);
            this.transformHttpsToHttp = transformHttpsToHttp;
        }

        private URI transformUri(URI uri) { // equivalent of switcheroo extension
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
        public Multimap<URI, ResponseSummary> useReplayServer(Path tempDir, HostAndPort proxy, ReplaySessionControl sessionControl) throws Exception {
            Multimap<URI, ResponseSummary> result = ArrayListMultimap.create();
            try (CloseableHttpClient client = HttpClients.custom()
                    .setProxy(new HttpHost(proxy.getHost(), proxy.getPort()))
                    .setRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
                    .build()) {
                for (URI uri : urisToGet) {
                    System.out.format("fetching %s%n", uri);
                    HttpGet get = new HttpGet(transformUri(uri));
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

    private ApacheRecordingClient newApacheClient(URI uri, boolean transformHttpsToHttp) {
        return new ApacheRecordingClient(ImmutableList.of(uri), transformHttpsToHttp);
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