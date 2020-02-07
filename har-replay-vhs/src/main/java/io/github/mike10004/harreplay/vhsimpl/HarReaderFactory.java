package io.github.mike10004.harreplay.vhsimpl;

import com.browserup.harreader.HarReader;

public interface HarReaderFactory {

    HarReader createReader();

    static HarReaderFactory easier() {
        return new EasierHarReaderFactory();
    }

    static HarReaderFactory stock() {
        return HarReader::new;
    }

}
