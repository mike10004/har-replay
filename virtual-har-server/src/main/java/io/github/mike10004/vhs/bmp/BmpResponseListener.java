package io.github.mike10004.vhs.bmp;

/**
 * Callback interface that defines a method invoked in exceptional cases
 * when a response manufacturer can't manufacture a response.
 * @see BmpResponseManufacturer
 * @see HarReplayManufacturer#HarReplayManufacturer(io.github.mike10004.vhs.EntryMatcher, Iterable)
 */
public interface BmpResponseListener {

    void responding(RequestCapture requestCapture, ResponseCapture responseCapture);

    static BmpResponseListener inactive() {
        return (req, rsp) -> {};
    }
}
