package io.github.mike10004.harreplay.vhsimpl;

import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.bmp.BmpResponseListener;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class SstoehrResponseManfacturerProvider extends ResponseManufacturerProviderBase {

    private final HarReaderFactory harReaderFactory;
    private final HarReaderMode harReaderMode;
    private final EntryParser<HarEntry> entryParser;

    public SstoehrResponseManfacturerProvider(ResponseManufacturerConfig responseManufacturerConfig, BmpResponseListener responseListener, HarReaderFactory harReaderFactory, HarReaderMode harReaderMode) {
        this(responseManufacturerConfig, responseListener, harReaderFactory, harReaderMode, new HarBridgeEntryParser<>(new SstoehrHarBridge()));
    }

    public SstoehrResponseManfacturerProvider(ResponseManufacturerConfig responseManufacturerConfig, BmpResponseListener responseListener, HarReaderFactory harReaderFactory, HarReaderMode harReaderMode, EntryParser<HarEntry> entryParser) {
        super(responseManufacturerConfig, responseListener);
        this.harReaderFactory = requireNonNull(harReaderFactory);
        this.entryParser = requireNonNull(entryParser);
        this.harReaderMode = requireNonNull(harReaderMode);
    }

    public static ResponseManufacturerProvider createDefault() {
        return new SstoehrResponseManfacturerProvider(ResponseManufacturerConfig.getDefaultInstance(), (x, y) -> {}, HarReaderFactory.easier(), HarReaderMode.STRICT);
    }

    @Override
    protected List readHarEntries(File harFile) throws IOException {
        HarReader harReader = harReaderFactory.createReader();
        try {
            return harReader.readFromFile(harFile, harReaderMode).getLog().getEntries();
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
    }

    @Override
    protected EntryParser getHarEntryParser() {
        return entryParser;
    }


}
