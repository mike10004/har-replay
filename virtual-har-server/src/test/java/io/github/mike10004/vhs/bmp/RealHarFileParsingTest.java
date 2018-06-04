package io.github.mike10004.vhs.bmp;

import com.google.common.io.ByteStreams;
import com.google.common.net.MediaType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarRequest;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.harbridge.HarBridge;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Run this test from the command line if you have a HAR file for which parsing fails.
 * Specify the pathname of the HAR as value of system property 'vhs.realHarFile'.
 * Then replicate the issue in a hard-coded test.
 */
public class RealHarFileParsingTest {

    @Test
    public void parseRealHarFile() throws Exception {
        String harFilePathname = System.getProperty("vhs.realHarFile");
        Assume.assumeTrue("no real har file specified", harFilePathname != null);
        File harFile = new File(harFilePathname);
        Assume.assumeTrue(String.format("skipping because not a file: %s", harFile), harFile.isFile());
        HarBridge<HarEntry> bridge = new SstoehrHarBridge();
        HarBridgeEntryParser<HarEntry> entryParser = HarBridgeEntryParser.withPlainEncoder(bridge);
        Har har = new HarReader().readFromFile(harFile, HarReaderMode.LAX);
        for (HarEntry entry : har.getLog().getEntries()) {
            try {
                entryParser.parseRequest(entry);
            } catch (RuntimeException e) {
                System.out.format("failed to parse request for entry%n");
                e.printStackTrace(System.out);
                System.out.println();
                new GsonBuilder().setPrettyPrinting().create().toJson(entry.getRequest(), System.out);
                System.out.println();
                fail("entry request parsing failure");
            }
        }
    }

    @Test
    public void parseRealHarEntry() throws Exception {
        String json = "{\n" +
                "  \"method\": \"GET\",\n" +
                "  \"url\": \"https://www.example.com/wuzzy/sysop?1234567890\",\n" +
                "  \"httpVersion\": \"HTTP/1.1\",\n" +
                "  \"cookies\": [\n" +
                "    {\n" +
                "      \"name\": \"JSESSIONID\",\n" +
                "      \"value\": \"ajax:123456789012345678901234567890\",\n" +
                "      \"comment\": \"\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"_ga\",\n" +
                "      \"value\": \"GA1.2.123456789.123456789\",\n" +
                "      \"comment\": \"\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"_gat\",\n" +
                "      \"value\": \"1\",\n" +
                "      \"comment\": \"\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"bcookie\",\n" +
                "      \"value\": \"v\\u003d2\\u004311bd0c-25cf-4f51-ba47-c6aa96eaee3f\",\n" +
                "      \"comment\": \"\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"bscookie\",\n" +
                "      \"value\": \"v\\u003d1234567890b17da5-76f5-445a-bf7e-fac136be870bAQEBOE-12909765432\",\n" +
                "      \"comment\": \"\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"lang\",\n" +
                "      \"value\": \"v\\u003d2\\u0026lang\\u003den-us\",\n" +
                "      \"comment\": \"\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"lidc\",\n" +
                "      \"value\": \"b\\u003d1234567890\\u003d257257:u\\u00313771\\u003d234567890:t\\u003d4567890:s\\u003d1234567890987654321\",\n" +
                "      \"comment\": \"\"\n" +
                "    }\n" +
                "  ],\n" +
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
                "      \"name\": \"Accept\",\n" +
                "      \"value\": \"*/*\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Referer\",\n" +
                "      \"value\": \"https://www.example.com/\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Accept-Encoding\",\n" +
                "      \"value\": \"gzip, deflate, br\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Accept-Language\",\n" +
                "      \"value\": \"en-US,en;q\\u003d0.9\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Cookie\",\n" +
                "      \"value\": \"SomeDummyValue\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"queryString\": [\n" +
                "    {\n" +
                "      \"name\": \"1518817289576\",\n" +
                "      \"value\": \"\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"postData\": {\n" +
                "    \"params\": []\n" +
                "  },\n" +
                "  \"headersSize\": 684,\n" +
                "  \"bodySize\": 0,\n" +
                "  \"comment\": \"\"\n" +
                "}";
        HarEntry harEntry = new HarEntry();
        HarRequest request = new Gson().fromJson(json, HarRequest.class);
        harEntry.setRequest(request);
        HarBridgeEntryParser.withPlainEncoder(new SstoehrHarBridge()).parseRequest(harEntry);
    }

    @Test
    public void testParseRequestWithPostData() throws Exception {
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
        ParsedRequest parsed = HarBridgeEntryParser.withPlainEncoder(new SstoehrHarBridge()).parseRequest(harEntry);
        assertTrue("body present", parsed.isBodyPresent());
        byte[] body;
        try (InputStream in = parsed.openBodyStream()) {
            body = ByteStreams.toByteArray(in);
        }
        HttpEntity entity = makeEntity(body, MediaType.FORM_DATA.withCharset(StandardCharsets.UTF_8));
        List<NameValuePair> params = URLEncodedUtils.parse(entity);
        assertEquals("num params", 1, params.size());
        NameValuePair param = params.iterator().next();
        assertEquals("name", "plist", param.getName());
        assertEquals("value", "eeny/meeny/miny/mo", param.getValue());
    }

    private static HttpEntity makeEntity(byte[] body, MediaType mediaType) {
        ContentType contentType = ContentType.create(mediaType.withoutParameters().toString(), mediaType.charset().orNull());
        HttpEntity entity = new ByteArrayEntity(body, contentType);
        return entity;
    }

}
