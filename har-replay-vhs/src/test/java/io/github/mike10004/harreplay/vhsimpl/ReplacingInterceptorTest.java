package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import io.github.mike10004.harreplay.ReplayServerConfig.RegexHolder;
import io.github.mike10004.harreplay.ReplayServerConfig.Replacement;
import io.github.mike10004.harreplay.ReplayServerConfig.StringLiteral;
import io.github.mike10004.harreplay.vhsimpl.ReplacingInterceptor.WritingActionResult;
import io.github.mike10004.vhs.HttpMethod;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.ImmutableHttpRespondable;
import io.github.mike10004.vhs.ParsedRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReplacingInterceptorTest {

    @Test
    public void collectText() throws IOException {
        String text = "\"<!doctype html>\\n<html>\\n<head>\\n    <title>ABCDEFG Domain</title>\\n\\n    <meta charset=\\\"utf-8\\\" />\\n    <meta http-equiv=\\\"Content-type\\\" content=\\\"text/html; charset=utf-8\\\" />\\n    <meta name=\\\"viewport\\\" content=\\\"width=device-width, initial-scale=1\\\" />\\n    <style type=\\\"text/css\\\">\\n    body {\\n        background-color: #f0f0f2;\\n        margin: 0;\\n        padding: 0;\\n        font-family: \\\"Open Sans\\\", \\\"Helvetica Neue\\\", Helvetica, Arial, sans-serif;\\n        \\n    }\\n    div {\\n        width: 600px;\\n        margin: 5em auto;\\n        padding: 50px;\\n        background-color: #fff;\\n        border-radius: 1em;\\n    }\\n    a:link, a:visited {\\n        color: #38488f;\\n        text-decoration: none;\\n    }\\n    @media (max-width: 700px) {\\n        body {\\n            background-color: #fff;\\n        }\\n        div {\\n            width: auto;\\n            margin: 0 auto;\\n            border-radius: 0;\\n            padding: 1em;\\n        }\\n    }\\n    </style>    \\n</head>\\n\\n<body>\\n<div>\\n    <h1>Example Domain</h1>\\n    <p>This domain is established to be used for illustrative examples in documents. You may use this\\n    domain in examples without prior coordination or asking for permission.</p>\\n    <p><a href=\\\"http://www.iana.org/domains/example\\\">More information...</a></p>\\n</div>\\n</body>\\n</html>\\n\"";
        MediaType contentType = MediaType.HTML_UTF_8;
        checkState(contentType.charset().isPresent());
        byte[] gzipped = gzip(text.getBytes(contentType.charset().get()));
        String contentEncoding = "gzip";
        HttpRespondable r = ImmutableHttpRespondable.builder(200)
                .bodySource(ByteSource.wrap(gzipped))
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_ENCODING, contentEncoding)
                .build();
        String actual = ReplacingInterceptor.collectText(r);
        assertEquals("text", text, actual);
    }

    @Test
    public void intercept_performed() throws Exception {
        Replacement replacement = new Replacement(StringLiteral.of("foo"), StringLiteral.of("bar"));
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8;
        Charset charset = contentType.charset().get();
        byte[] body = "This is a foo document".getBytes(charset);
        byte[] expected = "This is a bar document".getBytes(charset);
        HttpRespondable actual = doIntercept(replacement, body, contentType);
        WritingActionResult<MediaType> writeResult = ReplacingInterceptor.writeByteArray(actual::writeBody, expected.length);
        assertArrayEquals("actual bytes", expected, writeResult.byteArray);
        assertEquals("content type", contentType, writeResult.actionReturnValue);
    }

    @Test
    public void intercept_performed_regex() throws Exception {
        Replacement replacement = new Replacement(RegexHolder.of(" h(\\S+)"), StringLiteral.of(" x$1"));
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8;
        Charset charset = contentType.charset().get();
        byte[] body = "We said hello to horticulturalists with handkerchiefs".getBytes(charset);
        String expected = "We said xello to xorticulturalists with xandkerchiefs";
        HttpRespondable actual = doIntercept(replacement, body, contentType);
        WritingActionResult<MediaType> writeResult = ReplacingInterceptor.writeByteArray(actual::writeBody, expected.length());
        String actualText = new String(writeResult.byteArray, writeResult.actionReturnValue.charset().get());
        assertEquals("actual bytes", expected, actualText);
        assertEquals("content type", contentType, writeResult.actionReturnValue);
    }

    @Test
    public void intercept_notPerformed() throws Exception {
        Replacement replacement = new Replacement(StringLiteral.of("foo"), StringLiteral.of("bar"));
        MediaType contentType = MediaType.PNG;
        Charset charset = StandardCharsets.UTF_8;
        byte[] body = "This is a foo document".getBytes(charset);
        byte[] expected = "This is a bar document".getBytes(charset);
        HttpRespondable actual = doIntercept(replacement, body, contentType);
        WritingActionResult<MediaType> writeResult = ReplacingInterceptor.writeByteArray(actual::writeBody, expected.length);
        assertArrayEquals("actual bytes", body, writeResult.byteArray);
        assertEquals("content type", contentType, writeResult.actionReturnValue);
    }

    private HttpRespondable doIntercept(Replacement replacement, byte[] body, MediaType contentType) {
        Multimap<String, String> headers = ImmutableMultimap.of(HttpHeaders.CONTENT_TYPE, contentType.toString());
        ParsedRequest request = ParsedRequest.inMemory(HttpMethod.GET, URI.create("http://www.example.com/"), ImmutableMultimap.of(), ImmutableMultimap.of(), null);
        HttpRespondable response = HttpRespondable.inMemory(HttpStatus.SC_OK, headers, contentType, body);
        ReplacingInterceptor interceptor = new ReplacingInterceptor(VhsReplayManagerConfig.getDefault(), replacement);
        HttpRespondable intercepted = interceptor.intercept(request, response);
        return intercepted;
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
        try (OutputStream gout = new GZIPOutputStream(baos, data.length)) {
            gout.write(data);
        }
        return baos.toByteArray();
    }

    @Test
    public void isTextType() {
        MediaType[] expectTrue = {
                MediaType.PLAIN_TEXT_UTF_8,
                MediaType.JAVASCRIPT_UTF_8,
                MediaType.TEXT_JAVASCRIPT_UTF_8,
                MediaType.APPLICATION_XML_UTF_8,
                MediaType.HTML_UTF_8,
                MediaType.XHTML_UTF_8,
                MediaType.CSV_UTF_8,
                MediaType.CSS_UTF_8,
                MediaType.XML_UTF_8,
                MediaType.JSON_UTF_8,
        };
        Stream.of(expectTrue).forEach(ct -> confirmIsTextType(ct, true));
        MediaType[] expectFalse = {
                null,
                MediaType.PNG,
                MediaType.JPEG,
                MediaType.OCTET_STREAM,
                MediaType.FLV_VIDEO,
                MediaType.BZIP2,
                MediaType.MICROSOFT_WORD,
                MediaType.OPENDOCUMENT_TEXT,
        };
        Stream.of(expectFalse).forEach(ct -> confirmIsTextType(ct, false));
    }

    private void confirmIsTextType(@Nullable MediaType contentType, boolean expected) {
        boolean actual = ReplacingInterceptor.isTextType(contentType);
        assertEquals(String.valueOf(contentType), expected, actual);
        if (contentType != null) {
            actual = ReplacingInterceptor.isTextType(contentType.withoutParameters());
            assertEquals(contentType.withoutParameters().toString(), expected, actual);
        }
    }

    @Test
    public void parameterlessTypesAreParameterless() {
        ReplacingInterceptor.parameterlessTextLikeTypes.forEach(t -> {
            assertTrue("parameterless", t.equals(t.withoutParameters()));
        });
    }
}