package io.github.mike10004.vhs.harbridge;

import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;

import javax.annotation.Nullable;

public interface TypedContent {

    MediaType getContentType();
    ByteSource asByteSource();

    static TypedContent identity(ByteSource uncompressed, @Nullable String contentType) {
        // TODO catch MediaType.parse exception
        return identity(uncompressed, contentType == null ? MediaType.OCTET_STREAM : MediaType.parse(contentType));
    }

    static TypedContent identity(ByteSource uncompressed, MediaType contentType) {
        return new FrozenTypedContent(contentType, uncompressed);
    }
}
