package io.github.mike10004.vhs.bmp;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Interface that defines methods to generate keystores.
 */
public interface KeystoreGenerator {

    /**
     * Generates keystore data with a new randomly-generated password.
     * @param certificateCommonName the common name (CN) to be assigned to the
     *                              certificate associated with the keystore
     * @return the keystore data
     * @throws IOException on I/O error
     * @throws GeneralSecurityException if a security-related error occurs
     */
    KeystoreData generate(@Nullable String certificateCommonName) throws IOException, GeneralSecurityException;

    /**
     * Generates keystore data with a new randomly-generated password.
     * @return the keystore data
     * @throws IOException on I/O error
     */
    default KeystoreData generate() throws IOException, GeneralSecurityException {
        return generate(null);
    }

    /**
     * Returns an implementation that uses the JRE's key generation facilities.
     * @param keystoreType keystore type
     * @return a new generator instance
     */
    static KeystoreGenerator createJreGenerator(KeystoreType keystoreType) {
        return new JreKeystoreGenerator(keystoreType);
    }
}
