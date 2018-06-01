package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.JsonPath;
import io.github.mike10004.vhs.repackaged.org.apache.http.NameValuePair;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HarsTest {


    @Test
    public void testGzippedHtml() throws Exception {
        String json = Resources.toString(getClass().getResource("/gzipped-response.json"), UTF_8);
        JsonObject response = new JsonParser().parse(json).getAsJsonObject();
        JsonObject content = response.getAsJsonObject("content");
        String contentEncodingHeaderValue = HarBridgeTests.getFirstHeaderValueFromNameValuePairs(response.getAsJsonArray("headers"), HttpHeaders.CONTENT_ENCODING);
        String text = content.get("text").getAsString();
        byte[] data = Hars.translateResponseContent(content.get("mimeType").getAsString(),
                text,
                response.get("bodySize").getAsLong(),
                content.get("size").getAsLong(),
                contentEncodingHeaderValue,
                null, null, UTF_8).asByteSource().read();
        String decompressedText = new String(data, UTF_8);
        assertEquals("text", text, decompressedText);
    }

    @Test
    public void getUncompressedContent() throws Exception {
        String HEX_BYTES = "E29C93"; // unicode check mark (not in ASCII or ISO-8859-1 charsets)
        byte[] bytes = BaseEncoding.base16().decode(HEX_BYTES);
        String text = new String(bytes, UTF_8);
        System.out.format("text: %s%n", text);
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8.withoutParameters();
        TypedContent translation = Hars.getUncompressedContent(contentType.toString(), text, null, null, null, null, null, StandardCharsets.UTF_8);
        ByteSource data = translation.asByteSource();
        String hexActual = BaseEncoding.base16().encode(data.read());
        assertEquals("result", HEX_BYTES, hexActual);
    }

    @Test
    public void adjustCharset() throws Exception {
        Table<String, Optional<Charset>, Charset> testCases = ImmutableTable.<String, Optional<Charset>, Charset>builder()
                .put("", Optional.of(US_ASCII), US_ASCII)
                .put("abcdef", Optional.of(US_ASCII), US_ASCII)
                .put("Fractions: " + decodeFromHex("BCBDBE", ISO_8859_1), Optional.of(US_ASCII), UTF_8)
                .put("abcdef", Optional.of(ISO_8859_1), ISO_8859_1)
                .put("abcdef", Optional.of(UTF_8), UTF_8)
                .put("abc \uD83C\uDCA1\uD83C\uDCA8\ud83c\udcd1\ud83c\udcd8\ud83c\udcd3", Optional.of(ISO_8859_1), UTF_8)
                .build();
        testCases.cellSet().forEach(cell -> {
            String text = cell.getRowKey();
            @Nullable Charset charset = cell.getColumnKey().orElse(null);
            Charset expected = cell.getValue();
            Charset actual = Hars.adjustCharset(text, charset, UTF_8);
            String description = String.format("\"%s\" with suggestion %s", StringEscapeUtils.escapeJava(text), charset);
            System.out.format("%s -> %s (expecting %s)%n", description, actual, expected);
            assertEquals(description, expected, actual);
        });
    }

    @SuppressWarnings("SameParameterValue")
    private static String decodeFromHex(String hexEncodedBytes, Charset charset) {
        byte[] bytes = BaseEncoding.base16().decode(hexEncodedBytes.toUpperCase());
        return new String(bytes, charset);
    }

    private static String getHeaderValueFromHar(Object doc, String headerName) {
        @SuppressWarnings("unchecked")
        String value = ((List<String>)JsonPath.read(doc, "$.log.entries[0].response.headers[?(@.name == '" + headerName + "')].value")).get(0);
        return value;
    }

    @Test
    public void getUncompressedContent_brotliEncodedTextInBase64() throws Exception {
        System.out.format("%n%n getUncompressedContent_brotliEncodedTextInBase64%n");
        String expectedContent = Resources.toString(getClass().getResource("/expected-response-body.json"), StandardCharsets.UTF_8);
        System.out.println(expectedContent);
        getUncompressedContent_brotli(getClass().getResource("/har-with-encoded-brotli-entry.json"), expectedContent);
    }

    @Nullable
    private static <T> T readJson(Object doc, String jsonPath) {
        try {
            return JsonPath.read(doc, jsonPath);
        } catch (com.jayway.jsonpath.PathNotFoundException ignore) {
            return null;
        }
    }

    @Test
    public void getUncompressedContent_brotliEncodedTextInText() throws Exception {
        System.out.format("%n%n getUncompressedContent_brotliEncodedTextInText%n");
        getUncompressedContent_brotli(getClass().getResource("/har-with-decoded-brotli-entry.json"), null);
    }

    private static void describe(String format, Object value) {
        if (value instanceof String) {
            value = String.format("\"%s\"", StringEscapeUtils.escapeJava(value.toString()));
        }
        if (value != null) {
            System.out.print(value.getClass().getSimpleName());
            System.out.print(" ");
        } else {
            System.out.print("Object ");
        }
        System.out.format(format, value);
    }

    @SuppressWarnings("UnusedReturnValue")
    private TypedContent getUncompressedContent_brotli(URL resource, @Nullable String expectedContent) throws IOException {
        String json = Resources.toString(resource, StandardCharsets.UTF_8);
        com.jayway.jsonpath.Configuration jsonPathConfig = com.jayway.jsonpath.Configuration.defaultConfiguration();
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) jsonPathConfig.jsonProvider().parse(json);
        String contentType = JsonPath.read(doc, "$.log.entries[0].response.content.mimeType");
        String text = JsonPath.read(doc, "$.log.entries[0].response.content.text");
        String contentEncodingHeaderValue = getHeaderValueFromHar(doc, "Content-Encoding");
        String harContentEncoding = readJson(doc, "$.log.entries[0].response.content.encoding");
        Number bodySize = JsonPath.read(doc, "$.log.entries[0].response.bodySize");
        Number contentSize = JsonPath.read(doc, "$.log.entries[0].response.content.size");
        String contentLengthHeaderValue = getHeaderValueFromHar(doc, "Content-Length");
        String comment = readJson(doc, "$.log.entries[0].response.content.comment");
        describe("contentType = %s;%n", contentType);
        describe("text = %s;%n", text);
        describe("contentEncodingHeaderValue = %s;%n", contentEncodingHeaderValue);
        describe("harContentEncoding = %s;%n", harContentEncoding);
        describe("bodySize = %s;%n", bodySize);
        describe("contentSize = %s;%n", contentSize);
        describe("comment = %s;%n", comment);
        describe("contentLengthHeaderValue = %s;%n", contentLengthHeaderValue);
        checkState("br".equals(contentEncodingHeaderValue), "precondition: encoding = br");
        checkState(bodySize != null, "precondition: bodySize != null");
        checkState(contentSize != null, "precondition: contentSize != null");
        TypedContent typedContent = Hars.translateResponseContent(contentType, text, bodySize.longValue(), contentSize.longValue(),
                contentEncodingHeaderValue, harContentEncoding, comment, UTF_8);
        System.out.format("actual: %s%n", typedContent);
        System.out.format("content text base64 length: %d%n", text.length());
        if ("base64".equalsIgnoreCase(harContentEncoding)) {
            System.out.format("content bytes length: %d%n", BaseEncoding.base64().decode(text).length);
        }
        if (expectedContent == null) {
            expectedContent = text;
        }
        System.out.format("expected decoded length: %d%n", expectedContent.length());
        String actualContent = typedContent.asByteSource().asCharSource(MediaType.parse(contentType).charset().or(StandardCharsets.ISO_8859_1)).read();
        assertEquals("uncompressed content", expectedContent, actualContent);
        return typedContent;
    }

    @Test
    public void sizeOfBase64DecodedByteSourceIsKnown() throws Exception {
        byte[] bytes = "hello, world".getBytes(StandardCharsets.US_ASCII);
        String base64 = BaseEncoding.base64().encode(bytes);
        ByteSource decodingSource = Hars.base64DecodingSource(base64);
        Optional<Long> sizeIfKnown = decodingSource.sizeIfKnown().toJavaUtil();
        System.out.format("%s size: %s%n", decodingSource, sizeIfKnown);
        assertTrue("size known", sizeIfKnown.isPresent());
        assertEquals("size", bytes.length, sizeIfKnown.get().longValue());
    }

    @Test
    public void complicatedTextEncodingIssue() throws Exception {
        String contentType = "text/javascript";
        String harContentEncoding = null;
        String contentEncodingHeaderValue = "br";
        String comment = "";
        String text = "let nbsp=\"\u00A0\";";
        byte[] expectedBytes = text.getBytes(UTF_8);
        String expectedBytesHex = BaseEncoding.base16().encode(expectedBytes);
        System.out.format("complicated text: \"%s\"%n", StringEscapeUtils.escapeJava(text));
        System.out.format("%s decoded with %s is: %s%n", expectedBytesHex, UTF_8, new String(expectedBytes, UTF_8));
        System.out.format("%s decoded with %s is: %s%n", expectedBytesHex, ISO_8859_1, new String(expectedBytes, ISO_8859_1));
        long contentSize = expectedBytes.length;
        long bodySize = expectedBytes.length;
        @SuppressWarnings("UnnecessaryLocalVariable")
        Charset EX_MACHINA_CHARSET = UTF_8;
        @SuppressWarnings("ConstantConditions")
        TypedContent typedContent = Hars.translateResponseContent(contentType, text, bodySize, contentSize, contentEncodingHeaderValue, harContentEncoding, comment, EX_MACHINA_CHARSET);
        byte[] actualBytes = typedContent.asByteSource().read();
        int actualSize = actualBytes.length;
        assertEquals("actual size", expectedBytes.length, actualSize);
        assertEquals("content-type charset", UTF_8, typedContent.getContentType().charset().orNull());
    }

    @Test
    public void getRequestPostData() throws Exception {
        List<NameValuePair> pairs = new ArrayList<>();
        String contentType = "multipart/mixed; boundary=ABCDEF_1522096137171";
        String postDataText = "--ABCDEF_1522096137171\r\n" +
                "Content-Type: application/x-www-form-urlencoded\r\n" +
                "\r\n" +
                "foo=bar&baz=gaw\r\n" +
                "--ABCDEF_1522096137171--";
        String postDataComment = "";
        Long requestBodySize = 116L;
        ByteSource actual = Hars.getRequestPostData(pairs, contentType, postDataText, requestBodySize, postDataComment, UTF_8);
        assertNotNull("byte source not null is expected", actual);
        ByteSource expected = CharSource.wrap(postDataText).asByteSource(StandardCharsets.US_ASCII);
        assertEquals("bytes", BaseEncoding.base16().encode(expected.read()), BaseEncoding.base16().encode(actual.read()));
    }

}