package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ImmutableList;
import io.github.mike10004.harreplay.vhsimpl.NameValuePairList.StringMapEntryList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static org.junit.Assert.*;

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
}