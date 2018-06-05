package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.AtomicLongMap;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class StatefulHeuristicEntryMatcher extends HeuristicEntryMatcher<ReplaySessionState> {

    private static final int SEQUENCE_MATCH_BOOST = BasicHeuristic.DEFAULT_INCREMENT;

    private final ImmutableMap<ParsedEntry, Integer> entrySequencePositions;

    public StatefulHeuristicEntryMatcher(Heuristic heuristic, int thresholdExclusive, Collection<ParsedEntry> entries) {
        super(heuristic, thresholdExclusive, entries);
        this.entrySequencePositions = findSequencePositions(entries);
    }

    private static ImmutableMap<ParsedEntry, Integer> findSequencePositions(Iterable<ParsedEntry> entries) {
        ImmutableMap.Builder<ParsedEntry, Integer> b = ImmutableMap.builder();
        AtomicLongMap<Pair<HttpMethod, URI>> counter = AtomicLongMap.create();
        for (ParsedEntry entry : entries) {
            int sequencePosition = Ints.saturatedCast(counter.getAndIncrement(ImmutablePair.of(entry.request.method, entry.request.url)));
            b.put(entry, sequencePosition);
        }
        return b.build();
    }

    @Override
    protected Function<ParsedEntry, RatedEntry> createEntryToRatingFunction(ParsedRequest request, ReplaySessionState state) {
        return entry -> {
            int rating = heuristic.rate(entry.request, request);
            int boost = 0;
            if (rating > 0) {
                int entrySequencePosition = entrySequencePositions.get(entry);
                int requestSequencePosition = state.query(request) - 1; // minus one because the request was just registered, but we want what it was prior to that
                boost = entrySequencePosition == requestSequencePosition ? SEQUENCE_MATCH_BOOST : 0;
            }
            return new RatedEntry(entry, rating + boost);
        };
    }

    public static EntryMatcherFactory<ReplaySessionState> factory(Heuristic heuristic, int thresholdExclusive) {
        return new MyFactory(heuristic, thresholdExclusive);
    }

    protected static class MyFactory extends Factory<ReplaySessionState> {

        private MyFactory(Heuristic heuristic, int thresholdExclusive) {
            super(heuristic, thresholdExclusive);
        }

        @Override
        public <E> EntryMatcher<ReplaySessionState> createEntryMatcher(List<E> entries, EntryParser<E> requestParser) throws IOException {
            List<ParsedEntry> parsedEntries = parseEntries(entries, requestParser);
            return new StatefulHeuristicEntryMatcher(heuristic, thresholdExclusive, parsedEntries);
        }
    }
}
