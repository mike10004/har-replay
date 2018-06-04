package io.github.mike10004.vhs.harbridge;

import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;

import static java.util.Objects.requireNonNull;

class FrozenTypedContent implements TypedContent {

    private final MediaType contentType;
    private final ByteSource data;

    public FrozenTypedContent(MediaType contentType, ByteSource data) {
        this.contentType = requireNonNull(contentType);
        this.data = requireNonNull(data);
    }

    @Override
    public MediaType getContentType() {
        return contentType;
    }

    @Override
    public ByteSource asByteSource() {
        return data;
    }

    @Override
    public String toString() {
        String size = data.sizeIfKnown().toJavaUtil().map(Object::toString).orElse("?");
        return "ImmutableTypedContent{" +
                "contentType=" + contentType +
                ", data.size=" + size +
                '}';
    }
}
