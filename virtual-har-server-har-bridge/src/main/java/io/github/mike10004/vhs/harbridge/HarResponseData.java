package io.github.mike10004.vhs.harbridge;

import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Interface representing response data.
 */
public interface HarResponseData {

    List<Map.Entry<String, String>> headers();
    MediaType getContentType();
    ByteSource getBody();

    static HarResponseData of(Iterable<Map.Entry<String, String>> headers, @Nullable MediaType contentType, @Nullable ByteSource body) {
        return new SimpleHarResponseData(headers, contentType, body);
    }

    default HarResponseDataTransformer transformer() {
        return new HarResponseDataTransformer(this);
    }

    @Nullable
    default String getFirstHeaderValue(String headerName) {
        Objects.requireNonNull(headerName, "name");
        for (Map.Entry<String, String> entry : headers()) {
            if (headerName.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
