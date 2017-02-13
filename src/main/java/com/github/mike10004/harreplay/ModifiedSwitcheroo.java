package com.github.mike10004.harreplay;

import com.google.common.io.ByteSource;
import com.google.common.io.Resources;

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
        return Resources.asByteSource(ModifiedSwitcheroo.class.getResource("/modified-switcheroo.crx"));
    }
}
