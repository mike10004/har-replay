package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.junit.Test;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

public class HarResponseDataTransformerTest {

    @Test
    public void replaceContentType() {
        MediaType originalContentType = MediaType.parse("text/plain");
        Iterable<Map.Entry<String, String>> originalHeaders = Collections.singleton(new AbstractMap.SimpleImmutableEntry<>(HttpHeaders.CONTENT_TYPE, originalContentType.toString()));
        HarResponseData responseData = HarResponseData.of(originalHeaders, originalContentType, null);
        MediaType newContentType = originalContentType.withCharset(UTF_8);
        HarResponseData transformed = responseData.transformer()
                .replaceContentType(newContentType)
                .transform();
        assertEquals("contentType getter", newContentType, transformed.getContentType());
        String actualNewContentTypeHeaderValue = transformed.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
        assertNotNull("expect header value present", actualNewContentTypeHeaderValue);
        assertEquals("header value parsed", newContentType, MediaType.parse(actualNewContentTypeHeaderValue));
    }

    @Test
    public void replaceMultipleHeaders() {
        String name1 = "Name1", valuePre1 = "january", valuePost1 = "february";
        String name2 = "Name2", valuePre2 = "march", valuePost2 = "april";
        String name3 = "Name3", value3 = "may";
        Iterable<Map.Entry<String, String>> originalHeaders = ImmutableMap.<String, String>builder()
                .put(name1, valuePre1)
                .put(name2, valuePre2)
                .put(name3, value3)
                .build().entrySet();
        HarResponseData responseData = HarResponseData.of(originalHeaders, null, null);
        HarResponseData transformed = responseData.transformer()
                .replaceHeader(name1, valuePost1)
                .replaceHeader(name2, valuePost2)
                .transform();
        assertEquals("3", value3, transformed.getFirstHeaderValue(name3));
        assertEquals("2", valuePost2, transformed.getFirstHeaderValue(name2));
        assertEquals("1", valuePost1, transformed.getFirstHeaderValue(name1));
    }

    @Test
    public void filterHeaders() throws Exception {
        Iterable<Map.Entry<String, String>> originalHeaders = ImmutableMap.<String, String>builder()
                .put("a", "1")
                .put("b", "2")
                .put("c", "3")
                .build().entrySet();
        HarResponseData responseData = HarResponseData.of(originalHeaders, null, null);
        HarResponseData transformed = responseData.transformer()
                .filterHeaders(header -> !"b".equalsIgnoreCase(header.getKey()))
                .transform();
        assertEquals("a", "1", transformed.getFirstHeaderValue("a"));
        assertEquals("c", "3", transformed.getFirstHeaderValue("c"));
        assertNull("b", transformed.getFirstHeaderValue("b"));
    }
}