package io.github.mike10004.harreplay.vhsimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.browserup.harreader.HarReader;
import com.browserup.harreader.HarReaderMode;
import com.browserup.harreader.jackson.ExceptionIgnoringIntegerDeserializer;
import com.browserup.harreader.jackson.MapperFactory;

import java.util.Date;

public class EasierHarReaderFactory implements HarReaderFactory {

    @Override
    public HarReader createReader() {
        return new HarReader(createMapperFactory());
    }

    protected MapperFactory createMapperFactory() {
        return new MapperFactory() {
            @Override
            public ObjectMapper instance(HarReaderMode mode) {
                ObjectMapper mapper = new ObjectMapper();
                SimpleModule module = new SimpleModule();
                if (mode == HarReaderMode.LAX) {
                    module.addDeserializer(Integer.class, new ExceptionIgnoringIntegerDeserializer());
                }
                module.addDeserializer(Date.class, new MoreFlexibleDateDeserializer());
                mapper.registerModule(module);
                return mapper;
            }
        };
    }
}
