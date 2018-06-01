package io.github.mike10004.vhs.repackaged.org.apache.http;

import java.util.Map;

public interface NameValuePair {

    String getName();
    String getValue();

    static NameValuePair of(String name, String value) {
        return new NameValuePairImpl(name, value);
    }

    static NameValuePair from(Map.Entry<String, String> entry) {
        return of(entry.getKey(), entry.getValue());
    }
}
