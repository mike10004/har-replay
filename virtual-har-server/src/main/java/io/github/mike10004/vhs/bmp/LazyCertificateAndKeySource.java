package io.github.mike10004.vhs.bmp;

import com.google.common.base.Suppliers;
import net.lightbody.bmp.mitm.CertificateAndKey;
import net.lightbody.bmp.mitm.CertificateAndKeySource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.function.Supplier;

/**
 * Lazily-generating certificate and key source. On demand, meaning when
 * {@link #load()} is invoked, the certificate will be generated.
 */
public class LazyCertificateAndKeySource implements CertificateAndKeySource {

    private volatile Supplier<CertificateAndKey> memoizedCertificateAndKey;

    /**
     * Constructs a new instance with a supplier made from a keystore generator.
     * On demand, a certificate with the given common name will be generated.
     * @param keystoreGenerator the keystore generator
     * @param certificateCommonName the common name (CN) for the certificate
     */
    public LazyCertificateAndKeySource(KeystoreGenerator keystoreGenerator, @Nullable String certificateCommonName) {
        this(() -> {
            try {
                return keystoreGenerator.generate(certificateCommonName).asCertificateAndKeySource().load();
            } catch (IOException | GeneralSecurityException e) {
                throw new CertificateGenerationException(e);
            }
        });
    }

    public LazyCertificateAndKeySource(Supplier<CertificateAndKey> certificateAndKeySupplier) {
        memoizedCertificateAndKey = Suppliers.memoize(certificateAndKeySupplier::get);
    }

    @Override
    public CertificateAndKey load() {
        return memoizedCertificateAndKey.get();
    }

    /**
     * Exception that wraps checked exceptions thrown by the keystore generation process.
     * @see KeystoreGenerator#generate(String)
     */
    public static class CertificateGenerationException extends RuntimeException {
        public CertificateGenerationException(String message) {
            super(message);
        }

        @SuppressWarnings("unused")
        public CertificateGenerationException(String message, Throwable cause) {
            super(message, cause);
        }

        public CertificateGenerationException(Throwable cause) {
            super(cause);
        }
    }
}
