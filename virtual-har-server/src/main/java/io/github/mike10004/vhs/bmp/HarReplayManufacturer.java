package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.ReplaySessionState;
import io.github.mike10004.vhs.ResponseInterceptor;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of a response manufacturer that manufactures responses
 * based on the content of a HAR file.
 */
public class HarReplayManufacturer implements BmpResponseManufacturer<ReplaySessionState> {

    private static final Logger log = LoggerFactory.getLogger(HarReplayManufacturer.class);
    private static final Charset OUTGOING_CHARSET = StandardCharsets.UTF_8;

    private final Supplier<? extends ReplaySessionState> sessionStateFactory;
    private final EntryMatcher<? super ReplaySessionState> entryMatcher;
    private final ImmutableList<ResponseInterceptor> responseInterceptors;
    private final HttpAssistant<RequestCapture, HttpResponse> bmpAssistant;

    /**
     * Constructs an instance.
     * @param entryMatcher the entry matcher
     * @param responseInterceptors a list of response interceptors
     */
    public HarReplayManufacturer(EntryMatcher<? super ReplaySessionState> entryMatcher, Iterable<ResponseInterceptor> responseInterceptors) {
        this(entryMatcher, responseInterceptors, ReplaySessionState::countingUrlMethodPairs);
    }

    public HarReplayManufacturer(EntryMatcher<? super ReplaySessionState> entryMatcher, Iterable<ResponseInterceptor> responseInterceptors, Supplier<? extends ReplaySessionState> sessionStateFactory) {
        this(entryMatcher, responseInterceptors, new BmpHttpAssistant(), sessionStateFactory);
    }

    protected HarReplayManufacturer(EntryMatcher<? super ReplaySessionState> entryMatcher, Iterable<ResponseInterceptor> responseInterceptors, HttpAssistant<RequestCapture, HttpResponse> bmpAssistant, Supplier<? extends ReplaySessionState> sessionStateFactory) {
        this.entryMatcher = requireNonNull(entryMatcher);
        this.responseInterceptors = ImmutableList.copyOf(responseInterceptors);
        this.bmpAssistant = requireNonNull(bmpAssistant);
        this.sessionStateFactory = requireNonNull(sessionStateFactory);
    }

    @Override
    public ReplaySessionState createFreshState() {
        return sessionStateFactory.get();
    }

    @Override
    public ResponseCapture manufacture(ReplaySessionState state, RequestCapture capture) {
        state.register(capture.request);
        return manufacture(state, bmpAssistant, capture);
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

    protected <Q> ResponseCapture manufacture(ReplaySessionState sessionState, HttpAssistant<Q, HttpResponse> assistant, Q incoming) {
        ParsedRequest request;
        try {
            request = assistant.parseRequest(incoming);
        } catch (IOException e) {
            log.error("failed to read from incoming request", e);
            ImmutableHttpResponse outgoing = createParsingFailedResponse();
            HttpResponse netty = assistant.constructResponse(incoming, outgoing);
            return ResponseCapture.error(netty);
        }
        @Nullable HttpRespondable bestEntry = entryMatcher.findTopEntry(sessionState, request);
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
