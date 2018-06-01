package io.github.mike10004.vhs.harbridge;

import com.google.common.io.ByteStreams;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.Assert.assertEquals;

public class ZlibCompressorTest {

    @Test
    public void compressAndDecompress() throws Exception {
        HttpContentCodecs.ZlibCodec codec = new HttpContentCodecs.ZlibCodec();
        String expected = "this is the input";
        byte[] input = expected.getBytes();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStream compressingSink = codec.openCompressionFilter(baos, input.length)) {
            compressingSink.write(input);
        }
        byte[] compressed = baos.toByteArray();
        System.out.format("%d bytes in compressed%n", compressed.length);
        byte[] decompressed;
        try (InputStream decompressing = codec.openDecompressingStream(new ByteArrayInputStream(compressed))) {
            decompressed = ByteStreams.toByteArray(decompressing);
        }
        String actual = new String(decompressed);
        assertEquals("crunched", expected, actual);
    }

}
