package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.junit.Test;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.*;

public class HttpRequestsTest {

    @Test
    public void parseQuery() {
        Multimap<String, Optional<String>> q = HttpRequests.parseQuery(URI.create("http://example.com/hello"));
        assertNull(q);
        q = HttpRequests.parseQuery(URI.create("http://example.com/hello?foo=bar"));
        assertEquals(ImmutableMultimap.of("foo", Optional.of("bar")), q);
        q = HttpRequests.parseQuery(URI.create("http://example.com/hello?"));
        assertEquals(ImmutableMultimap.of(), q);
    }

    @Test
    public void parseQuery_paramWithNoAssignment() {
        URI uri = URI.create("https://www.example.com/foo/bar?1234567890");
        Multimap<?, ?> query = HttpRequests.parseQuery(uri);
        assertNotNull(query);
        ImmutableMultimap.copyOf(query);
    }

    @Test
    public void parseQuery_paramWithEmptyNameIsIgnored() {
        /*
         * Apache HTTP Client EntityUtils silently ignores nameless parameters, and
         * we expect that same behavior, mostly for simplicity.
         */
        URI uri = URI.create("https://www.example.com/foo?bar=baz&=gaw");
        Multimap<String, Optional<String>> query = HttpRequests.parseQuery(uri);
        assertNotNull("query is null but shouldn't be", query);
        ImmutableMultimap.copyOf(query);
        assertEquals("bar", ImmutableList.of(Optional.of("baz")), ImmutableList.copyOf(query.get("bar")));
        assertEquals("<empty>", ImmutableList.of(), ImmutableList.copyOf(query.get("")));
    }

}