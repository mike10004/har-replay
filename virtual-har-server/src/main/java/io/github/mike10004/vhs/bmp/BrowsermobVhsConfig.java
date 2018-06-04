package io.github.mike10004.vhs.bmp;

import net.lightbody.bmp.mitm.CertificateAndKeySource;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public class BrowsermobVhsConfig {

    static final KeystoreType DEFAULT_KEYSTORE_TYPE = KeystoreType.PKCS12;

    @Nullable
    public final Integer port;
    public final ScratchDirProvider scratchDirProvider;
    public final BmpResponseManufacturer bmpResponseManufacturer;
    public final TlsEndpointFactory tlsEndpointFactory;
    public final CertificateAndKeySourceFactory certificateAndKeySourceFactory;
    public final BmpResponseListener bmpResponseListener;

    private BrowsermobVhsConfig(Builder builder) {
        port = builder.port;
        scratchDirProvider = builder.scratchDirProvider;
        bmpResponseManufacturer = builder.bmpResponseManufacturer;
        tlsEndpointFactory = builder.tlsEndpointFactory;
        certificateAndKeySourceFactory = builder.certificateAndKeySourceFactory;
        bmpResponseListener = builder.bmpResponseListener;
    }

    public static Builder builder(BmpResponseManufacturer bmanufacturer) {
        return new Builder(bmanufacturer);
    }

    public interface DependencyFactory<T> {
        T produce(BrowsermobVhsConfig config, Path scratchDir) throws IOException;
    }

    public interface CertificateAndKeySourceFactory extends DependencyFactory<CertificateAndKeySource> {

        static CertificateAndKeySourceFactory predefined(CertificateAndKeySource certificateAndKeySource) {
            return new CertificateAndKeySourceFactory() {
                @Override
                public CertificateAndKeySource produce(BrowsermobVhsConfig config, Path scratchDir) {
                    return certificateAndKeySource;
                }
            };
        }
    }

    public interface TlsEndpointFactory extends DependencyFactory<TlsEndpoint> {
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static final class Builder {

        @Nullable
        private Integer port;
        private ScratchDirProvider scratchDirProvider;
        private final BmpResponseManufacturer bmpResponseManufacturer;
        private TlsEndpointFactory tlsEndpointFactory;
        private CertificateAndKeySourceFactory certificateAndKeySourceFactory;
        private BmpResponseListener bmpResponseListener = BmpResponseListener.inactive();

        private Builder(BmpResponseManufacturer bmpResponseManufacturer) {
            this.bmpResponseManufacturer = requireNonNull(bmpResponseManufacturer);
            scratchDirProvider = ScratchDirProvider.under(FileUtils.getTempDirectory().toPath());
            tlsEndpointFactory = (config, dir) -> TlsEndpoint.createDefault();
            certificateAndKeySourceFactory = (config, dir) -> new LazyCertificateAndKeySource(KeystoreGenerator.createJreGenerator(DEFAULT_KEYSTORE_TYPE), null);
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder scratchDirProvider(ScratchDirProvider val) {
            scratchDirProvider = requireNonNull(val);
            return this;
        }

        public Builder tlsEndpointFactory(TlsEndpointFactory val) {
            tlsEndpointFactory = requireNonNull(val);
            return this;
        }

        public Builder certificateAndKeySource(CertificateAndKeySource certificateAndKeySource) {
            return certificateAndKeySourceFactory(CertificateAndKeySourceFactory.predefined(certificateAndKeySource));
        }

        public Builder certificateAndKeySourceFactory(CertificateAndKeySourceFactory val) {
            certificateAndKeySourceFactory = requireNonNull(val);
            return this;
        }

        public Builder responseListener(BmpResponseListener bmpResponseListener) {
            this.bmpResponseListener = requireNonNull(bmpResponseListener);
            return this;
        }

        public BrowsermobVhsConfig build() {
            return new BrowsermobVhsConfig(this);
        }
    }
}
