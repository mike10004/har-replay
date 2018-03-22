package io.github.mike10004.harreplay.nodeimpl;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;

public class InteractionLogParser {

    private final CSVParser csvParser;

    public InteractionLogParser() {
        csvParser = new CSVParserBuilder()
                .withQuoteChar('\'')
                .withIgnoreLeadingWhiteSpace(true)
                .withSeparator(' ')
                .build();
    }

    // 200 'GET' 'http://www.example.com/' 'text/html;charset=utf8' 1270 'matchedentry' '16aae9a53b5fe221e3dc7c212d977b80'
    public Interaction parseInteraction(String line) {
        try {
            String[] elements = csvParser.parseLine(line);
            return constructInteraction(elements);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Interaction constructInteraction(String[] tokens) {
        Iterator<String> it = Arrays.asList(tokens).iterator();
        int status = assignNext(it, Integer::parseInt, -2);
        String method = assignNext(it, null);
        String url = assignNext(it, null);
        String contentType = assignNext(it, null);
        long contentLength = assignNext(it, Long::parseLong, -1L);
        String lengthType = assignNext(it, null);
        String origin = assignNext(it, null);
        return new Interaction(status, method, url, contentType, contentLength, lengthType, origin);
    }

    @SuppressWarnings("SameParameterValue")
    private static String assignNext(Iterator<String> iterator, String defaultValue) {
        return assignNext(iterator, Function.identity(), defaultValue);
    }

    private static <T> T assignNext(Iterator<String> iterator, Function<? super String, T> transform, T defaultValue) {
        if (iterator.hasNext()) {
            String token = iterator.next();
            try {
                return transform.apply(token);
            } catch (IllegalArgumentException e) {
                LoggerFactory.getLogger(InteractionLogParser.class).debug("failed to parse token {} due to {}", StringUtils.abbreviate(token, 64), e.toString());
            }
        }
        return defaultValue;
    }

    public static class Interaction {
        public final String method;
        public final String url;
        public final int status;
        public final long contentLength;
        public final String contentType;
        public final String origin;
        public final String hash;

        public Interaction(int status, String method, String url, String contentType, long contentLength, String origin, String hash) {
            this.method = method;
            this.url = url;
            this.status = status;
            this.contentLength = contentLength;
            this.contentType = contentType;
            this.hash = hash;
            this.origin = origin;
        }

        @Override
        public String toString() {
            return "Interaction{" +
                    "method='" + method + '\'' +
                    ", url='" + url + '\'' +
                    ", status=" + status +
                    ", contentLength=" + contentLength +
                    ", contentType='" + contentType + '\'' +
                    ", hash='" + hash + '\'' +
                    ", origin='" + origin + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Interaction that = (Interaction) o;
            return status == that.status &&
                    contentLength == that.contentLength &&
                    Objects.equals(method, that.method) &&
                    Objects.equals(url, that.url) &&
                    Objects.equals(contentType, that.contentType) &&
                    Objects.equals(hash, that.hash) &&
                    Objects.equals(origin, that.origin);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, url, status, contentLength, contentType, hash, origin);
        }
    }


}
