package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.EntryMatcher;

/**
 * Callback interface that defines a method invoked in exceptional cases
 * when a response manufacturer can't manufacture a response.
 * @see BmpResponseManufacturer
 * @see HarReplayManufacturer#HarReplayManufacturer(EntryMatcher, Iterable, HttpAssistant)
 */
public interface BmpResponseListener {

    void responding(RequestCapture requestCapture, ResponseCapture responseCapture);

    static BmpResponseListener inactive() {
        return (req, rsp) -> {};
    }
}
