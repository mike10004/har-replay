package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

public class WrappingResponseEncodingTest {

    @Test
    public void modifyContentEncodingHeaderInBrotliEncodedResponse_noAcceptEncodingHeader() throws Exception {
        modifyContentEncodingHeaderInBrotliEncodedResponse(null);
    }

    @Test
    public void modifyContentEncodingHeaderInBrotliEncodedResponse_brotliAccepted() throws Exception {
        modifyContentEncodingHeaderInBrotliEncodedResponse(HttpContentCodecs.CONTENT_ENCODING_BROTLI);
    }

    private void modifyContentEncodingHeaderInBrotliEncodedResponse(String acceptEncodingHeaderValue) throws Exception {
        String text = "hello, world";
        String contentType = "text/plain";
        Collection<Map.Entry<String, String>> originalHeaders = ImmutableMultimap.of(HttpHeaders.CONTENT_ENCODING, HttpContentCodecs.CONTENT_ENCODING_IDENTITY,
                HttpHeaders.CONTENT_TYPE, contentType).entries();
        HarResponseData plain = HarResponseData.of(originalHeaders, MediaType.parse(contentType), CharSource.wrap(text).asByteSource(US_ASCII));
        HarResponseEncoding encoding = WrappingResponseEncoding.fromHeaderValues(HttpContentCodecs.CONTENT_ENCODING_BROTLI, acceptEncodingHeaderValue);
        HarResponseData transformed = encoding.transformUnencoded(plain);
        String actualEncoding = transformed.getFirstHeaderValue(HttpHeaders.CONTENT_ENCODING);
        assertEquals("value", HttpContentCodecs.CONTENT_ENCODING_IDENTITY, actualEncoding);
        long numEncodingHeaders = ImmutableList.copyOf(transformed.headers()).stream().filter(entry -> HttpHeaders.CONTENT_ENCODING.equalsIgnoreCase(entry.getKey())).count();
        assertEquals("num encoding headers", 1, numEncodingHeaders);
        // TODO check charset in content-type
        String actualText = transformed.getBody().asCharSource(US_ASCII).read();
        assertEquals("text", text, actualText);
    }

    @Test
    public void testResponseEncodedButAcceptEncodingDisallows() throws Exception {
        String json = Resources.toString(getClass().getResource("/gzipped-response.json"), UTF_8);
        JsonObject response = new JsonParser().parse(json).getAsJsonObject();
        JsonObject content = response.getAsJsonObject("content");
        String contentEncodingHeaderValue = HarBridgeTests.getFirstHeaderValueFromNameValuePairs(response.getAsJsonArray("headers"), HttpHeaders.CONTENT_ENCODING);
        String text = content.get("text").getAsString();
        TypedContent result = Hars.translateResponseContent(content.get("mimeType").getAsString(),
                text,
                response.get("bodySize").getAsLong(),
                content.get("size").getAsLong(),
                contentEncodingHeaderValue,
                null, null, UTF_8);
        String responseText = result.asByteSource().asCharSource(UTF_8).read();
        assertEquals("text", text, responseText);

    }

    private void test_canServeOriginalResponseContentEncoding(boolean expected, String contentEncoding, String acceptEncoding) {
        List<String> contentEncodings = HttpContentCodecs.parseEncodings(contentEncoding);
        boolean actual = WrappingResponseEncoding.canServeOriginalResponseContentEncoding(contentEncodings, acceptEncoding);
        assertEquals(String.format("expect %s for Content-Encoding: %s with Accept-Encoding: %s", expected, contentEncoding, acceptEncoding), expected, actual);
    }

    @Test
    public void canServeOriginalResponseContentEncoding() {
        test_canServeOriginalResponseContentEncoding(true, "gzip", "gzip");
        test_canServeOriginalResponseContentEncoding(true, "gzip", "deflate, gzip;q=1.0, *;q=0.5");
        test_canServeOriginalResponseContentEncoding(true, "gzip", "gzip, deflate, br");
        test_canServeOriginalResponseContentEncoding(true, "gzip, br", "gzip, deflate, br");
        test_canServeOriginalResponseContentEncoding(false, "gzip", null);
        test_canServeOriginalResponseContentEncoding(false, "gzip", "");
        test_canServeOriginalResponseContentEncoding(false, "gzip", "deflate;q=1.0, gzip;q=0.0, *;q=0.5");
        test_canServeOriginalResponseContentEncoding(true, null, null);
        test_canServeOriginalResponseContentEncoding(true, null, "");
        test_canServeOriginalResponseContentEncoding(true, "", null);
        test_canServeOriginalResponseContentEncoding(true, "", "");
        test_canServeOriginalResponseContentEncoding(true, "identity", null);
        test_canServeOriginalResponseContentEncoding(true, null, "gzip, deflate, br");
        test_canServeOriginalResponseContentEncoding(true, "", "gzip, deflate, br");
        test_canServeOriginalResponseContentEncoding(true, "identity", "gzip, deflate, br");
    }

}