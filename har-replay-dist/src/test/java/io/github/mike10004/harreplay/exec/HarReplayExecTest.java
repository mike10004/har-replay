package io.github.mike10004.harreplay.exec;

import io.github.mike10004.subprocess.ProcessMonitor;
import io.github.mike10004.subprocess.ProcessResult;
import io.github.mike10004.subprocess.ScopedProcessTracker;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.google.common.math.LongMath;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class HarReplayExecTest extends HarReplayExecTestBase {

    private static final Duration SERVER_WAIT_DURATION = Tests.Settings.timeouts().get("HarReplayExecTest", Duration.ofSeconds(120));

    @ClassRule
    public static FixturesRule fixturesRule = Fixtures.asRule();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void execute_primaryPath() throws Exception {
        System.out.println("execute_primaryPath");
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
        System.out.println("execute_headerTransform");
        Fixture fixture = fixturesRule.getFixtures().httpsRedirect();
        URI uri = new URIBuilder(fixture.startUrl()).setScheme("http").build();
        System.out.format("starting at %s%n", uri);
        File configFile = temporaryFolder.newFile();
        ReplayServerConfig config = ReplayServerConfig.builder()
                .transformResponse(ReplayManagerTestBase.createLocationHttpsToHttpTransform())
                .build();
        Charset charset = UTF_8;
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
            int port = waitUntilFileNonempty(notifyFile, SERVER_WAIT_DURATION, Integer::parseInt);
            HostAndPort serverAddress = HostAndPort.fromParts("localhost", port);
            System.out.format("listening on %s%n", serverAddress);
            retVal = visitor.visit(serverAddress);
            // try an orderly termination
            monitor.destructor().sendTermSignal().await(100, TimeUnit.MILLISECONDS);
        }
        ProcessResult<String, String> result = monitor.await(0, TimeUnit.MILLISECONDS);
        System.out.println(result.content().stdout());
        return retVal;
    }

    private Multimap<URI, ImmutableHttpResponse> visitSite(HostAndPort replayServerAddress, Iterable<URI> urls) throws IOException {
        Multimap<URI, ImmutableHttpResponse> responses = ArrayListMultimap.create();
        for (URI uri : urls) {
            ImmutableHttpResponse response = Tests.fetch(replayServerAddress, uri);
            responses.put(uri, response);
        }
        return responses;
    }

    @SuppressWarnings("SameParameterValue")
    private String maybeRead(File file, Charset charset) {
        System.out.format("checking %s (length = %d)%n", file, file.length());
        try {
            return Files.asCharSource(file, charset).read();
        } catch (FileNotFoundException ignore) {
            return "";
        } catch (IOException e) {
            System.err.format("failed to read from existing file %s: %s%n", file, e.toString());
            return "";
        }
    }

    // https://stackoverflow.com/a/16251508/2657036
    @SuppressWarnings("SameParameterValue")
    private <T> T waitUntilFileNonempty(File file, Duration waitDuration, Function<? super String, T> transform) throws IOException, InterruptedException, TimeoutException {
        System.out.format("waiting until file becomes nonempty: %s%n", file);
        file = file.getAbsoluteFile();
        checkState(file.isFile(), "file is not yet created: %s", file);
        final Path targetFileAbsolutePath = file.toPath().toAbsolutePath();
        final Path parentDirectory = file.getParentFile().toPath();
        System.out.println(parentDirectory);
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            parentDirectory.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            long millisRemaining = waitDuration.toMillis();
            long millisStart = System.currentTimeMillis();
            while (millisRemaining > 0) {
                final WatchKey wk = watchService.poll(millisRemaining, TimeUnit.MILLISECONDS);
                if (wk != null) {
                    for (WatchEvent<?> event : wk.pollEvents()) {
                        if (StandardWatchEventKinds.OVERFLOW.equals(event.kind())) {
                            continue;
                        }
                        //we only register "ENTRY_MODIFY" so the context is always a Path.
                        final Path changed = parentDirectory.resolve((Path) event.context());
                        if (targetFileAbsolutePath.equals(changed.toAbsolutePath())) {
                            String contents = maybeRead(file, UTF_8);
                            if (!contents.trim().isEmpty()) {
                                return transform.apply(contents);
                            }
                        }
                    }
                } else {
                    break;
                }
                long millisWaited = LongMath.checkedSubtract(System.currentTimeMillis(), millisStart);
                millisRemaining = LongMath.checkedSubtract(waitDuration.toMillis(), millisWaited);
            }
        }
        throw new TimeoutException("timed out before watch service returned a poll event");
    }
}