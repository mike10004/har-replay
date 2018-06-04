package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HeuristicEntryMatcherTest {

    @Test
    public void findTopEntry() throws Exception {
        BasicHeuristic heuristic = new BasicHeuristic();
        int threshold = BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE;
        String correctUrl = "http://example.com/page?foo=bar&baz=gaw";
        HeuristicEntryMatcher.ParsedEntry[] entries = {
                createEntry("GET", "http://example.com/", 200, MediaType.PLAIN_TEXT_UTF_8, "bad"),
                createEntry("GET", "http://example.com/page?foo=bar", 200, MediaType.PLAIN_TEXT_UTF_8, "kinda bad"),
                createEntry("GET", correctUrl, 200, MediaType.PLAIN_TEXT_UTF_8, "good"),
                createEntry("POST", correctUrl, 200, MediaType.PLAIN_TEXT_UTF_8, "more bad"),
                createEntry("GET", "http://example.com/favicon.ico", 404, MediaType.PLAIN_TEXT_UTF_8, "404 Not Found"),
                createEntry("GET", "http://eviladagency.com/", 200, MediaType.JAVASCRIPT_UTF_8, "console.log('tracking you');\n"),
        };
        HeuristicEntryMatcher matcher = new HeuristicEntryMatcher(heuristic, threshold, Arrays.asList(entries));
        ParsedRequest request = Tests.createRequest("GET", correctUrl);
        HttpRespondable response = matcher.findTopEntry(request);
        assertNotNull("response", response);
        assertEquals(200, response.getStatus());
        assertEquals(1, response.streamHeaders().count());
        String content = Tests.readAsString(response);
        assertEquals("content", "good", content);
    }

    protected HeuristicEntryMatcher.ParsedEntry createEntry(String method, String url, int status, MediaType contentType, String bodyText) {
        ParsedRequest request = Tests.createRequest(method, url);
        assert contentType.charset().isPresent();
        HttpRespondable response = HttpRespondable.inMemory(status, ImmutableMultimap.of(HttpHeaders.CONTENT_TYPE, contentType.toString()), contentType, bodyText.getBytes(contentType.charset().get()));
        return new HeuristicEntryMatcher.ParsedEntry(request, request_ -> response);
    }
}
