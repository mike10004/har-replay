package io.github.mike10004.harreplay.vhsimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.jackson.ExceptionIgnoringIntegerDeserializer;
import de.sstoehr.harreader.jackson.MapperFactory;

import java.util.Date;

public interface HarReaderFactory {

    HarReader createReader();

    static HarReaderFactory easier() {
        return new EasierHarReaderFactory();
    }

    static HarReaderFactory fromMapper(MapperFactory mapperFactory) {
        return () -> new HarReader(mapperFactory);
    }

    static HarReaderFactory stock() {
        return HarReader::new;
    }

}
