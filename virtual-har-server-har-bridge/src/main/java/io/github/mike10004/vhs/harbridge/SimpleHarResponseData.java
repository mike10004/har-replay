package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;

import javax.annotation.Nullable;
import java.util.Map;

class SimpleHarResponseData implements HarResponseData {

    private final ImmutableList<Map.Entry<String, String>> headers;
    private final MediaType contentType;
    private final ByteSource body;

    /**
     *
     * @param headers headers; if null, empty stream will be used
     * @param contentType content-type; if null, {@code application/octet-stream} will be used
     * @param body body; if null, empty byte source will be used
     */
    public SimpleHarResponseData(@Nullable Iterable<Map.Entry<String, String>> headers, @Nullable MediaType contentType, @Nullable ByteSource body) {
        this.headers = headers == null ? ImmutableList.of() : ImmutableList.copyOf(headers);
        this.contentType = contentType == null ? HarBridge.getContentTypeDefaultValue() : contentType;
        this.body = body == null ? ByteSource.empty() : body;
    }

    @Override
    public ImmutableList<Map.Entry<String, String>> headers() {
        return headers;
    }

    @Override
    public MediaType getContentType() {
        return contentType;
    }

    @Override
    public ByteSource getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "SimpleHarResponseData{" +
                "headers.size=" + headers.size() +
                ", contentType=" + contentType +
                ", body.size=" + body.sizeIfKnown().orNull() +
                '}';
    }
}
