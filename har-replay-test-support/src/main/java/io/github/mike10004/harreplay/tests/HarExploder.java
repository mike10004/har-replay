package io.github.mike10004.harreplay.tests;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/*
{
    "log": {
        "version" : "1.2",
        "creator" : {},
        "browser" : {},
        "pages": [],
        "entries": [],
        "comment": ""
    }
}

"entries": [
    {
        "pageref": "page_0",
        "startedDateTime": "2009-04-16T12:07:23.596Z",
        "time": 50,
        "request": {...},
        "response": {...},
        "cache": {...},
        "timings": {},
        "serverIPAddress": "10.0.0.1",
        "connection": "52492",
        "comment": ""
    }
]
 */
public class HarExploder {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.format("exactly one argument required (the output directory)");
            System.exit(1);
        }
        if (Strings.nullToEmpty(args[0]).trim().isEmpty()) {
            throw new IllegalArgumentException("illegal directory name: " + args[0]);
        }
        Path outputRoot = new File(args[0]).toPath();
        byte[] harBytes = ByteStreams.toByteArray(System.in);
        if (harBytes.length == 0) {
            System.err.format("0 bytes from standard input; should be a har file");
            System.exit(1);
        }
        CharSource harSource = ByteSource.wrap(harBytes).asCharSource(StandardCharsets.UTF_8);
        HarExploder exploder = new HarExploder();
        exploder.explode(harSource, outputRoot);
    }

    public void explode(CharSource harSource, Path outputRoot) throws IOException {
        try (Reader reader = harSource.openStream()) {
            explode(new JsonParser().parse(reader), outputRoot);
        }
    }

    protected void explode(JsonElement harObject, Path outputRoot) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        outputRoot.toFile().mkdirs();
        if (!outputRoot.toFile().isDirectory()) {
            throw new IOException("failed to create output root " + outputRoot);
        }
        Path scratchDir = java.nio.file.Files.createTempDirectory(outputRoot, ".scratch-directory");
        try {
            IntermediateRep intermediateRep = createIntermediateRep(harObject, scratchDir);
            explode(intermediateRep, outputRoot);
        } finally {
            FileUtils.deleteDirectory(scratchDir.toFile());
        }
    }

    protected IntermediateRep createIntermediateRep(JsonElement harObject, Path scratchDir) throws IOException {
        List<CachedEntry> cachedEntries = new ArrayList<>();
        if (harObject.isJsonObject()) {
            JsonObject logObject = harObject.getAsJsonObject().getAsJsonObject("log");
            if (logObject != null) {
                JsonArray entriesArray = logObject.getAsJsonArray("entries");
                for (JsonElement entry : entriesArray) {
                    if (entry.isJsonObject()) {
                        JsonObject requestObject = entry.getAsJsonObject().getAsJsonObject("request");
                        JsonObject responseObject = entry.getAsJsonObject().getAsJsonObject("response");
                        @Nullable CachedEntry cachedEntry = CachedEntry.create(requestObject, responseObject, scratchDir);
                        cachedEntries.add(cachedEntry);
                    }
                }
            }
        }
        return new IntermediateRep(cachedEntries);
    }

    protected void explode(IntermediateRep intermediateRep, Path outputRoot) throws IOException {
        Path entriesRoot = outputRoot.resolve("log").resolve("entries");
        //noinspection ResultOfMethodCallIgnored
        entriesRoot.toFile().mkdirs();
        if (!entriesRoot.toFile().isDirectory()) {
            throw new IOException("failed to create directory " + entriesRoot);
        }
        for (int i = 0; i < intermediateRep.entries.size(); i++) {
            @Nullable CachedEntry entry = intermediateRep.entries.get(i);
            if (entry != null) {
                @Nullable String responseDirName = entry.constructDirectoryName(i);
                if (responseDirName != null) {
                    Path responseDir = entriesRoot.resolve(responseDirName);
                    //noinspection ResultOfMethodCallIgnored
                    responseDir.toFile().mkdirs();
                    entry.writeResponseFilesInDirectory(responseDir);
                }
            }
        }
    }

