package io.github.mike10004.vhs.harbridge;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Interface that defines a method to transform unencoded response data such
 * that it meets some desired encoding requirement.
 */
public interface HarResponseEncoding {

    HarResponseData transformUnencoded(HarResponseData unencoded);

    static HarResponseEncoding useEncoding(@Nullable String contentEncodingHeaderValue, @Nullable String acceptEncodingHeaderValue) {
        List<String> encodings = HttpContentCodecs.parseEncodings(contentEncodingHeaderValue);
        if (encodings.stream().anyMatch(token -> !HttpContentCodecs.CONTENT_ENCODING_IDENTITY.equalsIgnoreCase(token))) {
            return WrappingResponseEncoding.fromHeaderValues(encodings, acceptEncodingHeaderValue);
        } else {
            return unencoded();
        }
    }

    static HarResponseEncoding unencoded() {
        return unencoded -> unencoded;
    }
}
