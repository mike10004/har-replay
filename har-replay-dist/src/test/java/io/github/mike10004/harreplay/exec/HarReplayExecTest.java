package io.github.mike10004.harreplay.exec;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ScopedProcessTracker;
import com.github.mike10004.xvfbmanager.Poller;
import com.github.mike10004.xvfbmanager.Poller.PollOutcome;
import com.github.mike10004.xvfbmanager.Poller.StopReason;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.tests.Fixtures;
import io.github.mike10004.harreplay.tests.Fixtures.Fixture;
import io.github.mike10004.harreplay.tests.Fixtures.FixturesRule;
import io.github.mike10004.harreplay.tests.ImmutableHttpResponse;
import io.github.mike10004.harreplay.tests.ReplayManagerTestBase;
import io.github.mike10004.harreplay.tests.Tests;
import io.github.mike10004.vhs.harbridge.HttpContentCodec;
import io.github.mike10004.vhs.harbridge.HttpContentCodecs;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.jsoup.Jsoup;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class HarReplayExecTest extends HarReplayExecTestBase {

    @ClassRule
    public static FixturesRule fixturesRule = Fixtures.asRule();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void execute_primaryPath() throws Exception {
        Fixture fixture = fixturesRule.getFixtures().http();
        URI url = fixture.startUrl();
        List<String> args = Collections.emptyList();
        Multimap<URI, ImmutableHttpResponse> responses = execute(fixture.harFile(), args, serverAddress -> visitSite(serverAddress, Collections.singleton(url)));
        checkState(responses.size() == 1, "only one response expected");
        ImmutableHttpResponse response = responses.values().iterator().next();
        String contentType = response.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
        System.out.format("content-type: %s%n", contentType);
        assertNotNull("value of content-type header", contentType);
        Charset charset = MediaType.parse(contentType).charset().or(StandardCharsets.ISO_8859_1);
        String html = response.data.asCharSource(charset).read();
        System.out.format("parsing html from %s...%n%s%n%n", url, StringUtils.abbreviateMiddle(html, "\n[...]\n", 256));
        String actualTitle = Jsoup.parse(html).title();
        if (!fixture.title().equals(actualTitle)) {
            String encoding = response.getFirstHeaderValue(HttpHeaders.CONTENT_ENCODING);
            if (encoding != null) {
                decodeAndDump(response.data.read(), encoding, charset);
                fail("content stream was compressed with " + encoding + " but we did not ask for that with an Accept-Encoding header");
            }
        }
        assertEquals("title", fixture.title(), actualTitle);
    }

    @Test
    public void execute_headerTransform() throws Exception {
        Fixture fixture = fixturesRule.getFixtures().httpsRedirect();
        URI uri = new URIBuilder(fixture.startUrl()).setScheme("http").build();
        System.out.format("starting at %s%n", uri);
        File configFile = temporaryFolder.newFile();
        ReplayServerConfig config = ReplayServerConfig.builder()
                .transformResponse(ReplayManagerTestBase.createLocationHttpsToHttpTransform())
                .build();
        Charset charset = StandardCharsets.UTF_8;
        try (Writer out = new OutputStreamWriter(new FileOutputStream(configFile), charset)) {
            HarReplayMain.createDefaultReplayServerConfigGson().toJson(config, out);
        }
        Files.asCharSource(configFile, charset).copyTo(System.out);
        System.out.println();
        List<String> args = Arrays.asList("--" + HarReplayMain.OPT_REPLAY_CONFIG, configFile.getAbsolutePath());
        ImmutableHttpResponse response = execute(fixture.harFile(), args, serverAddress -> {
            Proxy proxy = Tests.toProxy(serverAddress);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection(proxy);
            try {
                conn.setInstanceFollowRedirects(false);
                return Tests.captureResponse(conn);
            } finally {
                conn.disconnect();
            }
        });
        String locationHeaderValue = response.getFirstHeaderValue(HttpHeaders.LOCATION);
        assertNotNull("location header value", locationHeaderValue);
        System.out.format("%s: %s%n", HttpHeaders.LOCATION, locationHeaderValue);
        assertEquals("location header value scheme", "http", URI.create(locationHeaderValue).getScheme());
    }

    private static void decodeAndDump(byte[] bytes, String encoding, Charset contentTypeCharset) throws Exception {
        HttpContentCodec codec = HttpContentCodecs.getCodec(encoding);
        assertNotNull("codec", codec);
        bytes = codec.decompress(bytes);
        String html = new String(bytes, contentTypeCharset);
        System.out.format("parsing html from decompressed text...%n%s%n%n", StringUtils.abbreviateMiddle(html, "\n[...]\n", 256));
    }

    private interface Visitor<T> {
        T visit(HostAndPort serverAddress) throws IOException;
    }

    private List<String> buildArgs(List<String> firstArgs, File notifyFile, File harFile) {
        List<String> args = new ArrayList<>();
        Iterables.addAll(args, firstArgs);
        args.addAll(Arrays.asList("--" + HarReplayMain.OPT_NOTIFY, notifyFile.getAbsolutePath()));
        args.addAll(Arrays.asList("--" + HarReplayMain.OPT_SCRATCH_DIR, temporaryFolder.getRoot().getAbsolutePath()));
        args.add(harFile.getAbsolutePath());
        System.out.format("args = %s%n", args);
        return args;
    }

    private <T> T execute(File harFile, List<String> moreArgs, Visitor<T> visitor) throws IOException, InterruptedException, TimeoutException {
        File notifyFile = temporaryFolder.newFile();
        List<String> args = buildArgs(moreArgs, notifyFile, harFile);
        T retVal;
        ProcessMonitor<String, String> monitor;
        try (ScopedProcessTracker processTracker = new ScopedProcessTracker()) {
            monitor = execute(processTracker, args);
            HostAndPort serverAddress = pollUntilNotified(notifyFile);
            System.out.format("listening on %s%n", serverAddress);
            retVal = visitor.visit(serverAddress);
            // try an orderly termination
            monitor.destructor().sendTermSignal().timeout(100, TimeUnit.MILLISECONDS);
        }
        ProcessResult<String, String> result = monitor.await(0, TimeUnit.MILLISECONDS);
        System.out.println(result.content().stdout());
        return retVal;
    }

    @SuppressWarnings("SameParameterValue")
    private String maybeRead(File file, Charset charset) {
        System.out.format("checking %s (length = %d)%n", file, file.length());
        try {
            return Files.asCharSource(file, charset).read();
        } catch (IOException ignore) {
            return "";
        }
    }

    private HostAndPort pollUntilNotified(File notifyFile) throws InterruptedException {
        PollOutcome<Integer> outcome = new Poller<Integer>() {
            @Override
            protected PollAnswer<Integer> check(int pollAttemptsSoFar) {
                 String contents = maybeRead(notifyFile, HarReplayMain.NOTIFY_FILE_CHARSET);
                 if (!contents.trim().isEmpty()) {
                     return resolve(Integer.parseInt(contents));
                 }
                 return continuePolling();
            }
        }.poll(100, 50);
        checkState(outcome.reason == StopReason.RESOLVED, "not resolved: %s", outcome);
        checkNotNull(outcome.content, "resolved, so content should be non-null");
        return HostAndPort.fromParts("localhost", outcome.content);
    }

    private Multimap<URI, ImmutableHttpResponse> visitSite(HostAndPort replayServerAddress, Iterable<URI> urls) throws IOException {
        Multimap<URI, ImmutableHttpResponse> responses = ArrayListMultimap.create();
        for (URI uri : urls) {
            ImmutableHttpResponse response = Tests.fetch(replayServerAddress, uri);
            responses.put(uri, response);
        }
        return responses;
    }

}