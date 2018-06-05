package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableList;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class HeuristicEntryMatcher<S> implements EntryMatcher<S> {

    private static final Logger log = LoggerFactory.getLogger(HeuristicEntryMatcher.class);

    protected final ImmutableList<ParsedEntry> entries;
    protected final Heuristic heuristic;
    private final int thresholdExclusive;
    private final Predicate<RatedEntry> ratedEntryFilter;

    protected HeuristicEntryMatcher(Heuristic heuristic, int thresholdExclusive, Collection<ParsedEntry> entries) {
        this.entries = ImmutableList.copyOf(entries);
        this.thresholdExclusive = thresholdExclusive;
        this.heuristic = requireNonNull(heuristic);
        ratedEntryFilter = new RatedEntryFilter();
    }

    public static <T> EntryMatcherFactory<T> factory(Heuristic heuristic, int thresholdExclusive) {
        return new Factory<>(heuristic, thresholdExclusive);
    }

    /**
     * Interface that maps a request to a response.
     */
    protected interface HttpRespondableCreator {
        /**
         * Constructs and returns a respondable.
         * @param request the request
         * @return the response
         * @throws IOException on I/O error
         */
        HttpRespondable createRespondable(ParsedRequest request) throws IOException;
    }

    protected static class Factory<S> implements EntryMatcherFactory<S> {

        private static final Logger log = LoggerFactory.getLogger(Factory.class);

        protected final Heuristic heuristic;
        protected final int thresholdExclusive;

        protected Factory(Heuristic heuristic, int thresholdExclusive) {
            this.thresholdExclusive = thresholdExclusive;
            this.heuristic = requireNonNull(heuristic);
        }

        protected <E> List<ParsedEntry> parseEntries(List<E> entries, EntryParser<E> requestParser) throws IOException {
            List<ParsedEntry> parsedEntries = new ArrayList<>(entries.size());
            for (E entry : entries) {
                ParsedRequest request = requestParser.parseRequest(entry);
                HttpRespondableCreator respondableCreator = new EntryRespondableCreator<>(entry, requestParser);
                ParsedEntry parsedEntry = new ParsedEntry(request, respondableCreator);
                parsedEntries.add(parsedEntry);
            }
            return parsedEntries;
        }


        @Override
        public <E> EntryMatcher<S> createEntryMatcher(List<E> entries, EntryParser<E> requestParser) throws IOException {
            log.trace("constructing heuristic from {} har entries", entries.size());
            List<ParsedEntry> parsedEntries = parseEntries(entries, requestParser);
            return new HeuristicEntryMatcher<>(heuristic, thresholdExclusive, parsedEntries);
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

    private static final Comparator<RatedEntry> RATED_ENTRY_COMPARATOR = new Comparator<RatedEntry>() {
        @Override
        public int compare(RatedEntry o1, RatedEntry o2) {
            return o1.rating - o2.rating;
        }
    };

    private Comparator<RatedEntry> getRatedEntryComparator() {
        return RATED_ENTRY_COMPARATOR;
    }

    protected static class RatedEntry {

        public final ParsedEntry entry;
        public final int rating;

        public RatedEntry(ParsedEntry entry, int rating) {
            this.entry = requireNonNull(entry);
            this.rating = rating;
        }

    }

    private class DefaultEntryToRatingFunction implements java.util.function.Function<ParsedEntry, RatedEntry> {

        private final ParsedRequest request;

        private DefaultEntryToRatingFunction(ParsedRequest request) {
            this.request = requireNonNull(request);
        }

        @Override
        public RatedEntry apply(ParsedEntry entry) {
            int rating = heuristic.rate(requireNonNull(entry).request, request);
            return new RatedEntry(entry, rating);
        }
    }

    protected Predicate<? super RatedEntry> getRatedEntryFilter(S state) {
        return ratedEntryFilter;
    }

    private class RatedEntryFilter implements Predicate<RatedEntry> {

        @Override
        public boolean test(RatedEntry ratedEntry) {
            return ratedEntry.rating > thresholdExclusive;
        }
    }

    protected java.util.function.Function<ParsedEntry, RatedEntry> createEntryToRatingFunction(ParsedRequest request, S state) {
        return new DefaultEntryToRatingFunction(request);
    }

    @Nullable
    @Override
    public HttpRespondable findTopEntry(S state, ParsedRequest request) {
        List<RatedEntry> ratedEntryList = entries.stream()
                .map(createEntryToRatingFunction(request, state))
                .collect(Collectors.toList());
        Optional<RatedEntry> topRatedEntry = ratedEntryList.stream()
                .filter(getRatedEntryFilter(state))
                .max(getRatedEntryComparator());
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
    protected static class ParsedEntry {

        public final ParsedRequest request;

        public final HttpRespondableCreator responseCreator;

        public ParsedEntry(ParsedRequest request, HttpRespondableCreator responseCreator) {
            this.responseCreator = requireNonNull(responseCreator);
            this.request = requireNonNull(request);
        }

    }
}
