package io.github.mike10004.vhs;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.github.mike10004.vhs.repackaged.org.apache.http.client.utils.URLEncodedUtils;

import javax.annotation.Nullable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;

class HttpRequests {

    private HttpRequests() {}

    /**
     * Creates a lowercase-keyed multimap from a list of headers.
     */
    public static <V> Multimap<String, V> indexHeaders(Stream<? extends Map.Entry<String, V>> entryHeaders) {
        Multimap<String, V> headers = ArrayListMultimap.create();
        entryHeaders.forEach(header -> {
            headers.put(header.getKey().toLowerCase(), header.getValue());
        });
        return headers;
    }

    /**
     * Parses the query string of a URI and creates a multimap.
     * Map entries are created from parameters as follows:
     * <ul>
     *     <li>{@code key=value} becomes {@code (key, Optional.of(value))}</li>
     *     <li>{@code key=} becomes {@code (key, Optional.of("")}</li>
     *     <li>{@code key} becomes {@code (key, Optional.empty())}</li>
     * </ul>
     * @param uri the URI
     * @return the multimap
     */
    @Nullable
    public static Multimap<String, Optional<String>> parseQuery(URI uri) {
        if (uri.getQuery() == null) {
            return null;
        }
        List<Entry<String, String>> nvps = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
        Multimap<String, Optional<String>> mm = ArrayListMultimap.create();
        nvps.forEach(nvp -> {
            mm.put(nvp.getKey().toLowerCase(), Optional.ofNullable(nvp.getValue()));
        });
        return mm;
    }
}
