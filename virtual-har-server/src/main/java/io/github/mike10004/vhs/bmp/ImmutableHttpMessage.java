package io.github.mike10004.vhs.bmp;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("unused")
public class ImmutableHttpMessage {

    private final ByteSource dataSource;
    public final ImmutableMultimap<String, String> headers;

    protected ImmutableHttpMessage(MessageBuilder<?> builder) {
        dataSource = builder.dataSource;
        headers = ImmutableMultimap.copyOf(builder.headers);
    }

    protected MoreObjects.ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("contentSource", dataSource)
                .add("headers.size", headers.size());
    }

    /**
     * Finds headers by name, case-insensitively.
     * @param headerName header name
     * @return the headers
     */
    public Stream<Entry<String, String>> getHeaders(String headerName) {
        checkNotNull(headerName);
        return headers.entries().stream().filter(entry -> entry.getKey().equalsIgnoreCase(headerName));
    }

    public Stream<String> getHeaderValues(String headerName) {
        return getHeaders(headerName).map(Entry::getValue);
    }

    @Nullable
    public MediaType getContentType() {
        String headerValue = getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
        if (headerValue == null) {
            return null;
        }
        return MediaType.parse(headerValue);
    }

    public ByteSource getDataSource() {
        return dataSource;
    }

    @Nullable
    public String getFirstHeaderValue(String headerName) {
        return getHeaders(headerName).map(Entry::getValue).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    public static abstract class MessageBuilder<B extends MessageBuilder> {

        private ByteSource dataSource = ByteSource.empty();
        private final Multimap<String, String> headers = ArrayListMultimap.create();

        protected MessageBuilder() {
        }

        public B content(MediaType contentType, ByteSource byteSource) {
            this.dataSource = requireNonNull(byteSource);
            setHeader(HttpHeaders.CONTENT_TYPE, contentType.toString());
            @Nullable Long len = byteSource.sizeIfKnown().orNull();
            if (len != null) {
                setHeader(HttpHeaders.CONTENT_LENGTH, len.toString());
            }
            return (B) this;
        }

        public B replaceHeaders(Multimap<String, String> val) {
            headers.clear();
            headers.putAll(val);
            return (B) this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public B setHeader(String headerName, String headerValue) {
            Set<String> ctKeys = headers.keySet().stream().filter(headerName::equalsIgnoreCase).collect(Collectors.toSet());
            ctKeys.forEach(headers::removeAll);
            headers.put(headerName, headerValue);
            return (B) this;
        }

        public B addHeader(String headerName, String headerValue) {
            this.headers.put(headerName, headerValue);
            return (B) this;
        }

        public B addHeaders(Collection<? extends Entry<String, String>> headers) {
            return addHeaders(headers.stream());
        }

        public B addHeaders(Stream<? extends Entry<String, String>> headers) {
            headers.forEach(entry -> this.headers.put(entry.getKey(), entry.getValue()));
            return (B) this;
        }
    }
}
