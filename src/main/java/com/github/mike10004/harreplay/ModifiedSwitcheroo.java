package com.github.mike10004.harreplay;

import com.google.common.io.ByteSource;
import com.google.common.io.Resources;

import java.net.URL;

/**
 * Static utility methods related to the Modified Switcheroo Chrome extension embedded in this library.
 */
public class ModifiedSwitcheroo {

    private ModifiedSwitcheroo() {}

    /**
     * Gets a source providing the bytes of a Chrome extension file. Write these bytes to a CRX file
     * to use it with Chrome.
     * @return the byte source
     */
    public static ByteSource getExtensionCrxByteSource() {
        String resourcePath = "/modified-switcheroo.crx";
        URL resource = ModifiedSwitcheroo.class.getResource(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("not found: classpath:" + resourcePath);
        }
        return Resources.asByteSource(resource);
    }
}
