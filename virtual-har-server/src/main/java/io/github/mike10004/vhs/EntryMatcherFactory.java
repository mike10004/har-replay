package io.github.mike10004.vhs;

import java.io.IOException;
import java.util.List;

/**
 * Interface for a factory that creates an entry matcher from a list of HAR entries and an entry parser.
 */
public interface EntryMatcherFactory {

    /**
     * Creates the entry matcher
     * @param harEntries list of har entries
     * @param requestParser request parser
     * @param <E> HAR entry type
     * @return an entry matcher instance
     * @throws IOException on I/O error
     */
    <E> EntryMatcher createEntryMatcher(List<E> harEntries, EntryParser<E> requestParser) throws IOException;

}
