package io.github.mike10004.vhs.repackaged.org.apache.http;

import org.junit.Test;

import static org.junit.Assert.*;

public class NameValuePairImplTest {

    @Test
    public void equals() {
        assertEquals("same", NameValuePair.of("x", "y"), NameValuePair.of("x", "y"));
    }

    @Test
    public void equals_otherImpl() {
        NameValuePair other = new NameValuePair() {
            @Override
            public String getName() {
                return "x";
            }

            @Override
            public String getValue() {
                return "y";
            }
        };
        assertEquals("same", NameValuePair.of(other.getName(), other.getValue()), other);
    }

    @Test
    public void equals_not() {
        assertNotEquals("same", NameValuePair.of("x", "y"), NameValuePair.of("x", "z"));
    }
}