package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.ResponseInterceptor;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of a response manufacturer that manufactures responses
 * based on the content of a HAR file.
 */
public class HarReplayManufacturer implements BmpResponseManufacturer {

    private static final Logger log = LoggerFactory.getLogger(HarReplayManufacturer.class);
    private static final Charset OUTGOING_CHARSET = StandardCharsets.UTF_8;

    private final EntryMatcher entryMatcher;
    private final ImmutableList<ResponseInterceptor> responseInterceptors;
    private final HttpAssistant<RequestCapture, HttpResponse> bmpAssistant;

    /**
     * Constructs an instance.
     * @param entryMatcher the entry matcher
     * @param responseInterceptors a list of response interceptors
     */
    public HarReplayManufacturer(EntryMatcher entryMatcher, Iterable<ResponseInterceptor> responseInterceptors) {
        this(entryMatcher, responseInterceptors, new BmpHttpAssistant());
    }

    protected HarReplayManufacturer(EntryMatcher entryMatcher, Iterable<ResponseInterceptor> responseInterceptors, HttpAssistant<RequestCapture, HttpResponse> bmpAssistant) {
        this.entryMatcher = requireNonNull(entryMatcher);
        this.responseInterceptors = ImmutableList.copyOf(responseInterceptors);
        this.bmpAssistant = requireNonNull(bmpAssistant);
    }

    @Override
    public ResponseCapture manufacture(RequestCapture capture) {
        return manufacture(bmpAssistant, capture);
    }

    protected ImmutableHttpResponse createNotFoundResponse() {
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8.withCharset(OUTGOING_CHARSET);
        return ImmutableHttpResponse.builder(404)
                .content(contentType, CharSource.wrap("404 Not Found").asByteSource(OUTGOING_CHARSET))
                .build();
    }

    protected ImmutableHttpResponse createParsingFailedResponse() {
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8.withCharset(OUTGOING_CHARSET);
        return ImmutableHttpResponse.builder(500)
                .content(contentType, CharSource.wrap("500 Internal Server Error").asByteSource(OUTGOING_CHARSET))
                .build();
    }

    protected <Q> ResponseCapture manufacture(HttpAssistant<Q, HttpResponse> assistant, Q incoming) {
        ParsedRequest request;
        try {
            request = assistant.parseRequest(incoming);
        } catch (IOException e) {
            log.error("failed to read from incoming request", e);
            ImmutableHttpResponse outgoing = createParsingFailedResponse();
            HttpResponse netty = assistant.constructResponse(incoming, outgoing);
            return ResponseCapture.error(netty);
        }
        @Nullable HttpRespondable bestEntry = entryMatcher.findTopEntry(request);
        if (bestEntry != null) {
            for (ResponseInterceptor interceptor : responseInterceptors) {
                bestEntry = interceptor.intercept(request, bestEntry);
            }
        }
        if (bestEntry == null) {
            ImmutableHttpResponse response = createNotFoundResponse();
            return ResponseCapture.unmatched(assistant.constructResponse(incoming, response));
        } else {
            try {
                return ResponseCapture.matched(assistant.transformRespondable(incoming, bestEntry));
            } catch (IOException e) {
                log.warn("failed to construct response", e);
                ImmutableHttpResponse response = HttpAssistant.standardServerErrorResponse();
                return ResponseCapture.error(assistant.constructResponse(incoming, response));
            }
        }
    }

}
