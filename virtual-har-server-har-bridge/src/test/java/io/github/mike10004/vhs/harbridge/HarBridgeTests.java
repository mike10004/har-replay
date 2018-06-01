package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.zip.GZIPInputStream;

class HarBridgeTests {

    private HarBridgeTests() {

    }

    public static int countLengthOfCommonPrefix(byte[] a, byte[] b) {
        int n = 0;
        while (n < a.length && n < b.length) {
            if (a[n] != b[n]) {
                break;
            }
            n++;
        }
        return n;
    }

    public static ParsedRequest buildRequest(@Nullable String acceptEncodingHeaderValue) {
        Multimap<String, String> headers = ArrayListMultimap.create();
        if (acceptEncodingHeaderValue != null) {
            headers.put(HttpHeaders.ACCEPT_ENCODING, acceptEncodingHeaderValue);
        }
        return ParsedRequest.inMemory(HttpMethod.GET, URI.create("http://www.example.com/"), ImmutableMultimap.of(), headers, null);
    }

    public static byte[] gunzip(byte[] compressed) throws IOException {
        try (GZIPInputStream gzin = new GZIPInputStream(new ByteArrayInputStream(compressed), compressed.length * 2)) {
            return ByteStreams.toByteArray(gzin);
        }
    }

    @Nullable
    public static String getFirstHeaderValueFromNameValuePairs(JsonArray arrayOfNameValuePairs, String headerName) {
        return ImmutableList.copyOf(arrayOfNameValuePairs).stream()
                .map(JsonElement::getAsJsonObject)
                .filter(el -> headerName.equalsIgnoreCase(el.get("name").getAsString()))
                .map(el -> el.get("value").getAsString())
                .findFirst().orElse(null);
    }
}
