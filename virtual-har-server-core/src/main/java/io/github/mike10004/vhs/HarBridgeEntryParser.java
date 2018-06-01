package io.github.mike10004.vhs;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import io.github.mike10004.vhs.harbridge.HarBridge;
import io.github.mike10004.vhs.harbridge.HarResponseData;
import io.github.mike10004.vhs.harbridge.HarResponseEncoding;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of a HAR entry parser that gets information on HAR entries
 * by using a HAR bridge.
 * @param <E> the HAR entry type
 */
public class HarBridgeEntryParser<E> implements EntryParser<E> {

    private final HarBridge<E> bridge;
    private final HarResponseEncoderFactory<E> responseEncoderFactory;

    public HarBridgeEntryParser(HarBridge<E> bridge, HarResponseEncoderFactory<E> responseEncoderFactory) {
        this.bridge = requireNonNull(bridge);
        this.responseEncoderFactory = requireNonNull(responseEncoderFactory);
    }

    public static <E> HarBridgeEntryParser<E> withPlainEncoder(HarBridge<E> bridge) {
        return new HarBridgeEntryParser<>(bridge, HarResponseEncoderFactory.alwaysIdentityEncoding());
    }

    @SuppressWarnings("SameParameterValue")
    protected static int getDefaultPortForScheme(@Nullable String scheme, int defaultDefault) {
        if (scheme != null) {
            switch (scheme) {
                case "http":
                    return 80;
                case "ftp":
                    return 21;
                case "https":
                    return 443;
            }
        }
        return defaultDefault;
    }

    protected URI parseUrl(HttpMethod method, String url) {
        try {
            if (method == HttpMethod.CONNECT) {
                HostAndPort hostAndPort;
                if (url.contains("/")) {
                    URI uri = new URI(url);
                    int port = uri.getPort();
                    if (port <= 0) {
                        port = getDefaultPortForScheme(uri.getScheme(), 443);
                    }
                    hostAndPort = HostAndPort.fromParts(uri.getHost(), port);
                } else {
                    hostAndPort = HostAndPort.fromString(url);
                }
                URI uri = new URI(null, null, hostAndPort.getHost(), hostAndPort.getPortOrDefault(-1), null, null, null);
                return uri;
            } else {
                return new URI(url);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL has unexpected syntax", e);
        }
    }

    @Override
    public ParsedRequest parseRequest(E harEntry) throws IOException {
        HttpMethod method = HttpMethod.valueOf(bridge.getRequestMethod(harEntry));
        URI parsedUrl = parseUrl(method, bridge.getRequestUrl(harEntry));
        Multimap<String, Optional<String>> query = parseQuery(parsedUrl);
        Multimap<String, String> indexedHeaders = indexHeaders(bridge.getRequestHeaders(harEntry));
        ByteSource bodySource = bridge.getRequestPostData(harEntry);
        byte[] body = bodySource.read();
        return ParsedRequest.inMemory(method, parsedUrl, query, indexedHeaders, body);
    }

    /**
     * Creates a lowercase-keyed multimap from a list of headers.
     * @param entryHeaders stream of headers as map entries
     * @return a multimap containing all entries in the stream
     */
    protected Multimap<String, String> indexHeaders(Stream<? extends Entry<String, String>> entryHeaders) {
        return HttpRequests.indexHeaders(entryHeaders);
    }

    /**
     * Creates a multimap from the parameters specified in a URI.
     * @param uri the URI
     * @return the multimap
     */
    @Nullable
    public Multimap<String, Optional<String>> parseQuery(URI uri) {
        return HttpRequests.parseQuery(uri);
    }

    @Override
    public HttpRespondable parseResponse(ParsedRequest request, E entry) throws IOException {
        int status = bridge.getResponseStatus(entry);
        HarResponseEncoding responseEncoder = responseEncoderFactory.getEncoder(request, entry);
        HarResponseData responseData = bridge.getResponseData(request, entry, responseEncoder);
        return constructRespondable(status, responseData);
    }

    /**
     * Replaces the content-length header.
     * @param headers headers
     * @param value new value
     * @see #replaceHeaders(Multimap, String, Object)
     */
    protected static void replaceContentLength(Multimap<String, String> headers, @Nullable Long value) {
        @Nullable String valueStr = value == null ? null : value.toString();
        replaceHeaders(headers, HttpHeaders.CONTENT_LENGTH, valueStr);
    }

    /**
     * Replaces or removes a header. Matches against header names case-insensitively.
     * @param headers headers
     * @param headerName name of header to be replaced
     * @param value value to take over; if null, existing values will be removed and none added
     * @param <V> value type
     */
    protected static <V> void replaceHeaders(Multimap<String, V> headers, String headerName, @Nullable V value) {
        Set<String> caseSensitiveKeys = headers.keySet().stream()
                .filter(headerName::equalsIgnoreCase)
                .collect(Collectors.toSet());
        if (value != null && caseSensitiveKeys.size() == 1) {
            Collection<V> vals = headers.get(caseSensitiveKeys.iterator().next());
            if (vals.size() == 1) { // if size > 1, we want to consolidate the entries into a single key
                if (value.equals(vals.iterator().next())) {
                    // no replacement needed
                    return;
                }
            }
        }
        caseSensitiveKeys.forEach(headers::removeAll);
        if (value != null) {
            headers.put(headerName, value);
        }
    }

    protected static HttpRespondable constructRespondable(int status, HarResponseData responseData) throws IOException {
        Multimap<String, String> headers = ArrayListMultimap.create();
        responseData.headers().forEach(header -> {
            headers.put(header.getKey(), header.getValue());
        });
        replaceContentLength(headers, responseData.getBody().size());
        return HttpRespondable.inMemory(status, headers, responseData.getContentType(), responseData.getBody());
    }

}
