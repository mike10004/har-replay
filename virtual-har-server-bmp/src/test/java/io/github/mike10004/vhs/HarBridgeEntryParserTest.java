package io.github.mike10004.vhs;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.harbridge.HarBridge;
import io.github.mike10004.vhs.harbridge.HarResponseData;
import io.github.mike10004.vhs.harbridge.HarResponseEncoding;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.junit.Assume;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class HarBridgeEntryParserTest {

    @Test
    public void parseResponse() throws Exception {
        Assume.assumeTrue("not yet implemented", false);
    }

    @Test
    public void constructRespondable_replaceContentLengthHeaderValue() throws Exception {
        String data = "hello, world";
        Charset charset = StandardCharsets.UTF_8;
        byte[] bytes = data.getBytes(charset);
        long originalContentLengthValue = bytes.length * 2;
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8.withCharset(charset);
        Map<String, String> originalHeaders = ImmutableMap.of(HttpHeaders.CONTENT_TYPE, contentType.toString(), HttpHeaders.CONTENT_LENGTH, String.valueOf(originalContentLengthValue));
        HttpRespondable respondable = HarBridgeEntryParser.constructRespondable(200, HarResponseData.of(originalHeaders.entrySet(), contentType, ByteSource.wrap(bytes)));
        Multimap<String, String> headersMm = ArrayListMultimap.create();
        respondable.streamHeaders().forEach(h -> {
            headersMm.put(h.getKey(), h.getValue());
        });
        Map<String, Collection<String>> headers = headersMm.asMap();
        Collection<String> values = headers.get(HttpHeaders.CONTENT_LENGTH);
        System.out.format("%s: %s%n", HttpHeaders.CONTENT_LENGTH, values);
        assertEquals("num values", 1, values.size());
        String finalContentLengthStr = values.iterator().next();
        assertNotNull("final content length header not found", finalContentLengthStr);
        long finalContentLength = Long.parseLong(finalContentLengthStr);
        assertEquals("content length", bytes.length, finalContentLength);
    }

    @Test
    public void replaceContentLengthHeaderValue_removeMultiple() throws Exception {
        checkState("content-length".equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH));
        Multimap<String, String> headers = ArrayListMultimap.create();
        headers.put("content-length", "100");
        headers.put("Content-Length", "150");
        headers.put("Content-Type", "application/octet-stream");
        HarBridgeEntryParser.replaceContentLength(headers, null);
        assertEquals("headers remaining", ImmutableMultimap.of("Content-Type", "application/octet-stream"), headers);
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @Test
    public void replaceHeaderValue_noReplacementIfSameValue() throws Exception {
        Multimap<String, Long> headers = ArrayListMultimap.create();
        long stamp = System.currentTimeMillis();
        Long val1 = new Long(stamp);
        Long val2 = new Long(stamp);
        headers.put("a", val1);
        headers.put("b", 0L);
        HarBridgeEntryParser.replaceHeaders(headers, "a", val2);
        assertSame(val1, headers.get("a").iterator().next());
        assertFalse(headers.get("b").isEmpty());
    }

    @Test
    public void parseGetRequest() throws IOException {
        HarBridgeEntryParser<FakeHarEntry> parser = HarBridgeEntryParser.withPlainEncoder(new FakeHarBridge());
        URI url = URI.create("http://www.example.com/");
        FakeHarEntry something = FakeHarEntry.request("GET", url.toString());
        ParsedRequest request = parser.parseRequest(something);
        assertEquals("method", HttpMethod.GET, request.method);
        assertEquals("url", url, request.url);
    }

    @Test
    public void parseConnectRequest() throws Exception {
        HarBridgeEntryParser<FakeHarEntry> parser = HarBridgeEntryParser.withPlainEncoder(new FakeHarBridge());
        URI url = new URI(null, null, "www.example.com", 443, null, null, null);
        FakeHarEntry something = FakeHarEntry.request("CONNECT", "www.example.com:443");
        ParsedRequest request = parser.parseRequest(something);
        assertEquals("method", HttpMethod.CONNECT, request.method);
        assertNull("scheme null", request.url.getScheme());
        assertEquals("port 443", 443, request.url.getPort());
        assertEquals("host", "www.example.com", request.url.getHost());
        assertEquals("url", url, request.url);
    }

    private static class FakeHarBridge implements HarBridge<FakeHarEntry> {

        @Override
        public String getRequestMethod(FakeHarEntry entry) {
            return entry.getRequestMethod();
        }

        @Override
        public String getRequestUrl(FakeHarEntry entry) {
            return entry.getRequestUrl();
        }

        @Override
        public Stream<Map.Entry<String, String>> getRequestHeaders(FakeHarEntry entry) {
            return entry.getRequestHeaders().stream();
        }

        @Nullable
        @Override
        public ByteSource getRequestPostData(FakeHarEntry entry) throws IOException {
            return entry.getRequestPostData();
        }

        @Override
        public int getResponseStatus(FakeHarEntry entry) {
            return entry.getResponseStatus();
        }

        @Override
        public HarResponseData getResponseData(ParsedRequest request, FakeHarEntry entry, HarResponseEncoding encoder) throws IOException {
            return HarResponseData.of(entry.getResponseHeaders(), entry.responseContentType, ByteSource.wrap(entry.getResponseBody() == null ? new byte[0] : entry.getResponseBody()));
        }
    }
    
    @SuppressWarnings("unused")
    private static class FakeHarEntry {

        private final String requestMethod, requestUrl;
        private final List<Map.Entry<String, String>> requestHeaders;
        private final ByteSource requestPostData;
        private final int responseStatus;
        private final List<Map.Entry<String, String>> responseHeaders;
        private final byte[] responseBody;
        private final MediaType responseContentType;

        public static FakeHarEntry request(String requestMethod, String requestUrl) {
            return request(requestMethod, requestUrl, Collections.emptyList(), null);
        }

        public static FakeHarEntry request(String requestMethod, String requestUrl, Collection<Entry<String, String>> requestHeaders, ByteSource requestPostData) {
            return new FakeHarEntry(requestMethod, requestUrl, requestHeaders, requestPostData, -1, null, null, null);
        }

        public FakeHarEntry(String requestMethod, String requestUrl, Collection<Entry<String, String>> requestHeaders, ByteSource requestPostData, int responseStatus, Collection<Entry<String, String>> responseHeaders, byte[] responseBody, MediaType responseContentType) {
            this.requestMethod = requestMethod;
            this.requestUrl = requestUrl;
            this.requestHeaders = requestHeaders == null ? ImmutableList.of() : ImmutableList.copyOf(requestHeaders);
            this.requestPostData = requestPostData == null ? ByteSource.empty() : requestPostData;
            this.responseStatus = responseStatus;
            this.responseHeaders = responseHeaders == null ? ImmutableList.of() : ImmutableList.copyOf(responseHeaders);
            this.responseBody = responseBody;
            this.responseContentType = responseContentType;
        }

        public String getRequestMethod() {
            return requestMethod;
        }

        
        public String getRequestUrl() {
            return requestUrl;
        }

        
        public List<Map.Entry<String, String>> getRequestHeaders() {
            return requestHeaders;
        }

        
        public List<Map.Entry<String, String>> getResponseHeaders() {
            return responseHeaders;
        }

        public ByteSource getRequestPostData() throws IOException {
            return requestPostData;
        }

        public byte[] getResponseBody() throws IOException {
            return responseBody;
        }

        
        public MediaType getResponseContentType() {
            return responseContentType;
        }

        
        public int getResponseStatus() {
            return responseStatus;
        }
        
    }

    @Test
    public void parseUrl_normal() throws Exception {
        String host = "example.com";
        int port = 6789;
        String url = HostAndPort.fromParts(host, port).toString();
        URI actual =  HarBridgeEntryParser.withPlainEncoder(new FakeHarBridge()).parseUrl(HttpMethod.CONNECT, url);
        assertEquals("parse results", parsedConnectUri(host, port), actual);
    }

    private static URI parsedConnectUri(String host, int port) throws URISyntaxException {
        return new URI(null, null, host, port, null, null, null);
    }

    @Test
    public void parseUrl_strangeConnectUrl() throws Exception {
        String host = "abc.def.ghijklm.com";
        String url = "https://" + host;
        URI actual = HarBridgeEntryParser.withPlainEncoder(new FakeHarBridge()).parseUrl(HttpMethod.CONNECT, url);
        URI expected = parsedConnectUri(host, 443);
        assertEquals("parse result", expected, actual);
    }
}