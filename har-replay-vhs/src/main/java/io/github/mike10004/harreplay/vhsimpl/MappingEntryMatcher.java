package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.harreplay.ReplayServerConfig.Mapping;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class MappingEntryMatcher implements EntryMatcher {

    private static final Logger log = LoggerFactory.getLogger(MappingEntryMatcher.class);

    private final ImmutableList<Mapping> mappings;
    private final Path fileResolutionRoot;

    public MappingEntryMatcher(Iterable<Mapping> mappings, Path fileResolutionRoot) {
        this.mappings = ImmutableList.copyOf(mappings);
        this.fileResolutionRoot = requireNonNull(fileResolutionRoot);
    }

    @Nullable
    @Override
    public HttpRespondable findTopEntry(ParsedRequest request) {
        String urlStr = request.url.toString();
        for (Mapping mapping : mappings) {
            if (mapping.match.evaluateUrlMatch(urlStr)) {
                try {
                    return buildRespondable(mapping, request);
                } catch (IOException e) {
                    log.info("failed to build response from " + mapping.path, e);
                }
            }
        }
        return null;
    }

    private static final int SC_OK = 200, SC_NOT_FOUND = 404;

    protected HttpRespondable buildRespondable(Mapping mapping, ParsedRequest request) throws IOException {
        int status = SC_OK;
        File file = mapping.path.resolveFile(fileResolutionRoot, mapping.match, request.url.toString());
        byte body[];
        MediaType contentType;
        if (!file.isFile()) {
            status = SC_NOT_FOUND;
            body = new byte[0];
            contentType = MediaType.PLAIN_TEXT_UTF_8;
            log.info("not found: {}", file);
        } else {
            contentType = divineContentType(file);
            body = java.nio.file.Files.readAllBytes(file.toPath());
        }
        Multimap<String, String> headers = constructHeaders(file, contentType);
        return HttpRespondable.inMemory(status, headers, contentType, body);
    }

    protected Multimap<String, String> constructHeaders(File file, MediaType contentType) {
        Multimap<String, String> headers = ArrayListMultimap.create();
        headers.put(HttpHeaders.CONTENT_TYPE, contentType.toString());
        readFileAttributes(file).forEach(headers::put);
        return headers;
    }

    private Map<String, String> readFileAttributes(File file) {
        Map<String, String> attrMap = new HashMap<>();
        // TODO use java.nio.file.Files.readAttributes to be more precise when populating attributes map
//        if (file.isFile()) {
//            try {
//                PosixFileAttributes attr = java.nio.file.Files.readAttributes(file.toPath(), PosixFileAttributes.class);
//                attr.size()
//            } catch (IOException e) {
//                log.info("failed to read file attributes", e);
//            }
//        }
        long len = file.length();
        if (len >= 0) {
            attrMap.put(HttpHeaders.CONTENT_LENGTH, String.valueOf(len));
        }
        return attrMap;
    }

    protected MediaType divineContentType(File file) {
        try {
            String mimeType = java.nio.file.Files.probeContentType(file.toPath());
            if (mimeType != null) {
                try {
                    return MediaType.parse(mimeType);
                } catch (IllegalArgumentException e) {
                    log.info("failed to parse mime type", e);
                }
            }
        } catch (IOException e) {
            log.info("failed to divine content type of " + file + " due to " + e.toString());
        }
        return MediaType.OCTET_STREAM;
    }
}
