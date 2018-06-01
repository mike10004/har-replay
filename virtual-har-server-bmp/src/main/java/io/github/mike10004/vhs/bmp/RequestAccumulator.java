package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.repackaged.org.apache.http.client.utils.URLEncodedUtils;
import io.netty.handler.codec.http.HttpVersion;
import net.lightbody.bmp.core.har.HarNameValuePair;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Class that represents an accumulation of request data.
 */
class RequestAccumulator {

    private final HttpVersion httpVersion;
    private volatile String method;
    private volatile String url;
    private final List<HarNameValuePair> headers = Collections.synchronizedList(new ArrayList<>());
    private volatile byte[] body = new byte[0];

    public RequestAccumulator(HttpVersion httpVersion) {
        this.httpVersion = requireNonNull(httpVersion);
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    private String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    private List<HarNameValuePair> getHeaders() {
        return headers;
    }

    public void addHeader(String name, String value) {
        getHeaders().add(new HarNameValuePair(name, value));
    }

    public void setBody(byte[] body) {
        this.body = requireNonNull(body, "body");
    }

    /**
     * Freezes this accumulation and returns an immutable object.
     * @return the frozen request
     * @throws IOException if formatting the body fails
     */
    public RequestCapture freeze() {
        ParsedRequest parsed = parse();
        return RequestCapture.of(httpVersion, parsed);
    }

    protected ParsedRequest parse() {
        HttpMethod method = HttpMethod.valueOf(getMethod());
        URI url = URI.create(getUrl());
        Multimap<String, String> headers = toMultimap(getHeaders());
        @Nullable Multimap<String, Optional<String>> query = queryStringToMultimapOfOptionals(url);
        byte[] body = this.body;
        return ParsedRequest.inMemory(method, url, query, headers, body);
    }

    @Nullable
    protected static Multimap<String, Optional<String>> queryStringToMultimapOfOptionals(URI uri) {
        if (uri.getQuery() == null) {
            return null;
        }
        List<Map.Entry<String, String>> params = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
        return toMultimapOfOptionals(params);
    }

    protected static Multimap<String, Optional<String>> toMultimapOfOptionals(Iterable<Map.Entry<String, String>> nameValuePairs) {
        Multimap<String, Optional<String>> mm = ArrayListMultimap.create();
        nameValuePairs.forEach(pair -> {
            mm.put(pair.getKey(), Optional.ofNullable(pair.getValue()));
        });
        return mm;
    }

    protected Multimap<String, String> toMultimap(Iterable<? extends HarNameValuePair> nameValuePairs) {
        Multimap<String, String> mm = ArrayListMultimap.create();
        nameValuePairs.forEach(pair -> {
            mm.put(pair.getName(), pair.getValue());
        });
        return mm;
    }

}
