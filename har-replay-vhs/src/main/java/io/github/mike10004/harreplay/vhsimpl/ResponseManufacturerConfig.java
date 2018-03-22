package io.github.mike10004.harreplay.vhsimpl;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public class ResponseManufacturerConfig {

    public final Path mappedFileResolutionRoot;
    public final Charset charsetFallbackPreference;

    public ResponseManufacturerConfig(Path mappedFileResolutionRoot, Charset charsetFallbackPreference) {
        this.mappedFileResolutionRoot = requireNonNull(mappedFileResolutionRoot);
        this.charsetFallbackPreference = requireNonNull(charsetFallbackPreference);
    }

    private static final ResponseManufacturerConfig DEFAULT_INSTANCE = new ResponseManufacturerConfig(new File(System.getProperty("user.dir")).toPath(), StandardCharsets.UTF_8);

    public static ResponseManufacturerConfig getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }
}
