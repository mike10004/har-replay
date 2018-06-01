package io.github.mike10004.vhs;

import io.github.mike10004.vhs.harbridge.ParsedRequest;

import javax.annotation.Nullable;

/**
 * Interface that defines a method to determine how to respond to
 * an HTTP request.
 */
public interface EntryMatcher {

    /**
     * Finds the best response for a given HTTP request.
     * @param request the request
     * @return the response that matches best, or null if none matches well enough
     */
    @Nullable
    HttpRespondable findTopEntry(ParsedRequest request);

}
