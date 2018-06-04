package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;

public abstract class EntryParserTestBase<E> {

    protected abstract EntryParser<E> createParser();

    @SuppressWarnings("SameParameterValue")
    protected abstract E createEntryWithRequest(String method, String url, String...headers);

    @Test
    public void create() throws Exception {
        String urlStr = "https://www.example.com/hello";
        E entry = createEntryWithRequest("GET", urlStr, "X-Something", "foo", "X-Something-Else", "bar");
        ParsedRequest parsed = createParser().parseRequest(entry);
        assertEquals("method", HttpMethod.GET, parsed.method);
        assertEquals("url", URI.create(urlStr), parsed.url);
        assertEquals("query", null, parsed.query);
        assertEquals(2, parsed.indexedHeaders.size());
        assertEquals("header value", "foo", parsed.indexedHeaders.get("x-something").iterator().next());
    }

}
