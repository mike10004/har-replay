package com.github.mike10004.harreplay;

import com.google.common.io.ByteSource;
import com.google.common.io.Resources;

import java.net.URL;

/**
 * Static utility methods related to the Modified Switcheroo Chrome extension embedded in this library.
 * The Switcheroo extension allows a user to create rules that cause URLs to be rewritten. We need such
 * a rule to rewrite https:// URLs to http:// URLs in order for the HAR replay proxy to serve responses
 * recorded for HTTPS requests.
 */
public class ModifiedSwitcheroo {

    private ModifiedSwitcheroo() {}

    static final String RESOURCE_PATH = "/modified-switcheroo.crx";

    /**
     * Gets the URL of the resource that is the Chrome extension file for the modified
     * switcheroo extension file.
     * @return the URL of the modified switcheroo crx file
     */
    public static URL getExtensionCrxResource() {
        URL resource = ModifiedSwitcheroo.class.getResource(RESOURCE_PATH);
        if (resource == null) {
            throw new IllegalStateException("not found: classpath:" + RESOURCE_PATH);
        }
        return resource;
    }

    /**
     * Gets a source providing the bytes of a Chrome extension file. Write these bytes to a CRX file
     * to use it with Chrome.
     * @return the byte source
     */
    public static ByteSource getExtensionCrxByteSource() {
        return Resources.asByteSource(getExtensionCrxResource());
    }
}
