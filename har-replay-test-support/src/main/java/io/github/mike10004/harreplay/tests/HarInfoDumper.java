package io.github.mike10004.harreplay.tests;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Table;
import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import org.apache.commons.lang3.StringUtils;

import java.io.PrintStream;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings({"Duplicates", "unused"})
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
        private final String indent;

        public SummaryDumper() {
            indent = Strings.repeat(" ", urlIndent);
        }

        private static String parseDomain(HarRequest request) {
            String url = request.getUrl();
            return URI.create(url).getHost();
        }

        @Override
        public void dump(List<HarEntry> harEntries, PrintStream out) {
            Multimap<String, String> summariesByDomain = ArrayListMultimap.create();
            harEntries.stream()
                    .filter(INTERESTING_REQUEST_PREDICATE)
                    .filter(NONEMPTY_RESPONSE_PREDICATE)
                    .forEach(entry -> {
                        HarRequest request = entry.getRequest();
                        HarResponse response = entry.getResponse();
                        String domain = parseDomain(request);
                        String url = request.getUrl();
                        String method = request.getMethod().name();
                        Long bodySize = response.getBodySize();
                        Integer status = response.getStatus();
                        String statusText = response.getStatusText();
                        String contentType = null;
                        HarContent content = response.getContent();
                        if (content != null) {
                            contentType = content.getMimeType();
                        }
                        String summary = String.format("%s %s -> %s %s %s %s", method, url, status, statusText, Strings.nullToEmpty(contentType), bodySize == null ? "" : bodySize);
                        summariesByDomain.put(domain, summary);
                    });
            Function<String, Integer> getNumUrls = key -> summariesByDomain.get(key).size();
            Ordering<String> keyOrdering = Ordering.<Integer>natural().onResultOf(getNumUrls::apply).reverse();

            keyOrdering.immutableSortedCopy(summariesByDomain.keySet())
                    .stream().limit(domainLimit)
                    .forEach(domain -> {
                        String abbrDomain = StringUtils.abbreviate(domain, terminalWidth);
                        out.println(abbrDomain);
                        Collection<String> summaries = summariesByDomain.get(domain);
                        summaries.forEach(summary -> {
                            out.format("%s%s%n", indent, summary);
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
}
