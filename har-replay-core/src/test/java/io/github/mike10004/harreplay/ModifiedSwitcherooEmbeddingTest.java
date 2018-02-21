package io.github.mike10004.harreplay;

import org.junit.Test;

import java.net.URL;

/**
 * Test that confirms we have configured pom.xml to pack the modified-switcheroo extension
 * such that it is available on the classpath. This one doesn't need all of the
 * Xvfb and temporary folder setup and cleanup, so we have it in a separate test class.
 * It does need the generate-resources phase to have executed, though.
 */
public class ModifiedSwitcherooEmbeddingTest {

    @Test
    public void getExtensionCrxResource() {
        URL resource = ModifiedSwitcheroo.getExtensionCrxResource();
        System.out.format("extension on classpath at %s -> %s%n", ModifiedSwitcheroo.RESOURCE_PATH, resource);
    }
}
