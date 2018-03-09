package io.github.mike10004.harreplay.vhsimpl;

import io.github.mike10004.vhs.bmp.BmpResponseListener;
import io.github.mike10004.vhs.bmp.KeystoreGenerator;
import io.github.mike10004.vhs.bmp.KeystoreType;

import java.io.File;
import java.nio.file.Path;

public class VhsReplayManagerConfig {

    private static final VhsReplayManagerConfig DEFAULT = builder().build();

    public final Path mappedFileResolutionRoot;
    public final KeystoreGenerator keystoreGenerator;
    public final BmpResponseListener bmpResponseListener;

    private VhsReplayManagerConfig(Builder builder) {
        mappedFileResolutionRoot = builder.mappedFileResolutionRoot;
        keystoreGenerator = builder.keystoreGenerator;
        bmpResponseListener = builder.bmpResponseListener;
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

        private Builder() {
            mappedFileResolutionRoot = new File(System.getProperty("user.dir")).toPath();
            bmpResponseListener = (x, y) -> {};
            keystoreGenerator = KeystoreGenerator.createJreGenerator(KeystoreType.PKCS12);
        }

        public Builder mappedFileResolutionRoot(Path val) {
            mappedFileResolutionRoot = val;
            return this;
        }

        public Builder keystoreGenerator(KeystoreGenerator val) {
            keystoreGenerator = val;
            return this;
        }

        public Builder bmpResponseListener(BmpResponseListener val) {
            bmpResponseListener = val;
            return this;
        }

        public VhsReplayManagerConfig build() {
            return new VhsReplayManagerConfig(this);
        }
    }
}
