package io.github.mike10004.vhs.repackaged.org.apache.http;

import java.util.Objects;

public class NameValuePairImpl implements NameValuePair {

    private final String name;
    private final String value;

    public NameValuePairImpl(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !NameValuePair.class.isAssignableFrom(o.getClass())) return false;
        NameValuePair that = (NameValuePair) o;
        return Objects.equals(name, that.getName()) &&
                Objects.equals(value, that.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        return "Pair{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
