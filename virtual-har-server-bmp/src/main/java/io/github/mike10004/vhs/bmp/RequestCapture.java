package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.netty.handler.codec.http.HttpVersion;

import static java.util.Objects.requireNonNull;

public final class RequestCapture {

    public final HttpVersion httpVersion;
    public final ParsedRequest request;

    private RequestCapture(HttpVersion httpVersion, ParsedRequest request) {
        this.httpVersion = requireNonNull(httpVersion);
        this.request = requireNonNull(request);
    }

    public static RequestCapture of(HttpVersion httpVersion, ParsedRequest fullCapturedRequest) {
        return new RequestCapture(httpVersion, fullCapturedRequest);
    }
}
