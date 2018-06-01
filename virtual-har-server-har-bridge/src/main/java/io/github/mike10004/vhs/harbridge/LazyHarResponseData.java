package io.github.mike10004.vhs.harbridge;

import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

class LazyHarResponseData implements HarResponseData {

    private final Supplier<ByteSource> bodySupplier;
    private final Supplier<MediaType> contentTypeSupplier;
    private final Supplier<List<Map.Entry<String, String>>> headersSupplier;

    public LazyHarResponseData(Supplier<ByteSource> bodySupplier, Supplier<MediaType> contentTypeSupplier, Supplier<List<Map.Entry<String, String>>> headersSupplier) {
        this.bodySupplier = requireNonNull(bodySupplier);
        this.contentTypeSupplier = requireNonNull(contentTypeSupplier);
        this.headersSupplier = requireNonNull(headersSupplier);
    }

    @Override
    public List<Map.Entry<String, String>> headers() {
        return headersSupplier.get();
    }

    @Override
    public MediaType getContentType() {
        return contentTypeSupplier.get();
    }

    @Override
    public ByteSource getBody() {
        return bodySupplier.get();
    }


}
