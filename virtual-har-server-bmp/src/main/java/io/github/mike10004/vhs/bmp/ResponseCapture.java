package io.github.mike10004.vhs.bmp;

import io.netty.handler.codec.http.HttpResponse;

import static java.util.Objects.requireNonNull;

public class ResponseCapture {

    public final HttpResponse response;
    public final ResponseOrigin origin;

    public ResponseCapture(HttpResponse response, ResponseOrigin origin) {
        this.response = requireNonNull(response);
        this.origin = requireNonNull(origin);
    }

    public static ResponseCapture matched(HttpResponse response) {
        return new ResponseCapture(response, ResponseOrigin.MATCHED_ENTRY);
    }

    public static ResponseCapture unmatched(HttpResponse response) {
        return new ResponseCapture(response, ResponseOrigin.UNMATCHED);
    }

    public static ResponseCapture error(HttpResponse response) {
        return new ResponseCapture(response, ResponseOrigin.ERROR);
    }

    public enum ResponseOrigin {
        MATCHED_ENTRY,
        UNMATCHED,
        ERROR
    }
}
