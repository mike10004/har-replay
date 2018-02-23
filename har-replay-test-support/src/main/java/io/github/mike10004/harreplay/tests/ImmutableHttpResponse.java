package io.github.mike10004.harreplay.tests;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;

import javax.annotation.Nullable;
import java.util.Map.Entry;

import static java.util.Objects.requireNonNull;

public class ImmutableHttpResponse {
    public final int status;
    public final ImmutableMultimap<String, String> headers;
    public final ByteSource data;

    @SuppressWarnings("unused")
    public ImmutableHttpResponse(int status, ImmutableMultimap<String, String> headers, ByteSource data) {
        this.status = status;
        this.headers = headers;
        this.data = data;
    }

    private ImmutableHttpResponse(Builder builder) {
        status = builder.status;
        headers = ImmutableMultimap.copyOf(builder.headers);
        data = builder.data;
    }

    public static Builder builder(int status) {
        return new Builder(status);
    }

    @Nullable
    public String getFirstHeaderValue(String headerName) {
        requireNonNull(headerName, "header name");
        return headers.entries().stream().filter(entry -> headerName.equalsIgnoreCase(entry.getKey())).map(Entry::getValue).findFirst().orElse(null);
    }


    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static final class Builder {
        private final int status;
        private Multimap<String, String> headers = ArrayListMultimap.create();
        private ByteSource data;

        private Builder(int status) {
            this.status = status;
        }

        public Builder replaceHeaders(Multimap<String, String> val) {
            headers = ArrayListMultimap.create();
            headers.putAll(val);
            return this;
        }

        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public Builder data(ByteSource val) {
            data = val;
            return this;
        }

        public ImmutableHttpResponse build() {
            return new ImmutableHttpResponse(this);
        }
    }
}
