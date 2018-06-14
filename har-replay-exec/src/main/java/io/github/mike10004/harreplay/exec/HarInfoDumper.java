package io.github.mike10004.harreplay.exec;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.io.Files;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.gson.GsonBuilder;
import com.opencsv.CSVWriter;
import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarPostData;
import de.sstoehr.harreader.model.HarPostDataParam;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import de.sstoehr.harreader.model.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

interface HarInfoDumper {
    void dump(List<HarEntry> harEntries, PrintStream out);

    static HarInfoDumper silent() {
        return (harEntries, out) -> out.flush();
    }

    class TerseDumper extends AbstractDumper {
        @Override
        public void dump(List<HarEntry> harEntries, PrintStream out) {
            HarRequest req = harEntries.stream()
                    .filter(INTERESTING_REQUEST_PREDICATE)
                    .map(HarEntry::getRequest)
                    .findFirst().orElse(null);
            if (req != null) {
                out.format("%s is first request in HAR file%n", req.getUrl());
            } else {
                System.err.format("no non-favicon requests found in HAR file%n");
            }
        }
    }

    class SummaryDumper extends AbstractDumper {

        private final int domainLimit = 10;
        private final int urlPerDomainLimit = 3;
        private final int terminalWidth = 80;
        private final int urlIndent = 4;

        private static String parseDomain(HarRequest request) {
            String url = request.getUrl();
            return URI.create(url).getHost();
        }

        @Override
        public void dump(List<HarEntry> harEntries, PrintStream out) {
            Multimap<String, String> urlsByDomain = ArrayListMultimap.create();
            harEntries.stream()
                    .filter(INTERESTING_REQUEST_PREDICATE)
                    .filter(NONEMPTY_RESPONSE_PREDICATE)
                    .map(HarEntry::getRequest)
                    .forEach(request -> {
                        String domain = parseDomain(request);
                        String url = request.getUrl();
                        urlsByDomain.put(domain, url);
                    });
            Function<String, Integer> getNumUrls = key -> urlsByDomain.get(key).size();
            Ordering<String> keyOrdering = Ordering.<Integer>natural().onResultOf(getNumUrls::apply).reverse();
            keyOrdering.immutableSortedCopy(urlsByDomain.keySet())
                    .stream().limit(domainLimit)
                    .forEach(domain -> {
                        String abbrDomain = StringUtils.abbreviate(domain, terminalWidth);
                        out.println(abbrDomain);
                        List<String> abbrUrls = urlsByDomain.get(domain).stream()
                                .limit(urlPerDomainLimit)
                                .map(url -> StringUtils.abbreviate(url, terminalWidth - urlIndent))
                                .collect(Collectors.toList());
                        String indent = Strings.repeat(" ", urlIndent);
                        abbrUrls.forEach(url -> {
                            out.format("%s%s%n", indent, url);
                        });
                    });
        }
    }

    abstract class AbstractDumper implements HarInfoDumper {
        protected static final Predicate<HarEntry> INTERESTING_REQUEST_PREDICATE =  new Predicate<HarEntry>() {
            @Override
            public boolean test(HarEntry harEntry) {
                HarRequest request = harEntry.getRequest();
                return request != null && request.getUrl() != null && !request.getUrl().endsWith("favicon.ico");
            }
        };
        protected static final Predicate<HarEntry> NONEMPTY_RESPONSE_PREDICATE = new Predicate<HarEntry>() {
            @Override
            public boolean test(HarEntry harEntry) {
                HarResponse response = harEntry.getResponse();
                if (response != null && response.getStatus() > 0) {
                    Long bodySize = response.getBodySize();
                    if (bodySize != null) {
                        return bodySize.longValue() > 0;
                    }
                }
                return false;
            }
        };
    }

    @SuppressWarnings("Duplicates") // there is a class in test-support that does exactly this
    class VerboseDumper extends AbstractDumper {
        @Override
        public void dump(List<HarEntry> harEntries, PrintStream out) {
            harEntries.stream()
                    .filter(INTERESTING_REQUEST_PREDICATE)
                    .filter(NONEMPTY_RESPONSE_PREDICATE)
                    .forEach(entry -> {
                HarRequest request = entry.getRequest();
                HarResponse response = entry.getResponse();
                int status = response.getStatus();
                String method = request.getMethod().name();
                Long bodySize = response.getBodySize();
                String url = request.getUrl();
                out.format("%3d %6s %5s %s%n", status, method, bodySize, url);
            });
        }
    }

