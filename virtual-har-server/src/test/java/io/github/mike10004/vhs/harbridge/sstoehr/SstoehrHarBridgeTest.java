package io.github.mike10004.vhs.harbridge.sstoehr;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.gson.Gson;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import de.sstoehr.harreader.model.HttpMethod;
import io.github.mike10004.vhs.harbridge.HarBridge;
import io.github.mike10004.vhs.harbridge.HarResponseData;
import io.github.mike10004.vhs.harbridge.HarResponseEncoding;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.harbridge.TypedContent;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SstoehrHarBridgeTest {

    private HarBridge<HarEntry> bridge = new SstoehrHarBridge();
    private HarEntry entry;
    private URI url = URI.create("https://www.example.com/");
    private MediaType contentType = MediaType.PLAIN_TEXT_UTF_8;
    private String responseText = "hello";
    @SuppressWarnings("ConstantConditions")
    private byte[] responseBody = responseText.getBytes(contentType.charset().get());

    @Before
    public void createCommonEntry() throws Exception {
        entry = createHarEntry(url, contentType, responseText, responseBody.length);
    }

    private static HarEntry createHarEntry(URI requestUrl, MediaType responseContentType, String responseText, long responseBodyLength) {
        HarEntry entry = new HarEntry();
        HarRequest request = new HarRequest();
        request.setMethod(HttpMethod.GET);
        request.setUrl(requestUrl.toString());
        HarResponse response = new HarResponse();
        response.setStatus(200);
        response.setStatusText("OK");
        HarHeader contentTypeHeader = new HarHeader();
        contentTypeHeader.setName(HttpHeaders.CONTENT_TYPE);
        contentTypeHeader.setValue(responseContentType.toString());
        response.getHeaders().add(contentTypeHeader);
        HarContent harContent = new HarContent();
        harContent.setMimeType(responseContentType.toString());
        harContent.setText(responseText);
        response.setBodySize(responseBodyLength);
        response.setContent(harContent);
        entry.setRequest(request);
        entry.setResponse(response);
        return entry;
    }

    @Test
    public void getRequestMethod() throws Exception {
        assertEquals("method", HttpMethod.GET.name(), bridge.getRequestMethod(entry));
    }

    @Test
    public void getRequestUrl() throws Exception {
        assertEquals("url", url.toString(), bridge.getRequestUrl(entry));
    }

    @Test
    public void getRequestHeaders() throws Exception {
        assertEquals("request header count", 0L, bridge.getRequestHeaders(entry).count());
    }

    @Test
    public void getResponseData() throws Exception {
        ParsedRequest request = ParsedRequest.inMemory(io.github.mike10004.vhs.harbridge.HttpMethod.GET, URI.create("http://www.example.com/"), ImmutableMultimap.of(), ImmutableMultimap.of(), null);
        HarResponseData responseData = bridge.getResponseData(request, entry, HarResponseEncoding.unencoded());
        assertEquals("body size", responseBody.length, responseData.getBody().size());
        assertEquals("content type", contentType, responseData.getContentType());
        assertEquals("number of headers", entry.getResponse().getHeaders().size(), (responseData.headers()).size());
    }

    @Test
    public void getRequestPostData() throws Exception {
        assertTrue(ByteSource.empty().contentEquals(bridge.getRequestPostData(entry)));
    }

    @Test
    public void getResponseStatus() throws Exception {
        assertEquals(200, bridge.getResponseStatus(entry));
    }

    @Test
    public void getRequestPostData_params() throws Exception {
        String json = "{\n" +
                "  \"method\": \"POST\",\n" +
                "  \"url\": \"https://www.example.com/hello/world?foo=438989901\",\n" +
                "  \"httpVersion\": \"HTTP/1.1\",\n" +
                "  \"headers\": [\n" +
                "    {\n" +
                "      \"name\": \"Host\",\n" +
                "      \"value\": \"www.example.com\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Connection\",\n" +
                "      \"value\": \"keep-alive\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"User-Agent\",\n" +
                "      \"value\": \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"content-type\",\n" +
                "      \"value\": \"application/x-www-form-urlencoded;charset\\u003dUTF-8\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"queryString\": [\n" +
                "    {\n" +
                "      \"name\": \"csrfToken\",\n" +
                "      \"value\": \"ajax:123456789034567890\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"postData\": {\n" +
                "    \"mimeType\": \"application/x-www-form-urlencoded;charset\\u003dUTF-8\",\n" +
                "    \"params\": [\n" +
                "      {\n" +
                "        \"name\": \"plist\",\n" +
                "        \"value\": \"eeny/meeny/miny/mo\",\n" +
                "        \"comment\": \"\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"comment\": \"\"\n" +
                "  },\n" +
                "  \"headersSize\": 993,\n" +
                "  \"bodySize\": 1996,\n" +
                "  \"comment\": \"\"\n" +
                "}";
        HarEntry harEntry = new HarEntry();
        HarRequest request = new Gson().fromJson(json, HarRequest.class);
        harEntry.setRequest(request);
        ByteSource postData = new SstoehrHarBridge().getRequestPostData(harEntry);
        assertNotNull("post data", postData);
        HttpEntity entity = makeEntity(postData, MediaType.FORM_DATA.withCharset(UTF_8));
        List<NameValuePair> params = URLEncodedUtils.parse(entity);
        assertEquals("num params", 1, params.size());
        NameValuePair param = params.iterator().next();
        assertEquals("name", "plist", param.getName());
        assertEquals("value", "eeny/meeny/miny/mo", param.getValue());
    }

    private static HttpEntity makeEntity(ByteSource byteSource, MediaType mediaType) throws IOException {
        ContentType contentType = ContentType.create(mediaType.withoutParameters().toString(), mediaType.charset().orNull());
        HttpEntity entity = new ByteArrayEntity(byteSource.read(), contentType);
        return entity;
    }

    @Test
    public void getContentType_absent() throws Exception {
        HarBridge<HarEntry> bridge = new SstoehrHarBridge();
        HarEntry entry = new HarEntry();
        HarResponse response = new HarResponse();
        HarContent content = new HarContent();
        response.setContent(content);
        entry.setResponse(response);
        ParsedRequest request = ParsedRequest.inMemory(io.github.mike10004.vhs.harbridge.HttpMethod.GET, URI.create("https://www.example.com/"), null, ImmutableMultimap.of(), null);
        HarResponseData actual = bridge.getResponseData(request, entry, HarResponseEncoding.unencoded());
        assertEquals("body", 0, actual.getBody().read().length);
        assertEquals("content-type", HarBridge.getContentTypeDefaultValue(), actual.getContentType());
        assertEquals("", 0, actual.headers().size());
    }

    @Test
    public void getResponseBody_addExMachinaCharsetToContentTypeHeader() throws Exception {
        Charset exMachinaCharset = UTF_8;
        SstoehrHarBridge bridge = new SstoehrHarBridge(exMachinaCharset);
        ParsedRequest request = ParsedRequest.inMemory(io.github.mike10004.vhs.harbridge.HttpMethod.GET, URI.create("http://foo.bar/"), null, ImmutableMultimap.of(), null);
        MediaType entryResponseContentType = MediaType.PLAIN_TEXT_UTF_8.withoutParameters();
        HarEntry entry = createHarEntry(request.url, entryResponseContentType, "foo", 3);
        TypedContent content = bridge.getResponseBody(entry);
        MediaType expectedResponseContentType = entryResponseContentType.withCharset(exMachinaCharset);
        assertEquals("content type", expectedResponseContentType, content.getContentType());
    }

    @Test
    public void getResponseData_contentTypeExMachina() throws Exception {
        Charset exMachinaCharset = UTF_8;
        SstoehrHarBridge bridge = new SstoehrHarBridge(exMachinaCharset);
        ParsedRequest request = ParsedRequest.inMemory(io.github.mike10004.vhs.harbridge.HttpMethod.GET, URI.create("http://foo.bar/"), null, ImmutableMultimap.of(), null);
        MediaType entryResponseContentType = MediaType.PLAIN_TEXT_UTF_8.withoutParameters();
        HarEntry entry = createHarEntry(request.url, entryResponseContentType, "foo", 3);
        HarResponseData responseData = bridge.getResponseData(request, entry, HarResponseEncoding.unencoded());
        MediaType expectedResponseContentType = entryResponseContentType.withCharset(exMachinaCharset);
        MediaType responseDataContentType = responseData.getContentType();
        assertEquals("responseDataContentType", expectedResponseContentType, responseDataContentType);
        String responseDataContentTypeHeaderValue = responseData.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
        assertNotNull("expect responseData to contain content-type header", responseDataContentTypeHeaderValue);
        assertEquals("responseData content-type header value", expectedResponseContentType, MediaType.parse(responseDataContentTypeHeaderValue));
    }

    @Test
    public void getRequestData() throws Exception {
        String requestJson = "{\n" +
                "  \"method\": \"POST\",\n" +
                "  \"url\": \"https://www.example.com/foo/bar\",\n" +
                "  \"httpVersion\": \"HTTP/1.1\",\n" +
                "  \"cookies\": [],\n" +
                "  \"headers\": [\n" +
                "    {\n" +
                "      \"name\": \"Host\",\n" +
                "      \"value\": \"www.example.com\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Connection\",\n" +
                "      \"value\": \"keep-alive\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Content-Length\",\n" +
                "      \"value\": \"116\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"X-ABCDEF-Protocol-Version\",\n" +
                "      \"value\": \"Foo\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"User-Agent\",\n" +
                "      \"value\": \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0 Safari/537.36\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Content-Type\",\n" +
                "      \"value\": \"multipart/mixed; boundary\\u003dABCDEF_1522096137171\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Accept\",\n" +
                "      \"value\": \"application/json, text/javascript, */*; q\\u003d0.01\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"X-Requested-With\",\n" +
                "      \"value\": \"XMLHttpRequest\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Accept-Encoding\",\n" +
                "      \"value\": \"gzip, deflate, br\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Accept-Language\",\n" +
                "      \"value\": \"en-US,en;q\\u003d0.9\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"queryString\": [],\n" +
                "  \"postData\": {\n" +
                "    \"mimeType\": \"multipart/mixed; boundary\\u003dABCDEF_1522096137171\",\n" +
                "    \"params\": [],\n" +
                "    \"text\": \"--ABCDEF_1522096137171\\r\\nContent-Type: application/x-www-form-urlencoded\\r\\n\\r\\nfoo\\u003dbar\\u0026baz\\u003dgaw\\r\\n--ABCDEF_1522096137171--\",\n" +
                "    \"comment\": \"\"\n" +
                "  },\n" +
                "  \"headersSize\": 1891,\n" +
                "  \"bodySize\": 116,\n" +
                "  \"comment\": \"\"\n" +
                "}";
        SstoehrHarBridge bridge = new SstoehrHarBridge();
        HarRequest request = new Gson().fromJson(requestJson, HarRequest.class);
        HarEntry entry = new HarEntry();
        entry.setRequest(request);
        ByteSource byteSource = bridge.getRequestPostData(entry);
        assertNotNull("post data byte source", byteSource);
        byte[] expected = request.getPostData().getText().getBytes(StandardCharsets.US_ASCII);
        assertEquals("post data bytes", BaseEncoding.base16().encode(expected), BaseEncoding.base16().encode(byteSource.read()));

    }
}