//    protected static final CharMatcher SLASH = CharMatcher.is('/').or(CharMatcher.is('\\'));
    protected static final CharMatcher SAFE_CHARS = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z')).or(CharMatcher.inRange('0', '9')).or(CharMatcher.anyOf("_-."));
    protected static final CharMatcher UNSAFE_CHARS = SAFE_CHARS.negate();
    protected static String makeSafe(String unsafe) {
        String allSafeChars = UNSAFE_CHARS.replaceFrom(unsafe, '_');
        return StringUtils.abbreviateMiddle(allSafeChars, "_truncated_", 128);
    }

    protected static String normalizeUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (uri.getPort() > 0) {
                host = String.format("%s:%s", host, uri.getPort());
            }
            return host + (uri.getPath());
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(HarExploder.class).info("failed to normalize {}", StringUtils.abbreviate(url, 128));
            return url;
        }
    }

    protected static class CachedEntry {
        public final String method;
        public final String url;
        public final int status;
        public final String statusText;
        public final ByteSource responseContent;

        public CachedEntry(String method, String url, int status, String statusText, ByteSource responseContent) {
            this.method = requireNonNull(method);
            this.url = requireNonNull(url);
            this.status = status;
            this.statusText = Strings.nullToEmpty(statusText);
            this.responseContent = requireNonNull(responseContent);
        }


        @Nullable
        public String constructDirectoryName(int index) {
            return String.format("%d-%s-%s", index, method, makeSafe(normalizeUrl(url)));
        }

        protected String constructResponseDataFilename() {
            return String.format("%d-%s", status, makeSafe(statusText));
        }

        public void writeResponseFilesInDirectory(Path directory) throws IOException {
            File urlFile = directory.resolve("url.txt").toFile();
            Files.asCharSink(urlFile, StandardCharsets.UTF_8).write(url);
            String dataFilename = constructResponseDataFilename();
            File dataFile = directory.resolve(dataFilename).toFile();
            responseContent.copyTo(Files.asByteSink(dataFile));
        }

        @Nullable
        public static CachedEntry create(JsonObject request, @Nullable JsonObject response, Path scratchDir) throws IOException {
            JsonPrimitive urlPrimitive = request.getAsJsonPrimitive("url");
            if (urlPrimitive == null) {
                return null;
            }
            JsonPrimitive methodPrimitive = request.getAsJsonPrimitive("method");
            if (methodPrimitive == null) {
                methodPrimitive = new JsonPrimitive("XXXX");
            }
            String url = urlPrimitive.getAsString(), method = methodPrimitive.getAsString();
            int status = 0;
            String statusText = "Unknown";
            ByteSource responseContent = ByteSource.empty();
            if (response != null) {
                status = response.get("status").getAsInt();
                statusText = asStringOrNull(response.getAsJsonPrimitive("statusText"));
                responseContent = prepareContent(response.getAsJsonObject("content"), scratchDir);
            }
            return new CachedEntry(method, url, status, statusText, responseContent);
        }

        protected static ByteSource prepareContent(@Nullable JsonObject content, Path scratchDir) throws IOException {
            if (content == null) {
                return ByteSource.empty();
            }
            @Nullable String text = asStringOrNull(content.getAsJsonPrimitive("text"));
            if (text == null) {
                return ByteSource.empty();
            }
            @Nullable String encoding = asStringOrNull(content.getAsJsonPrimitive("encoding"));
            ByteSource decodedSource;
            if ("base64".equalsIgnoreCase(encoding)) {
                decodedSource = BaseEncoding.base64().decodingSource(CharSource.wrap(text));
            } else {
                decodedSource = CharSource.wrap(text).asByteSource(StandardCharsets.UTF_8);
            }
            // do this stuff if you want to cache the response content bytes on disk
//            File tempFile = File.createTempFile("response-content", ".tmp", scratchDir.toFile());
//            decodedSource.copyTo(Files.asByteSink(tempFile));
//            return Files.asByteSource(tempFile);
            return decodedSource;
        }
    }

    @Nullable
    protected static String asStringOrNull(@Nullable JsonPrimitive primitive) {
        if (primitive == null) {
            return null;
        }
        return primitive.getAsString();
    }

    protected static class IntermediateRep {
        public final List<CachedEntry> entries;

        public IntermediateRep(List<CachedEntry> entries) {
            this.entries = entries;
        }
    }

}
