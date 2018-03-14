package io.github.mike10004.harreplay.vhsimpl;

import io.github.mike10004.vhs.bmp.KeystoreGenerator;
import io.github.mike10004.vhs.bmp.KeystoreType;

import static java.util.Objects.requireNonNull;

public class VhsReplayManagerConfig {

    private static final VhsReplayManagerConfig DEFAULT = builder().build();

    public final KeystoreGenerator keystoreGenerator;
    public final ResponseManufacturerProvider responseManufacturerProvider;

    private VhsReplayManagerConfig(Builder builder) {
        keystoreGenerator = builder.keystoreGenerator;
        responseManufacturerProvider = builder.responseManufacturerProvider;
    }

    public static VhsReplayManagerConfig getDefault() {
        return DEFAULT;
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unused")
    public static final class Builder {

        private KeystoreGenerator keystoreGenerator;
        private ResponseManufacturerProvider responseManufacturerProvider;

        private Builder() {
            keystoreGenerator = KeystoreGenerator.createJreGenerator(KeystoreType.PKCS12);
            responseManufacturerProvider = SstoehrResponseManfacturerProvider.createDefault();
        }

        public Builder keystoreGenerator(KeystoreGenerator val) {
            keystoreGenerator = requireNonNull(val);
            return this;
        }

        public Builder responseManufacturerProvider(ResponseManufacturerProvider responseManufacturerProvider) {
            this.responseManufacturerProvider = requireNonNull(responseManufacturerProvider);
            return this;
        }

        public VhsReplayManagerConfig build() {
            return new VhsReplayManagerConfig(this);
        }
    }
}
