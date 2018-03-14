package io.github.mike10004.harreplay.vhsimpl;

import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.vhs.bmp.BmpResponseManufacturer;

import java.io.File;
import java.io.IOException;

public interface ResponseManufacturerProvider {

    BmpResponseManufacturer create(File harFile, ReplayServerConfig replayServerConfig) throws IOException;

}
