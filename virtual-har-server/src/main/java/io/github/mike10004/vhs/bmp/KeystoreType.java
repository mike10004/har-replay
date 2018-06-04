package io.github.mike10004.vhs.bmp;

import java.security.KeyStore;
import java.security.KeyStoreException;

/**
 * Enumeration of keystore types. These are just the types supported
 * by these libraries, not all types supported by the JRE.
 */
public enum KeystoreType {
    PKCS12,
    JKS;

    public KeyStore loadKeyStore() throws KeyStoreException {
        return KeyStore.getInstance(name());
    }
}
