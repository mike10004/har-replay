package io.github.mike10004.harreplay.vhsimpl;

import de.sstoehr.harreader.HarReaderMode;
import io.github.mike10004.vhs.bmp.BmpResponseListener;
import io.github.mike10004.vhs.bmp.KeystoreGenerator;
import io.github.mike10004.vhs.bmp.KeystoreType;

import java.io.File;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public class VhsReplayManagerConfig {

    private static final VhsReplayManagerConfig DEFAULT = builder().build();

    public final Path mappedFileResolutionRoot;
    public final KeystoreGenerator keystoreGenerator;
    public final BmpResponseListener bmpResponseListener;
    public final HarReaderFactory harReaderFactory;
    public final HarReaderMode harReaderMode;

    private VhsReplayManagerConfig(Builder builder) {
        mappedFileResolutionRoot = builder.mappedFileResolutionRoot;
        keystoreGenerator = builder.keystoreGenerator;
        bmpResponseListener = builder.bmpResponseListener;
        harReaderFactory = builder.harReaderFactory;
        harReaderMode = builder.harReaderMode;
    }

    public static VhsReplayManagerConfig getDefault() {
        return DEFAULT;
    }

    public static Builder builder() {
        return new Builder();
    }


    @SuppressWarnings("unused")
    public static final class Builder {
        private Path mappedFileResolutionRoot;
        private KeystoreGenerator keystoreGenerator;
        private BmpResponseListener bmpResponseListener;
        private HarReaderFactory harReaderFactory;
        private HarReaderMode harReaderMode;

        private Builder() {
            mappedFileResolutionRoot = new File(System.getProperty("user.dir")).toPath();
            bmpResponseListener = (x, y) -> {};
            keystoreGenerator = KeystoreGenerator.createJreGenerator(KeystoreType.PKCS12);
            harReaderFactory = HarReaderFactory.easier();
            harReaderMode = HarReaderMode.STRICT;
        }

        public Builder mappedFileResolutionRoot(Path val) {
            mappedFileResolutionRoot = requireNonNull(val);
            return this;
        }

        public Builder keystoreGenerator(KeystoreGenerator val) {
            keystoreGenerator = requireNonNull(val);
            return this;
        }

        public Builder bmpResponseListener(BmpResponseListener val) {
            bmpResponseListener = requireNonNull(val);
            return this;
        }

        public Builder harReaderFactory(HarReaderFactory harReaderFactory) {
            this.harReaderFactory = requireNonNull(harReaderFactory);
            return this;
        }

        public Builder harReaderMode(HarReaderMode harReaderMode) {
            this.harReaderMode = requireNonNull(harReaderMode);
            return this;
        }

        public VhsReplayManagerConfig build() {
            return new VhsReplayManagerConfig(this);
        }
    }
}
