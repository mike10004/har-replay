package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import javax.annotation.Nullable;
import java.net.URI;

public class RequestSpec {
    public String method;
    public URI url;
    public Multimap<String, String> headers;
    @Nullable
    public byte[] body;

    public RequestSpec(String method, URI url, @Nullable Multimap<String, String> headers, @Nullable byte[] body) {
        this.method = method;
        this.url = url;
        this.headers = headers == null ? ImmutableMultimap.of() : headers;
        this.body = body;
    }

    public static RequestSpec get(URI url) {
        return new RequestSpec(HttpMethodStrings.GET, url, null, null);
    }

    @SuppressWarnings("unused")
    public static final class HttpMethodStrings {
        private HttpMethodStrings() {}
        public static final String GET = "GET";
        public static final String POST = "POST";
        public static final String PUT = "PUT";
        public static final String DELETE = "DELETE";
        public static final String OPTIONS = "OPTIONS";
        public static final String PATCH = "PATCH";
    }

}
