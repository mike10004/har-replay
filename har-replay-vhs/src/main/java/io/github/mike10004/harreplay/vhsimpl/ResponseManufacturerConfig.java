package io.github.mike10004.harreplay.vhsimpl;

import java.io.File;
import java.nio.file.Path;

public class ResponseManufacturerConfig {

    public final Path mappedFileResolutionRoot;

    public ResponseManufacturerConfig(Path mappedFileResolutionRoot) {
        this.mappedFileResolutionRoot = mappedFileResolutionRoot;
    }

    private static final ResponseManufacturerConfig DEFAULT_INSTANCE = new ResponseManufacturerConfig(new File(System.getProperty("user.dir")).toPath());

    public static ResponseManufacturerConfig getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }
}