    class CsvDumper extends AbstractDumper {

        private final RowTransform rowTransform;
        private final Charset charset;

        public CsvDumper() {
            this(new DefaultRowTransform(), StandardCharsets.UTF_8);
        }

        public static HarInfoDumper getDefaultInstance() {
            return new CsvDumper();
        }

        public static HarInfoDumper makeContentWritingInstance(@Nullable File destinationDir) {
            if (destinationDir == null) {
                return getDefaultInstance();
            }
            return new CsvDumper(new ContentDumpingRowTransform(destinationDir), StandardCharsets.UTF_8);
        }

        public CsvDumper(RowTransform rowTransform, Charset charset) {
            this.rowTransform = rowTransform;
            this.charset = charset;
        }

        @Override
        public void dump(List<HarEntry> harEntries, PrintStream out) {
            boolean columnNamesRowPrinted = false;
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, charset));
            try {
                CSVWriter csv = new CSVWriter(writer);
                for (int i = 0; i < harEntries.size(); i++) {
                    HarEntry entry = harEntries.get(i);
                    if (!columnNamesRowPrinted) {
                        String[] columnNames = rowTransform.getColumnNames();
                        if (columnNames != null) {
                            csv.writeNext(columnNames);
                            csv.flushQuietly();
                        }
                        columnNamesRowPrinted = true;
                    }
                    String[] row = rowTransform.apply(entry, i);
                    csv.writeNext(row);
                    csv.flushQuietly();
                }
            } finally {
                writer.flush();
            }
        }

        private static String getFirstHeaderValue(List<HarHeader> headers, String headerName) {
            if (headers != null) {
                return headers.stream().filter(header -> {
                    return headerName.equalsIgnoreCase(header.getName());
                }).map(HarHeader::getValue)
                        .findFirst().orElse(null);
            }
            return null;
        }

        private interface RowTransform {
            String[] getColumnNames();
            default String[] apply(HarEntry harEntry, int entryIndex) {
                return toStringArray(transform(harEntry, entryIndex));
            }
            Object[] transform(HarEntry harEntry, int entryIndex);
            default String[] toStringArray(Object...objects) {
                return Stream.of(objects)
                        .map(x -> x == null ? "" : x.toString())
                        .toArray(String[]::new);
            }

        }

        private static class ContentDumpingRowTransform extends DefaultRowTransform {

            private static final String[] ADDITIONAL_HEADERS = {
                    "requestContentType", "responseContent", "requestContent"
            };

            private final File destinationDir;
            private final Path relativeRoot;

            private ContentDumpingRowTransform(File destinationDir) {
                this.destinationDir = destinationDir.getAbsoluteFile();
                relativeRoot = this.destinationDir.toPath();
            }

            protected File constructPathname(int entryIndex, String infix, String contentType) {
                String filename = String.format("%d-%s", entryIndex, infix);
                try {
                    MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
                    MimeType type = allTypes.forName(contentType);
                    String ext = type.getExtension();
                    if (!Strings.isNullOrEmpty(ext)) {
                        filename = String.format("%s%s", filename, ext);
                    }
                } catch (MimeTypeException | RuntimeException ignore) {}
                return new File(destinationDir, filename);
            }

            @Override
            public String[] getColumnNames() {
                return Stream.concat(Stream.of(super.getColumnNames()), Stream.of(ADDITIONAL_HEADERS))
                        .toArray(String[]::new);
            }

            private byte[] toBytes(String text, String encoding) {
                byte[] bytes;
                if ("base64".equalsIgnoreCase(encoding)) {
                    bytes = Base64.getDecoder().decode(text);
                } else {
                    bytes = text.getBytes(StandardCharsets.UTF_8);
                }
                return bytes;
            }

            @Override
            public Object[] transform(HarEntry harEntry, int entryIndex) {
                Object[] start = super.transform(harEntry, entryIndex);
                String requestContentType = null;
                Path responseContentPath = null, requestContentPath = null;
                HarResponse response = harEntry.getResponse();
                if (response != null) {
                    HarContent content = response.getContent();
                    if (content != null) {
                        String text = content.getText();
                        if (text != null) {
                            byte[] bytes = toBytes(text, content.getEncoding());
                            File responseContentFile = constructPathname(entryIndex, "response", (String) start[INDEX_CONTENT_TYPE]);
                            responseContentPath = writeAndReturnPath(bytes, responseContentFile);
                        }
                    }
                }
                HarRequest request = harEntry.getRequest();
                if (request != null) {
                    if (mightContainBody(request.getMethod())) {
                        HarPostData postData = request.getPostData();
                        if (postData != null) {
                            List<HarPostDataParam> params = postData.getParams();
                            File requestContentFile = null;
                            byte[] bytes = null;
                            if (params != null && !params.isEmpty()) {
                                String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(params);
                                bytes = json.getBytes(StandardCharsets.UTF_8);
                                requestContentFile = constructPathname(entryIndex, "request", MediaType.JSON_UTF_8.toString());
                            } else {
                                String text = postData.getText();
                                if (text != null) {
                                    bytes = toBytes(text, null);
                                    requestContentType = postData.getMimeType();
                                    if (requestContentType == null) {
                                        requestContentType = getFirstHeaderValue(request.getHeaders(), HttpHeaders.CONTENT_TYPE);
                                    }
                                    requestContentFile = constructPathname(entryIndex, "request", postData.getMimeType());
                                }
                            }
                            if (bytes != null && requestContentFile != null) {
                                requestContentPath = writeAndReturnPath(bytes, requestContentFile);
                            }
                        }
                    }
                }
                return Stream.concat(Stream.of(start), Stream.of(requestContentType, responseContentPath, requestContentPath)).toArray();
            }

            @Nullable
            private Path writeAndReturnPath(byte[] bytes, File file) {
                try {
                    Files.createParentDirs(file);
                    Files.write(bytes, file);
                    Path path = file.getAbsoluteFile().toPath();
                    try {
                        return relativeRoot.relativize(path);
                    } catch (IllegalArgumentException e) {
                        LoggerFactory.getLogger(getClass()).error("failed to relativize {} against {}", path, relativeRoot);
                        throw new IOException(e);
                    }
                } catch (IOException e) {
                    LoggerFactory.getLogger(getClass()).warn("failed to write file", e);
                }
                return null;
            }

            private static boolean mightContainBody(@Nullable HttpMethod method) {
                if (method != null) {
                    switch (method) {
                        case POST:
                        case PUT:
                            return true;
                    }
                }
                return false;
            }
        }

        private static class DefaultRowTransform implements RowTransform {

            protected static final int INDEX_CONTENT_TYPE = 3;

            private static final ImmutableList<String> DEFAULT_COLUMN_NAMES = ImmutableList.copyOf(new String[]{
                    "status",
                    "method",
                    "url",
                    "contentType",
                    "contentSize",
                    "redirect",
            });

            @Override
            public String[] getColumnNames() {
                return DEFAULT_COLUMN_NAMES.toArray(new String[0]);
            }

            @Override
            public Object[] transform(HarEntry harEntry, int entryIndex) {
                String url = null;
                Integer statusCode = null;
                HttpMethod method = null;
                Long contentSize = null;
                String contentType = null;
                String redirectLocation = null;
                HarResponse response = harEntry.getResponse();
                if (response != null) {
                    statusCode = response.getStatus();
                    HarContent content = response.getContent();
                    if (content != null) {
                        contentType = content.getMimeType();
                        contentSize = content.getSize();
                    }
                    List<HarHeader> headers = response.getHeaders();
                    if (contentType == null) {
                        contentType = getFirstHeaderValue(headers, HttpHeaders.CONTENT_TYPE);
                    }
                    redirectLocation = getFirstHeaderValue(headers, HttpHeaders.LOCATION);
                }
                HarRequest request = harEntry.getRequest();
                if (request != null) {
                    url = request.getUrl();
                    method = request.getMethod();
                }
                return new Object[]{statusCode, method, url, contentType, contentSize, redirectLocation};
            }

        }
    }
}
