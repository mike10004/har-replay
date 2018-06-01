package io.github.mike10004.vhs;

import io.github.mike10004.vhs.harbridge.HarResponseEncoding;
import io.github.mike10004.vhs.harbridge.ParsedRequest;

public interface HarResponseEncoderFactory<E> {

    HarResponseEncoding getEncoder(ParsedRequest request, E harEntry);

    static <E> HarResponseEncoderFactory<E> alwaysIdentityEncoding() {
        return (request, entry) -> HarResponseEncoding.unencoded();
    }
}
