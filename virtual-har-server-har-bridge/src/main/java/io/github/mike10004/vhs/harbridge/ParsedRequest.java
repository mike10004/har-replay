package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Immutable class that represents an HTTP request.
 */
public abstract class ParsedRequest {

    /**
     * HTTP method of the request.
     */
    public final HttpMethod method;

    /**
     * Full request URL (with query string).
     */
    public final URI url;

    /**
     * Query parameters parsed from query string. Null if the URL does not
     * contain a {@code ?}-character after the path. Empty if the URL contains
     * a {@code ?}-character but nothing after it. All keys are in original case.
     */
    @Nullable
    public final ImmutableMultimap<String, Optional<String>> query;

    /**
     * Request headers with header names normalized to lowercase.
     */
    public final ImmutableMultimap<String, String> indexedHeaders;

    private ParsedRequest(HttpMethod method, URI url, @Nullable Multimap<String, Optional<String>> query, Multimap<String, String> indexedHeaders) {
        this.method = requireNonNull(method);
        this.url = requireNonNull(url);
        this.query = query == null ? null : ImmutableMultimap.copyOf(query);
        this.indexedHeaders = ImmutableMultimap.copyOf(indexedHeaders);

    }

    public abstract boolean isBodyPresent();

    public abstract InputStream openBodyStream() throws IOException;

    public static ParsedRequest inMemory(HttpMethod method, URI url, @Nullable Multimap<String, Optional<String>> query, Multimap<String, String> indexedHeaders, @Nullable byte[] body) {
        return new MemoryRequest(method, url, query, indexedHeaders, body);
    }

    static class MemoryRequest extends ParsedRequest {

        private final ByteSource bodySource;
        private final boolean bodyPresent;

        public MemoryRequest(HttpMethod method, URI url, @Nullable Multimap<String, Optional<String>> query, Multimap<String, String> indexedHeaders, @Nullable byte[] body) {
            super(method, url, query, indexedHeaders);
            if (bodyPresent = (body != null)) {
                bodySource = ByteSource.wrap(Arrays.copyOf(body, body.length));
            } else {
                bodySource = ByteSource.empty();
            }
        }

        @Override
        public InputStream openBodyStream() throws IOException {
            return bodySource.openStream();
        }

        @Override
        public boolean isBodyPresent() {
            return bodyPresent;
        }

    }

    @Nullable
    public String getFirstHeaderValue(String headerName) {
        requireNonNull(headerName, "headerName");
        return indexedHeaders.entries().stream()
                .filter(header -> headerName.equalsIgnoreCase(header.getKey()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    @Override
    public String toString() {
        int querySize = query == null ? -1 : query.size();
        boolean bodyPresent = isBodyPresent();
        return "ParsedRequest{" +
                "method=" + method +
                ", url=" + StringUtils.abbreviate(url.toString(), 128) +
                ", query.size=" + querySize +
                ", headers.size=" + indexedHeaders.size() +
                ", hasBody=" + bodyPresent +
                '}';
    }
}
