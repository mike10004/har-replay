package io.github.mike10004.vhs.bmp;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.testsupport.Tests;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.easymock.EasyMock;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResponseManufacturingFilterTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void captureRequest() throws IOException {
        String bodyContent = "hello, world";
        Charset bodyCharset = StandardCharsets.UTF_8;
        ByteBuf content = Unpooled.wrappedBuffer(bodyContent.getBytes(bodyCharset));
        String url = "http://www.example.com/foo?bar=baz";
        DefaultFullHttpRequest littleRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, url, content);
        String contentType = MediaType.PLAIN_TEXT_UTF_8.withCharset(bodyCharset).toString();
        littleRequest.headers().set(HttpHeaders.CONTENT_TYPE, contentType);
        ResponseManufacturingFilter filter = new ResponseManufacturingFilter(littleRequest, createChannelHandlerContext(false), EasyMock.createMock(BmpResponseManufacturer.class), EasyMock.createMock(BmpResponseListener.class));
        filter.captureRequest(littleRequest);
        RequestCapture bmpRequest = filter.freezeRequestCapture();
        ParsedRequest actual = bmpRequest.request;
        byte[] actualBody;
        try (InputStream bodyIn = actual.openBodyStream()) {
            actualBody = ByteStreams.toByteArray(bodyIn);
        }
        assertEquals("body", bodyContent, new String(actualBody, bodyCharset));
        assertEquals("method", "POST", actual.method.name());
        assertEquals("url", url, actual.url.toString());
        assertEquals("content-type header", contentType, actual.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE));
    }

    @SuppressWarnings("SameParameterValue")
    private static ChannelHandlerContext createChannelHandlerContext(boolean https) {
        ChannelHandlerContext ctx = EasyMock.createNiceMock(ChannelHandlerContext.class);
        EasyMock.expect(ctx.attr(EasyMock.anyObject())).andReturn(new AtomicRefAttr<>(new AtomicReference<>(https)));
        EasyMock.replay(ctx);
        return ctx;
    }

    private static class AtomicRefAttr<T> implements Attribute<T> {
        private final AtomicReference<T> ref;

        public AtomicRefAttr(AtomicReference<T> ref) {
            this.ref = ref;
        }

        @Override
        public AttributeKey<T> key() {
            throw new UnsupportedOperationException();
        }

        @Override
        public T get() {
            return ref.get();
        }

        @Override
        public T setIfAbsent(T value) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("deprecation")
        public T getAndRemove() {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("deprecation")
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(T newValue) {
            ref.set(newValue);
        }

        @Override
        public boolean compareAndSet(T expect, T update) {
            return ref.compareAndSet(expect, update);
        }

        @Override
        public T getAndSet(T newValue) {
            return ref.getAndSet(newValue);
        }
    }

    @Test
    public void reconstructUrlFromFullUrlAndHostHeader() throws Exception {
        String[][] testCases = {
                new String[]{"https://localhost:36591/foo", "www.example.com", "https://www.example.com/foo"},
                new String[]{"https://localhost:36591/foo", "www.example.com:5688", "https://www.example.com:5688/foo"},
                new String[]{"https://localhost:36591/foo", "www.example.com:443", "https://www.example.com/foo"},
                new String[]{"http://www.example.com/foo", "www.example.com", "http://www.example.com/foo"},
                new String[]{"http://www.example.com/foo", "www.example.com:80", "http://www.example.com/foo"},
                new String[]{"http://www.example.com:8080/foo", "www.example.com:8080", "http://www.example.com:8080/foo"},
                new String[]{"http://localhost:36591/foo", "www.example.com:8080", "http://localhost:36591/foo"},
        };
        List<String> failures = new ArrayList<>();
        for (String[] testCase : testCases) {
            String fullUrl = testCase[0], hostHeader = testCase[1], expected = testCase[2];
            boolean https = "https".equalsIgnoreCase(URI.create(fullUrl).getScheme());
            HttpRequest mockRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "");
            ResponseManufacturingFilter filter = new ResponseManufacturingFilter(mockRequest, createChannelHandlerContext(https), EasyMock.createMock(BmpResponseManufacturer.class), EasyMock.createMock(BmpResponseListener.class));
            String actual = filter.reconstructUrlFromFullUrlAndHostHeader(fullUrl, hostHeader);
            String msg = String.format("%s reconstructed from %s and Host: %s", actual, fullUrl, hostHeader);
            if (!Objects.equals(expected, actual)) {
                failures.add(String.format("FAIL: %s (expected %s)", msg, expected));
            } else {
                System.out.println(msg);
            }
        }
        failures.forEach(System.out::println);
        assertEquals("failures", Collections.emptyList(), failures);
    }

    @Test
    public void captureRequest_https() throws Exception {
        ImmutableHttpResponse alwaysResponse = ImmutableHttpResponse.builder(201)
                .content(MediaType.PLAIN_TEXT_UTF_8, CharSource.wrap("not a real server").asByteSource(StandardCharsets.UTF_8))
                .build();
        BmpResponseManufacturer responseManufacturer = new BmpResponseManufacturer() {
            @Override
            public ResponseCapture manufacture(RequestCapture capture) {
                return ResponseCapture.matched(new BmpHttpAssistant().constructResponse(capture, alwaysResponse));
            }
        };
        BrowsermobVhsConfig vhsConfig = BrowsermobVhsConfig.builder(responseManufacturer)
                .tlsEndpointFactory(NanohttpdTlsEndpointFactory.create(BmpTests.generateKeystoreForUnitTest("localhost"), null))
                .scratchDirProvider(ScratchDirProvider.under(temporaryFolder.getRoot().toPath()))
                .build();
        List<RequestCapture> requests = Collections.synchronizedList(new ArrayList<>());
        BrowsermobVirtualHarServer server = new BrowsermobVirtualHarServer(vhsConfig) {
            @Override
            ResponseManufacturingFiltersSource createFirstFiltersSource(BmpResponseManufacturer responseManufacturer, HostRewriter hostRewriter, BmpResponseListener bmpResponseListener, ResponseManufacturingFiltersSource.PassthruPredicate passthruPredicate) {
                return new ResponseManufacturingFiltersSource(responseManufacturer, hostRewriter, bmpResponseListener, passthruPredicate) {
                    @Override
                    ResponseManufacturingFilter createResponseManufacturingFilter(HttpRequest originalRequest, ChannelHandlerContext ctx, BmpResponseManufacturer responseManufacturer, BmpResponseListener bmpResponseListener) {
                        return new ResponseManufacturingFilter(originalRequest, ctx, responseManufacturer, bmpResponseListener) {
                            @Override
                            RequestCapture freezeRequestCapture() {
                                RequestCapture frozen = super.freezeRequestCapture();
                                requests.add(frozen);
                                return frozen;
                            }
                        };
                    }
                };
            }
        };
        URI requestUri = URI.create("https://www.example.com/foo");
        StatusLine responseStatus;
        try (VirtualHarServerControl ctrl = server.start()) {
            try (CloseableHttpClient client = Tests.buildBlindlyTrustingHttpClient(ctrl.getSocketAddress())) {
                HttpGet request = new HttpGet(requestUri);
                try (CloseableHttpResponse response = client.execute(request)) {
                    responseStatus = response.getStatusLine();
                }
            }
        }
        assertEquals("response status", alwaysResponse.status, responseStatus.getStatusCode());
        assertEquals("num requests parsed", 1, requests.size());
        ParsedRequest parsedRequest = requests.iterator().next().request;
        assertEquals("parsed request uri", requestUri, parsedRequest.url);
    }

    @Test
    public void isDefaultPortForScheme() {
        assertTrue(ResponseManufacturingFilter.isDefaultPortForScheme("http", 80));
        assertTrue(ResponseManufacturingFilter.isDefaultPortForScheme("https", 443));
        assertFalse(ResponseManufacturingFilter.isDefaultPortForScheme("https", 8443));
        assertFalse(ResponseManufacturingFilter.isDefaultPortForScheme("http", 8080));
        assertFalse(ResponseManufacturingFilter.isDefaultPortForScheme("notarealscheme", 0));
        assertFalse(ResponseManufacturingFilter.isDefaultPortForScheme("notarealscheme", 1003));
        assertFalse(ResponseManufacturingFilter.isDefaultPortForScheme(null, 80));
    }
}