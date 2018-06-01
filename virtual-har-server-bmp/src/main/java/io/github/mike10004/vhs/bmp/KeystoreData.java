package io.github.mike10004.vhs.bmp;

import com.google.common.base.Suppliers;
import net.lightbody.bmp.mitm.CertificateAndKey;
import net.lightbody.bmp.mitm.CertificateAndKeySource;
import net.lightbody.bmp.mitm.KeyStoreCertificateSource;
import net.lightbody.bmp.mitm.exception.ImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class KeystoreData {

    private static final Logger log = LoggerFactory.getLogger(KeystoreData.class);

    public final KeystoreType keystoreType;
    public final byte[] keystoreBytes;
    public final String privateKeyAlias;
    public final char[] keystorePassword;

    public KeystoreData(KeystoreType keystoreType, byte[] keystoreBytes, String privateKeyAlias, char[] keystorePassword) {
        this.keystoreBytes = requireNonNull(keystoreBytes);
        this.privateKeyAlias = requireNonNull(privateKeyAlias);
        this.keystorePassword = keystorePassword;
        this.keystoreType = requireNonNull(keystoreType);
    }

    private static CertificateAndKey loadCertificateAndKey(byte[] keyStoreBytes, KeystoreType keystoreType,
                                                           String privateKeyAlias, char[] keyStorePasswordChars) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        final KeyStore keyStore = loadKeystore(keystoreType, keyStoreBytes, keyStorePasswordChars);
        KeyStoreCertificateSource keyStoreCertificateSource = new KeyStoreCertificateSource(keyStore, privateKeyAlias, String.copyValueOf(keyStorePasswordChars));
        return keyStoreCertificateSource.load();
    }

    public KeyStore loadKeystore() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        return loadKeystore(keystoreType, keystoreBytes, keystorePassword);
    }

    private static KeyStore loadKeystore(KeystoreType keystoreType, byte[] keystoreBytes, char[] keystorePassword) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore keystore = keystoreType.loadKeyStore();
        try (InputStream stream = new ByteArrayInputStream(keystoreBytes)) {
            keystore.load(stream, keystorePassword);
        }
        return keystore;
    }

    @Override
    public String toString() {
        return String.format("KeystoreData{type=%s, byte[%s]}", keystoreType, keystoreBytes.length);
    }

    public CertificateAndKeySource asCertificateAndKeySource() {
        return new MemoryKeyStoreCertificateSource(keystoreType, keystoreBytes, privateKeyAlias, keystorePassword);
    }

    protected static class MemoryKeyStoreCertificateSource implements CertificateAndKeySource {

        private final Supplier<CertificateAndKey> certificateAndKey;

        public MemoryKeyStoreCertificateSource(KeystoreType keystoreType, byte[] keystoreBytes, String privateKeyAlias, char[] keyStorePassword) {
            certificateAndKey = Suppliers.memoize(() -> {
                log.debug("loading and memoizing keystore of type {} with key at alias {}", keystoreType, privateKeyAlias);
                try {
                    return loadCertificateAndKey(keystoreBytes, keystoreType, privateKeyAlias, keyStorePassword);
                } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException e) {
                    throw new ImportException(e);
                }
            });
        }

        @Override
        public CertificateAndKey load() {
            return certificateAndKey.get();
        }

    }
}
