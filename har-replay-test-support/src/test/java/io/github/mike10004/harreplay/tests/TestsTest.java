package io.github.mike10004.harreplay.tests;

import com.google.common.collect.ImmutableMap;
import fi.iki.elonen.NanoHTTPD;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.junit.Test;

import javax.annotation.Nullable;
import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.*;

public class TestsTest {

    @Test
    public void findOpenPort() throws Exception {
        int port = Tests.findOpenPort();
        assertTrue("port > 0: " + port, port > 0);
        assertTrue("port <= 65535: " + port, port <= 65535);
    }

    @Test
    public void fetchWithNoAcceptEncodingRequestHeader() throws Exception {
        int port = Tests.findOpenPort();
        checkState(port >= 0 && port < Short.MAX_VALUE * 2, "port out of range: %s", port);
        List<Map<String, String>> headerMaps = new ArrayList<>();
        NanoHTTPD nano = new NanoHTTPD(port) {
            @Override
            public Response serve(IHTTPSession session) {
                headerMaps.add(ImmutableMap.copyOf(session.getHeaders()));
                return NanoHTTPD.newFixedLengthResponse("hello");
            }
        };
        nano.start();
        try {
            ImmutableHttpResponse response = Tests.fetchWithNoAcceptEncodingRequestHeader(Proxy.NO_PROXY, URI.create("http://localhost:" + port + "/"));
            checkState(HttpStatus.SC_OK == response.status, "non-OK status %s", response.status);
        } finally {
            nano.stop();
        }
        assertEquals("num header maps", 1, headerMaps.size());
        Map<String, String> headers = headerMaps.iterator().next();
        @Nullable String acceptEncodingValue = Tests.getCaseInsensitively(headers, HttpHeaders.ACCEPT_ENCODING);
        assertNull("accept-encoding", acceptEncodingValue);
    }
}