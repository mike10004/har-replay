package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ImmutableList;
import io.github.mike10004.harreplay.vhsimpl.NameValuePairList.StringMapEntryList;
import org.junit.Test;

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
}