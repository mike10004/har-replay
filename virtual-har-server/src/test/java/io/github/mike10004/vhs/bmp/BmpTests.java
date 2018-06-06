package io.github.mike10004.vhs.bmp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.net.MediaType;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.jackson.ExceptionIgnoringIntegerDeserializer;
import de.sstoehr.harreader.jackson.MapperFactory;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.vhs.BasicHeuristic;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.HeuristicEntryMatcher;
import io.github.mike10004.vhs.ReplaySessionState;
import io.github.mike10004.vhs.ResponseInterceptor;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static java.util.Objects.requireNonNull;

public class BmpTests {

    private BmpTests() {}

    private static final KeystoreDataCache keystoreDataCache =
            new KeystoreDataCache(new JreKeystoreGenerator(KeystoreType.PKCS12,
                    new Random(KeystoreDataCache.class.getName().hashCode())));

    private static class KeystoreDataCache {
        private final KeystoreDataSerializer keystoreDataSerializer;
        private final LoadingCache<Optional<String>, String> serializedFormCache;

        public KeystoreDataCache(KeystoreGenerator keystoreGenerator) {
            requireNonNull(keystoreGenerator);
            keystoreDataSerializer = KeystoreDataSerializer.getDefault();
            //noinspection NullableProblems
            serializedFormCache = CacheBuilder.newBuilder()
                    .build(new CacheLoader<Optional<String>, String>() {
                        @Override
                        public String load(Optional<String> key) throws Exception {
                            KeystoreData keystoreData = keystoreGenerator.generate(key.orElse(null));
                            return keystoreDataSerializer.serialize(keystoreData);
                        }
                    });
        }

        public KeystoreData get(@Nullable String certificateCommonName) {
            String serializedForm = serializedFormCache.getUnchecked(Optional.ofNullable(certificateCommonName));
            return keystoreDataSerializer.deserialize(serializedForm);
        }
    }

    public static KeystoreData generateKeystoreForUnitTest(@Nullable String certificateCommonName) {
        return keystoreDataCache.get(certificateCommonName);
    }

    private static MapperFactory tolerantMapperFactory() {
        return new MapperFactory() {
            @Override
            public ObjectMapper instance(HarReaderMode mode) {
                ObjectMapper mapper = new ObjectMapper();
                SimpleModule module = new SimpleModule();
                module.addDeserializer(Date.class, new TolerantDateDeserializer());
                if (mode == HarReaderMode.LAX) {
                    module.addDeserializer(Integer.class, new ExceptionIgnoringIntegerDeserializer());
                }
                mapper.registerModule(module);
                return mapper;
            }
        };
    }

    public static HarReplayManufacturer createManufacturer(List<HarEntry> entries, Iterable<ResponseInterceptor> responseInterceptors) throws IOException {
        System.out.println("requests contained in HAR:");
        entries.stream().map(HarEntry::getRequest).forEach(request -> {
            System.out.format("  %s %s%n", request.getMethod(), request.getUrl());
        });
        EntryMatcherFactory<ReplaySessionState> entryMatcherFactory = HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
        EntryParser<HarEntry> parser = HarBridgeEntryParser.withPlainEncoder(new SstoehrHarBridge());
        EntryMatcher<ReplaySessionState> entryMatcher = entryMatcherFactory.createEntryMatcher(entries, parser);
        return new HarReplayManufacturer(entryMatcher, responseInterceptors);
    }

    public static HarReplayManufacturer createManufacturer(File harFile, Iterable<ResponseInterceptor> responseInterceptors) throws IOException {
        List<HarEntry> entries;
        try {
            entries = new de.sstoehr.harreader.HarReader(tolerantMapperFactory()).readFromFile(harFile).getLog().getEntries();
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
        return createManufacturer(entries, responseInterceptors);
    }

    public static HarEntry buildHarEntry(de.sstoehr.harreader.model.HarRequest request, de.sstoehr.harreader.model.HarResponse response) {
        HarEntry entry = new HarEntry();
        entry.setRequest(request);
        entry.setResponse(response);
        return entry;
    }

    public static de.sstoehr.harreader.model.HarRequest buildHarRequest(de.sstoehr.harreader.model.HttpMethod method, String url, List<de.sstoehr.harreader.model.HarHeader> headers) {
        de.sstoehr.harreader.model.HarRequest request = new de.sstoehr.harreader.model.HarRequest();
        request.setUrl(url);
        request.setMethod(method);
        request.setHeaders(headers);
        return request;
    }

    public static de.sstoehr.harreader.model.HarResponse buildHarResponse(int status, List<de.sstoehr.harreader.model.HarHeader> headers, de.sstoehr.harreader.model.HarContent content) {
        de.sstoehr.harreader.model.HarResponse response = new de.sstoehr.harreader.model.HarResponse();
        response.setHeaders(headers);
        response.setStatus(status);
        response.setContent(content);
        return response;
    }

    public static List<de.sstoehr.harreader.model.HarHeader> buildHarHeaders(String...namesAndValues) {
        List<de.sstoehr.harreader.model.HarHeader> headers = new ArrayList<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            String name = namesAndValues[i], value = namesAndValues[i + 1];
            de.sstoehr.harreader.model.HarHeader h = new de.sstoehr.harreader.model.HarHeader();
            h.setName(name);
            h.setValue(value);
            headers.add(h);
        }
        return headers;
    }

    public static de.sstoehr.harreader.model.HarContent buildHarContent(String text, MediaType contentType) {
        de.sstoehr.harreader.model.HarContent content = new de.sstoehr.harreader.model.HarContent();
        content.setText(text);
        content.setMimeType(contentType.toString());
        return content;
    }
}
