package io.github.mike10004.vhs.bmp;

import com.google.common.annotations.VisibleForTesting;
import net.lightbody.bmp.mitm.CertificateAndKey;
import net.lightbody.bmp.mitm.CertificateInfo;
import net.lightbody.bmp.mitm.RootCertificateGenerator;
import net.lightbody.bmp.mitm.exception.KeyStoreAccessException;
import net.lightbody.bmp.mitm.util.KeyStoreUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Random;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * Implementation of a keystore generator that uses the JRE's key generation facilities.
 */
public class JreKeystoreGenerator implements KeystoreGenerator {

    private static final Logger log = LoggerFactory.getLogger(JreKeystoreGenerator.class);

    private static final int KEYSTORE_BUFFER_LEN = 8192 * 2;

    static final String KEYSTORE_PRIVATE_KEY_ALIAS = "key";

    private final Random random;
    private final KeystoreType keystoreType;

    public JreKeystoreGenerator(KeystoreType keystoreType, Random random) {
        this.random = requireNonNull(random);
        this.keystoreType = requireNonNull(keystoreType);
    }

    public JreKeystoreGenerator(KeystoreType keystoreType) {
        this(keystoreType, new SecureRandom());
    }

    /**
     * Exports the keyStore to the specified output stream.
     *
     * @param fos             output stream to write to
     * @param keyStore         KeyStore to export
     * @param keystorePassword the password for the KeyStore
     */
    protected static void saveKeyStore(OutputStream fos, KeyStore keyStore, char[] keystorePassword) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        keyStore.store(fos, keystorePassword);
    }

    /**
     * Creates a new KeyStore containing the specified root certificate and private key.
     *
     * @param keyStoreType       type of the generated KeyStore, such as PKCS12 or JKS
     * @param certificate        root certificate to add to the KeyStore
     * @param privateKeyAlias    alias for the private key in the KeyStore
     * @param privateKey         private key to add to the KeyStore
     * @param privateKeyPassword password for the private key
     * @param provider           JCA provider to use, or null to use the system default
     * @return new KeyStore containing the root certificate and private key
     * @see KeyStoreUtil#createRootCertificateKeyStore(String, X509Certificate, String, PrivateKey, String, String)
     */
    @SuppressWarnings("SameParameterValue")
    private static KeyStore _createRootCertificateKeyStore(String keyStoreType,
                                                           X509Certificate certificate,
                                                           String privateKeyAlias,
                                                           PrivateKey privateKey,
                                                           char[] privateKeyPassword,
                                                           @Nullable String provider) {
        if (privateKeyPassword == null) {
            throw new IllegalArgumentException("Must specify a KeyStore password");
        }

        KeyStore newKeyStore = KeyStoreUtil.createEmptyKeyStore(keyStoreType, provider);

        try {
            newKeyStore.setKeyEntry(privateKeyAlias, privateKey, privateKeyPassword, new Certificate[]{certificate});
        } catch (KeyStoreException e) {
            throw new KeyStoreAccessException("Unable to store certificate and private key in KeyStore", e);
        }
        return newKeyStore;
    }

    protected static KeyStore createRootCertificateKeyStore(KeystoreType keyStoreType,
                                                         CertificateAndKey rootCertificateAndKey,
                                                         String privateKeyAlias,
                                                         char[] password) {
        return _createRootCertificateKeyStore(keyStoreType.name(), rootCertificateAndKey.getCertificate(), privateKeyAlias, rootCertificateAndKey.getPrivateKey(), password, null);
    }

    @Override
    public KeystoreData generate() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        return generate(null);
    }

    @VisibleForTesting
    static char[] asciiBytesToChars(byte[] asciiBytes) {
        char[] chars = new char[asciiBytes.length];
        for (int i = 0; i < chars.length; i++) {
            checkArgument(asciiBytes[i] >= 0 && asciiBytes[i] < 128, "char at index %s is not ascii: %s", i, asciiBytes[i]);
            chars[i] = (char) asciiBytes[i];
        }
        return chars;
    }

    public KeystoreData generate(@Nullable String certificateCommonName) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        byte[] bytes = new byte[PASSWORD_GENERATION_BYTE_LENGTH];
        random.nextBytes(bytes);
        byte[] asciiBytes = Base64.getEncoder().encode(bytes);
        Arrays.fill(bytes, (byte) 0);
        char[] password = asciiBytesToChars(asciiBytes);
        Arrays.fill(asciiBytes, (byte) 0);
        byte[] keystoreBytes = doGenerate(KEYSTORE_PRIVATE_KEY_ALIAS, password, certificateCommonName);
        return new KeystoreData(keystoreType, keystoreBytes, KEYSTORE_PRIVATE_KEY_ALIAS, password);
    }

    private static final int PASSWORD_GENERATION_BYTE_LENGTH = 32;

    /**
     * Creates a dynamic CA root certificate generator using default settings (2048-bit RSA keys).
     * @return the generator
     */
    protected RootCertificateGenerator buildCertificateGenerator(@Nullable String commonName) {
        if (commonName == null) {
            commonName = getDefaultCommonName();
        }
        long timestamp = System.currentTimeMillis();
        CertificateInfo certificateInfo = new CertificateInfo()
                .commonName(commonName)
                .organization("CA dynamically generated by KeystoreGenerator")
                .notBefore(new Date(timestamp - 365L * 24L * 60L * 60L * 1000L))
                .notAfter(new Date(timestamp + 365L * 24L * 60L * 60L * 1000L));
        RootCertificateGenerator rootCertificateGenerator = RootCertificateGenerator.builder()
                .certificateInfo(certificateInfo)
                .build();
        return rootCertificateGenerator;
    }

    /**
     * Creates a default CN field for a certificate, using the hostname of this machine and the current time.
     */
    private static String getDefaultCommonName() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "localhost";
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");

        String currentDateTime = dateFormat.format(new Date());

        String defaultCN = "Generated CA (" + hostname + ") " + currentDateTime;

        // CN fields can only be 64 characters
        return defaultCN.length() <= 64 ? defaultCN : defaultCN.substring(0, 63);
    }

    protected byte[] doGenerate(String privateKeyAlias, char[] keystorePassword, @Nullable String commonName) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        RootCertificateGenerator rootCertificateGenerator = buildCertificateGenerator(commonName);
        byte[] keystoreBytes = saveRootCertificateAndKey(rootCertificateGenerator.load(),
                privateKeyAlias, keystorePassword);
        log.debug("saved keystore to {}-byte array", keystoreBytes.length);
        return keystoreBytes;
    }

    /**
     * Saves the generated certificate and private key as a file, using the specified password to protect the key store.
     * @param certificateAndKey the root certificate and key
     * @param privateKeyAlias alias for the private key in the KeyStore
     * @param password        password for the private key and the KeyStore
     */
    private byte[] saveRootCertificateAndKey(CertificateAndKey certificateAndKey,
                                             String privateKeyAlias,
                                             char[] password) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        KeyStore keyStore = createRootCertificateKeyStore(keystoreType, certificateAndKey, privateKeyAlias, password);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(KEYSTORE_BUFFER_LEN);
        saveKeyStore(baos, keyStore, password);
        baos.flush();
        return baos.toByteArray();
    }

}
