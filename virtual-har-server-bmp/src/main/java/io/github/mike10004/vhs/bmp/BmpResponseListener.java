package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * Callback interface that defines a method invoked in exceptional cases
 * when a response manufacturer can't manufacture a response.
 * @see BmpResponseManufacturer
 * @see HarReplayManufacturer#HarReplayManufacturer(EntryMatcher, Iterable, BmpResponseListener)
 */
public interface BmpResponseListener {

    void responding(RequestCapture requestCapture, ResponseCapture responseCapture);

    static BmpResponseListener inactive() {
        return (req, rsp) -> {};
    }
}
