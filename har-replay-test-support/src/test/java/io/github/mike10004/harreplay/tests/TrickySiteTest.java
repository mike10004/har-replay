package io.github.mike10004.harreplay.tests;

import com.github.mike10004.common.image.ImageInfo;
import com.github.mike10004.common.image.ImageInfos;
import com.google.common.io.ByteSource;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TrickySiteTest {

    @Test
    public void decodeFavicon() throws Exception {
        byte[] bytes = TrickySite.decodeFavicon();
        ImageInfo imageInfo = ImageInfos.read(ByteSource.wrap(bytes));
        assertEquals("format", ImageInfo.Format.PNG, imageInfo.getFormat());
    }
}
