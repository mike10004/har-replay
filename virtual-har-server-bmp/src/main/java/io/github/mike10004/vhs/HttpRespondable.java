package io.github.mike10004.vhs;

import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Interface that defines methods used to write an HTTP response to a socket.
 */
public interface HttpRespondable {

    /**
     * Gets the HTTP status code of the response.
     * @return the status code
     */
    int getStatus();

    /**
     * Gets a stream of the response headers.
     * @return response header stream
     */
    Stream<? extends Entry<String, String>> streamHeaders();

    /**
     * Writes the response body to an output stream.
     * @param out an output stream
     * @return the content type (value for Content-Type header)
     * @throws IOException if thrown by a stream-writing method
     */
    MediaType writeBody(OutputStream out) throws IOException;

    /**
     * Gets the content type if it is available.
     * @return the content type
     */
    @Nullable
    MediaType previewContentType();

    /**
     * Creates an instance whose content is held in memory.
     * @param status HTTP status code
     * @param headers response headers
     * @param contentType content-type header value
     * @param body response body bytes
     * @return the new instance
     */
    static HttpRespondable inMemory(int status, Multimap<String, String> headers, MediaType contentType, byte[] body) {
        return inMemory(status, headers, contentType, ByteSource.wrap(body));
    }

    static HttpRespondable inMemory(int status, Multimap<String, String> headers, MediaType contentType, ByteSource body) {
        requireNonNull(headers, "headers");
        requireNonNull(contentType, "contentType");
        requireNonNull(body, "body");
        return ImmutableHttpRespondable.builder(status)
                .headers(headers)
                .contentType(contentType)
                .bodySource(body)
                .build();
    }

}
