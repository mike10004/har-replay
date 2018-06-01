package io.github.mike10004.vhs.bmp;

import net.lightbody.bmp.mitm.TrustSource;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NanohttpdTlsEndpointFactoryTest {

    @Test
    public void createTrustSource() throws Exception {
        KeystoreData keystoreData = BmpTests.generateKeystoreForUnitTest(null);
        TrustSource trustSource = NanohttpdTlsEndpointFactory.createTrustSource(keystoreData);
        assertNotNull("trustSource", trustSource);
        int numTrustedCAs = trustSource.getTrustedCAs().length;
        assertTrue("more than one trusted CA", numTrustedCAs > 1);
    }

}