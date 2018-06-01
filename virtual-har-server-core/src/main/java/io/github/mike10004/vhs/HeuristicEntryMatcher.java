package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableList;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class HeuristicEntryMatcher implements EntryMatcher {

    private static final Logger log = LoggerFactory.getLogger(HeuristicEntryMatcher.class);

    private final ImmutableList<ParsedEntry> entries;
    private final Heuristic heuristic;
    private final int thresholdExclusive;
    private final Predicate<RatedEntry> ratedEntryFilter;

    HeuristicEntryMatcher(Heuristic heuristic, int thresholdExclusive, Collection<ParsedEntry> entries) {
        this.entries = ImmutableList.copyOf(entries);
        this.thresholdExclusive = thresholdExclusive;
        this.heuristic = requireNonNull(heuristic);
        ratedEntryFilter = new RatedEntryFilter();
    }

    public static EntryMatcherFactory factory(Heuristic heuristic, int thresholdExclusive) {
        return new Factory(heuristic, thresholdExclusive);
    }

    /**
     * Interface that maps a request to a response.
     */
    interface HttpRespondableCreator {
        /**
         * Constructs and returns a respondable.
         * @param request the request
         * @return the response
         * @throws IOException on I/O error
         */
        HttpRespondable createRespondable(ParsedRequest request) throws IOException;
    }

    private static class Factory implements EntryMatcherFactory {

        private static final Logger log = LoggerFactory.getLogger(Factory.class);

        private final Heuristic heuristic;
        private final int thresholdExclusive;

        private Factory(Heuristic heuristic, int thresholdExclusive) {
            this.thresholdExclusive = thresholdExclusive;
            this.heuristic = heuristic;
        }

        @Override
        public <E> EntryMatcher createEntryMatcher(List<E> entries, EntryParser<E> requestParser) throws IOException {
            log.trace("constructing heuristic from {} har entries", entries.size());
            List<ParsedEntry> parsedEntries = new ArrayList<>(entries.size());
            for (E entry : entries) {
                ParsedRequest request = requestParser.parseRequest(entry);
                HttpRespondableCreator respondableCreator = new EntryRespondableCreator<>(entry, requestParser);
                ParsedEntry parsedEntry = new ParsedEntry(request, respondableCreator);
                parsedEntries.add(parsedEntry);
            }
            return new HeuristicEntryMatcher(heuristic, thresholdExclusive, parsedEntries);
        }
    }

    private static class EntryRespondableCreator<E> implements HttpRespondableCreator {

        private final E entry;
        private final EntryParser<E> requestParser;

        private EntryRespondableCreator(E entry, EntryParser<E> requestParser) {
            this.requestParser = requestParser;
            this.entry = entry;
        }

        @Override
        public HttpRespondable createRespondable(ParsedRequest newRequest) throws IOException {
            return requestParser.parseResponse(newRequest, entry);
        }
    }

    private static class RatedEntry implements Comparable<RatedEntry> {
        public final ParsedEntry entry;
        public final int rating;

        private RatedEntry(ParsedEntry entry, int rating) {
            this.entry = entry;
            this.rating = rating;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public int compareTo(RatedEntry o) {
            return this.rating - o.rating;
        }
    }

    private class EntryToRatingFunction implements java.util.function.Function<ParsedEntry, RatedEntry> {

        private final ParsedRequest request;

        private EntryToRatingFunction(ParsedRequest request) {
            this.request = request;
        }

        @Override
        public RatedEntry apply(ParsedEntry entry) {
            int rating = heuristic.rate(requireNonNull(entry).request, request);
            return new RatedEntry(entry, rating);
        }
    }

    private class RatedEntryFilter implements Predicate<RatedEntry> {

        @Override
        public boolean test(RatedEntry ratedEntry) {
            return ratedEntry.rating > thresholdExclusive;
        }
    }

    @Override
    @Nullable
    public HttpRespondable findTopEntry(ParsedRequest request) {
        List<RatedEntry> ratedEntryList = entries.stream()
                .map(new EntryToRatingFunction(request))
                .collect(Collectors.toList());
        Optional<RatedEntry> topRatedEntry = ratedEntryList.stream()
                .filter(ratedEntryFilter)
                .max(RatedEntry::compareTo);
        if (topRatedEntry.isPresent()) {
            try {
                return topRatedEntry.get().entry.responseCreator.createRespondable(request);
            } catch (IOException e) {
                log.warn("could not create response for top-rated entry", e);
            }
        }
        return null;
    }


    /**
     * Class that represents a HAR entry with a saved request and a method to produce
     * a response.
     */
    static class ParsedEntry {

        public final ParsedRequest request;

        public final HttpRespondableCreator responseCreator;

        public ParsedEntry(ParsedRequest request, HttpRespondableCreator responseCreator) {
            this.responseCreator = requireNonNull(responseCreator);
            this.request = requireNonNull(request);
        }

    }
}
