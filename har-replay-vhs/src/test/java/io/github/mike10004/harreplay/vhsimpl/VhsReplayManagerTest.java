package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import io.github.mike10004.harreplay.ReplaySessionControl;
import io.github.mike10004.harreplay.tests.Fixtures.Fixture;
import io.github.mike10004.harreplay.tests.ImmutableHttpResponse;
import io.github.mike10004.harreplay.tests.ReplayManagerTestBase;
import io.github.mike10004.harreplay.tests.ReplayManagerTester;
import io.github.mike10004.harreplay.tests.Tests;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.CookieHandler;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.Method;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.ResponseException;
import io.github.mike10004.nanochamp.server.NanoServer;
import io.github.mike10004.nanochamp.server.NanoServer.RequestHandler;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class VhsReplayManagerTest extends ReplayManagerTestBase {

    private static final String SYSPROP_RESERVED_PORT = "har-replay.unit-tests.reservedPort";

    @Override
    protected ReplayManagerTester createTester(Path tempDir, File harFile, ReplayServerConfig config) {
        io.github.mike10004.vhs.harbridge.Hars.class.getName();
        return new VhsReplayManagerTester(tempDir, harFile, config);
    }

    @Override
    protected String getReservedPortSystemPropertyName() {
        return SYSPROP_RESERVED_PORT;
    }

    @Test
    public void unmatchedDoesNotHang() throws Exception {
        ReplayManager replayManager = new VhsReplayManager();
        File harFile = temporaryFolder.newFile();
        Resources.asByteSource(getClass().getResource("/empty.har")).copyTo(Files.asByteSink(harFile));
        ReplaySessionConfig config = ReplaySessionConfig.builder(temporaryFolder.getRoot().toPath())
                .build(harFile);
        int numTrials = 12;
        checkNanoResponses();
        URI url = URI.create("http://www.example.com/");
        try (ReplaySessionControl ctrl = replayManager.start(config)) {
            HostAndPort proxyAddress = ctrl.getSocketAddress();
            for (int i = 0; i < numTrials; i++) {
                System.out.format("[%2d] fetching %s%n", i + 1, url);
                ImmutableHttpResponse rsp = Tests.fetch(proxyAddress, url);
                String actual = rsp.data.asCharSource(StandardCharsets.UTF_8).read();
                assertEquals("repsonse content", "404 Not Found", actual);
            }
        }
    }

    @Test
    public void acceptEncodingIsObeyed() throws Exception {
        ReplayManager replayManager = new VhsReplayManager();
        Fixture fixture = fixturesRule.getFixtures().http();
        File harFile = fixture.harFile();
        ReplaySessionConfig config = ReplaySessionConfig.builder(temporaryFolder.getRoot().toPath())
                .build(harFile);
        URI url = fixture.startUrl();
        try (ReplaySessionControl ctrl = replayManager.start(config)) {
            HostAndPort proxyAddress = ctrl.getSocketAddress();
            System.out.format("fetching %s%n", url);
            ImmutableHttpResponse rsp = Tests.fetchWithNoAcceptEncodingRequestHeader(proxyAddress, url);
            String actual = rsp.data.asCharSource(StandardCharsets.UTF_8).read();
            boolean allAscii = ASCII.matchesAllOf(actual);
            if (!allAscii) {
                System.out.format("text \"%s\" has first non-ascii character at index %d%n", StringEscapeUtils.escapeJava(StringUtils.abbreviateMiddle(actual, "[...]", 64)), ASCII.negate().indexIn(actual));
            }
            assertTrue("response content all ascii", allAscii);
        }
    }

    private static final CharMatcher ASCII = CharMatcher.ascii();

    @Test
    public void checkNanoResponses() {
        RequestHandler rh = NanoServer.RequestHandler.getDefault();
        NanoHTTPD.Response rsp1 = rh.serve(session("http://www.example.com/x"));
        NanoHTTPD.Response rsp2 = rh.serve(session("http://www.example.com/y"));
        assertNotSame("responses should not be the same", rsp1, rsp2);
    }

    private static NanoHTTPD.IHTTPSession session(String url) {
        return new NanoHTTPD.IHTTPSession() {

            @SuppressWarnings("RedundantThrows")
            @Override
            public void execute() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public CookieHandler getCookies() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<String, String> getHeaders() {
                return ImmutableMap.of();
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public Method getMethod() {
                return NanoHTTPD.Method.GET;
            }

            @Override
            public Map<String, String> getParms() {
                return ImmutableMap.of();
            }

            @Override
            public Map<String, List<String>> getParameters() {
                return null;
            }

            @Override
            public String getQueryParameterString() {
                return null;
            }

            @Override
            public String getUri() {
                return url;
            }

            @SuppressWarnings("RedundantThrows")
            @Override
            public void parseBody(Map<String, String> map) throws IOException, ResponseException {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getRemoteIpAddress() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getRemoteHostName() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
