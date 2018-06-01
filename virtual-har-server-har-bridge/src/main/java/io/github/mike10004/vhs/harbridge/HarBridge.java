package io.github.mike10004.vhs.harbridge;

import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Interface that defines the methods needed to support a HAR library.
 * Implementations of this class should avoid throwing exceptions, because
 * it's likely this will be used to decide how to respond to a request,
 * and an exception will result in a default value like 'not found' that
 * probably doesn't represent what you want to be returned if the content of
 * a HAR is present but malformed in some way.
 * @param <E> HAR entry class
 */
public interface HarBridge<E> {

    /**
     * Content type value substituted when no actual content-type value is contained
     * in the HAR content object.
     */
    static MediaType getContentTypeDefaultValue() {
        return Hars.CONTENT_TYPE_DEFAULT_VALUE;
    }


    /**
     * Gets the request method as a string.
     * @param entry the HAR entry
     * @return the request method
     */
    String getRequestMethod(E entry);

    /**
     * Gets the URL the request was sent to, as a string.
     * @param entry the HAR entry
     * @return the request URL
     */
    String getRequestUrl(E entry);

    /**
     * Streams the request headers.
     * @param entry the HAR entry
     * @return a stream of the request headers
     */
    Stream<Map.Entry<String, String>> getRequestHeaders(E entry);

    ByteSource getRequestPostData(E entry) throws IOException;

    /**
     * Gets the HTTP response status code.
     * In some degenerate cases, such as a HAR collected from a user agent where
     * requests were blocked by the client (as by an ad blocker), the response
     * status may not have been set and might be a value like -1 or 0.
     * @param entry the HAR entry
     * @return the response status
     */
    int getResponseStatus(E entry);

    /**
     * Gets an object representing the HAR response data.
     * @param request the request
     * @param entry the HAR entry
     * @param encodingStrategy encoding strategy
     * @return the response data
     * @throws IOException on I/O error
     */
    HarResponseData getResponseData(ParsedRequest request, E entry, HarResponseEncoding encodingStrategy) throws IOException;

}
