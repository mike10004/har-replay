package io.github.mike10004.vhs;

import io.github.mike10004.vhs.harbridge.ParsedRequest;

/**
 * Interface defining a method to rate how closely a given request matches
 * the request contained in a HAR entry.
 */
public interface Heuristic {

    /**
     * Returns a rating of how closesly an incoming request matches a HAR entry request.
     * @param entryRequest the HAR entry request
     * @param request the incoming request
     * @return the rating
     */
    int rate(ParsedRequest entryRequest, ParsedRequest request);

}
