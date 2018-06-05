package io.github.mike10004.vhs;

import java.io.IOException;
import java.util.List;

/**
 * Interface for a factory that creates an entry matcher from a list of HAR entries and an entry parser.
 */
public interface EntryMatcherFactory<S> {

    /**
     * Creates the entry matcher
     * @param <E> HAR entry type
     * @param harEntries list of har entries
     * @param requestParser request parser
     * @return an entry matcher instance
     * @throws IOException on I/O error
     */
    <E> EntryMatcher<S> createEntryMatcher(List<E> harEntries, EntryParser<E> requestParser) throws IOException;

}
