package io.github.mike10004.vhs.bmp;

import org.junit.Test;

import java.security.KeyStore;

import static org.junit.Assert.*;

public class KeystoreTypeTest {

    /**
     * Makes sure we can load a keystore for every type.
     */
    @Test
    public void loadKeyStore() throws Exception {
        for (KeystoreType kt : KeystoreType.values()) {
            kt.loadKeyStore();
        }
    }
}