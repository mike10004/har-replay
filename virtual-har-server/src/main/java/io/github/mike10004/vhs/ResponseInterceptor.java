package io.github.mike10004.vhs;

import io.github.mike10004.vhs.harbridge.ParsedRequest;

/**
 * Interface that defines a method to intercept a request.
 */
public interface ResponseInterceptor {

    HttpRespondable intercept(ParsedRequest request, HttpRespondable respondable);

}
