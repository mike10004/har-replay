package io.github.mike10004.vhs.harbridge;

import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import org.junit.Test;

import static org.junit.Assert.*;

public class Base64ByteSourceTest {

    @Test
    public void size() throws Exception {
        for (int n = 0; n < 16; n++) {
            byte[] bytes = new byte[n];
            String base64Data = BaseEncoding.base64().encode(bytes);
            ByteSource decodingSource = Base64ByteSource.wrap(base64Data);
            assertEquals(String.format("base64 length %d", base64Data.length()), n, decodingSource.size());
        }
    }
}