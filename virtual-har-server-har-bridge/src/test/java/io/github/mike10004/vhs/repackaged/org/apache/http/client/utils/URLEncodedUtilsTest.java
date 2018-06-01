package io.github.mike10004.vhs.repackaged.org.apache.http.client.utils;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;

import static org.junit.Assert.assertEquals;

public class URLEncodedUtilsTest {

    @Test
    public void parse_string() {
        String s = "foo=bar";
        java.util.Map.Entry<String, String> pair = URLEncodedUtils.parse(s, StandardCharsets.UTF_8).iterator().next();
        assertEquals("value of param definition " + s, new SimpleImmutableEntry<>("foo", "bar"), pair);
    }

}