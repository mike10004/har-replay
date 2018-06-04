package io.github.mike10004.vhs;

import io.github.mike10004.vhs.harbridge.ParsedRequest;

import java.io.IOException;

/**
 * Interface that defines methods used to parse requests and responses from
 * a HAR entry.
 * @param <E> har entry type used by HAR library
 */
public interface EntryParser<E> {

    /**
     * Parses the request present in a HAR entry.
     * @param harEntry the HAR entry
     * @return the parsed request
     * @throws IOException if extraction from HAR goes awry
     */
    ParsedRequest parseRequest(E harEntry) throws IOException;

    /**
     * Parses the HTTP response present in a HAR entry.
     * @param harEntry the HAR entry
     * @param request the new client request
     * @return the parsed response
     * @throws IOException if extraction from HAR goes awry
     */
    HttpRespondable parseResponse(ParsedRequest request, E harEntry) throws IOException;

}
