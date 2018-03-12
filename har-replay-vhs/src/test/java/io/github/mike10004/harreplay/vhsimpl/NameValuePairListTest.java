package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.github.mike10004.harreplay.vhsimpl.NameValuePairList.StringMapEntryList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NameValuePairListTest {

    @Test
    public void empty() {
        NameValuePairList<org.apache.http.NameValuePair> list = NameValuePairList.empty();
        testEmpty(list);
    }

    private void testEmpty(NameValuePairList<?> list) {
        assertEquals("list", ImmutableList.of(), list.listValues("hello"));
        assertEquals("stream", 0, list.streamValues("hello").count());
        assertEquals("get", null, list.getFirstValue("hello"));
    }

    @Test
    public void StringMapEntryList_empty() {
        testEmpty(StringMapEntryList.empty());
    }

    @Test
    public void caseInsensitive() {
        ArrayPairList list = new ArrayPairList(ArrayPairList.caseInsensitiveMatcher(), "Foo", "bar", "baz", "gaw");
        assertEquals("case-insensitive retrieval", "bar", list.getFirstValue("foo"));
    }

    @Test
    public void caseSensitive() {
        ArrayPairList list = new ArrayPairList(ArrayPairList.caseSensitiveMatcher(), "Foo", "Bar", "foo", "bar");
        assertEquals("case-insensitive retrieval", "bar", list.getFirstValue("foo"));
        assertNull("not present", list.getFirstValue("fOO"));
    }

    private static class ArrayPairList extends NameValuePairList<String[]> {

        public ArrayPairList(BiPredicate<? super String, ? super String> nameMatcher, String...pairs) {
            this(toPairs(pairs), nameMatcher);
        }

        private static Iterable<String[]> toPairs(String[] flat) {
            List<String[]> pairs = new ArrayList<>();
            for (int i = 0; i < flat.length; i += 2) {
                pairs.add(new String[]{flat[i], flat[i+1]});
            }
            return pairs;
        }

        public ArrayPairList(Iterable<String[]> pairs, BiPredicate<? super String, ? super String> nameMatcher) {
            super(pairs, array -> array[0], array -> array[1], nameMatcher);
        }

    }

    @Test
    public void useOptionalValues() {
        Multimap<String, Optional<String>> mm = ImmutableMultimap.<String, Optional<String>>builder()
                .put("x", Optional.empty())
                .put("y", Optional.of("2"))
                .put("x", Optional.of("1"))
                .put("z", Optional.of("3"))
                .build();
        NameValuePairList list = NameValuePairList.caseSensitive(mm.entries(), Map.Entry::getKey, entry -> entry.getValue().orElse(null));
        assertEquals("value for x", null, list.getFirstValue("x"));
        assertEquals("value for 'y'", "2", list.getFirstValue("y"));
    }

}