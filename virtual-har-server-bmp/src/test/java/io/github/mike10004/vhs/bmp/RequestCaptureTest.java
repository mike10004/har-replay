package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableMultimap;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RequestCaptureTest {

    @Test
    public void freeze() {
        RequestAccumulator input = new RequestAccumulator(HttpVersion.HTTP_1_1);
        input.setMethod("GET");
        String url = "http://www.example.com/";
        input.setUrl(url);
        ParsedRequest frozen = input.freeze().request;
        assertEquals("method", "GET", frozen.method.name());
        assertEquals("url", url, frozen.url.toString());
    }

    @Test
    public void queryStringToMultimapOfOptionals() {
        URI urlWithNoQuery = URI.create("http://example.com/hello");
        assertNull("no query", RequestAccumulator.queryStringToMultimapOfOptionals(urlWithNoQuery));
        URI urlWithEmptyQuery = URI.create("http://example.com/hello?");
        assertEquals("empty query", ImmutableMultimap.of(), RequestAccumulator.queryStringToMultimapOfOptionals(urlWithEmptyQuery));
        URI urlWithQuery = URI.create("http://example.com/hello?foo=bar");
        assertEquals("url with query", ImmutableMultimap.of("foo", Optional.of("bar")), RequestAccumulator.queryStringToMultimapOfOptionals(urlWithQuery));
        URI urlWithValuelessParam = URI.create("http://example.com/hello?foo");
        assertEquals("url with query", ImmutableMultimap.of("foo", Optional.empty()), RequestAccumulator.queryStringToMultimapOfOptionals(urlWithValuelessParam));
        URI urlWithEmptyValuedParam = URI.create("http://example.com/hello?foo=");
        assertEquals("url with query", ImmutableMultimap.of("foo", Optional.of("")), RequestAccumulator.queryStringToMultimapOfOptionals(urlWithEmptyValuedParam));
    }
}