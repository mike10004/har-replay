package io.github.mike10004.vhs.harbridge;

import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;

import java.io.IOException;
import java.io.InputStream;

import static java.util.Objects.requireNonNull;

@SuppressWarnings("Guava")
class Base64ByteSource extends ByteSource {

    private static final char PAD_CHAR = '=';
    private final String base64Data;
    private final long decodedLength;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private final com.google.common.base.Optional<Long> sizeIfKnown;
    private final ByteSource decodingSource;

    protected Base64ByteSource(String base64Data) {
        this.base64Data = requireNonNull(base64Data);
        int unpaddedLength = unpaddedLength(base64Data); // in case of extra padding
        this.decodedLength = unpaddedLength * 6 / 8; // each char represents 6 bits; round down because of padding
        this.sizeIfKnown = com.google.common.base.Optional.of(decodedLength);
        decodingSource = BaseEncoding.base64().decodingSource(CharSource.wrap(base64Data));
    }

    static int unpaddedLength(String base64Data) {
        int numPadChars = 0;
        for (int i = base64Data.length() - 1; i >= 0; i--) {
            if (base64Data.charAt(i) == PAD_CHAR) {
                numPadChars++;
            }
        }
        return base64Data.length() - numPadChars;
    }

    @Override
    public InputStream openStream() throws IOException {
        return decodingSource.openStream();
    }

    @Override
    public boolean isEmpty() {
        return base64Data.isEmpty();
    }

    @SuppressWarnings("Guava")
    @Override
    public com.google.common.base.Optional<Long> sizeIfKnown() {
        return sizeIfKnown;
    }

    @Override
    public long size() {
        return decodedLength;
    }

    public static Base64ByteSource wrap(String base64Data) {
        return new Base64ByteSource(base64Data);
    }
}
