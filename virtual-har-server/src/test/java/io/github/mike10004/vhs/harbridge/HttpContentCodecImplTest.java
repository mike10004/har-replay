package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(Parameterized.class)
public class HttpContentCodecImplTest {

    private final String encoding;

    public HttpContentCodecImplTest(String encoding) {
        this.encoding = encoding;
    }

    @Parameterized.Parameters
    public static List<String> encodings() {
        return ImmutableList.copyOf(HttpContentCodecs.getSupportedEncodings());
    }

    @Test
    public void compressAndDecompress_byteArrays() throws Exception {
        byte[] expected = "This is the input".getBytes(StandardCharsets.US_ASCII);
        HttpContentCodec codec = HttpContentCodecs.getCodec(encoding);
        assertNotNull("codec for " + encoding, codec);
        byte[] compressed;
        try {
            compressed = codec.compress(expected);
        } catch (IOException e) {
            maybeIgnoreCompressionException();
            throw e;
        }
        if (!HttpContentCodecs.CONTENT_ENCODING_IDENTITY.equals(encoding)) {
            assertFalse("compressed array is different", Arrays.equals(expected, compressed));
        }
        byte[] decompressed;
        try {
            decompressed = codec.decompress(compressed);
        } catch (IOException e) {
            maybeIgnoreDecompressionException();
            throw e;
        }
        assertArrayEquals("after compression & decompression with encoding " + encoding, expected, decompressed);
    }

    private static final ImmutableSet<String> IGNORE_COMPRESSION = ImmutableSet.of("br");

    private void maybeIgnoreCompressionException() {
        Assume.assumeFalse("ignoring compression exception for encoding " + encoding, IGNORE_COMPRESSION.contains(encoding)); // ignores test if compression fails for certain encodings
    }

    private void maybeIgnoreDecompressionException() {
    }
}
