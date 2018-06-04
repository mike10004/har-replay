package io.github.mike10004.vhs.bmp;

import com.google.common.io.CharSource;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.harbridge.ParsedRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Interface defining methods to interpret HTTP message objects.
 * @param <RQ> request type
 * @param <RS> response type
 */
public interface HttpAssistant<RQ, RS> {

    ParsedRequest parseRequest(RQ incomingRequest) throws IOException;

    RS transformRespondable(RQ incomingRequest, HttpRespondable respondable) throws IOException;

    RS constructResponse(RQ incomingRequest, ImmutableHttpResponse response);

    static ImmutableHttpResponse standardServerErrorResponse() {
        return ImmutableHttpResponse.builder(500)
                .content(MediaType.PLAIN_TEXT_UTF_8, CharSource.wrap("500 Internal Server Error").asByteSource(StandardCharsets.UTF_8))
                .build();
    }
}
