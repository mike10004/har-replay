package io.github.mike10004.vhs.harbridge;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

class WrappingResponseEncoding implements HarResponseEncoding {

    private final ImmutableList<Map.Entry<String, HttpContentCodec>> codecs;

    public WrappingResponseEncoding(Iterable<Map.Entry<String, HttpContentCodec>> codecs) {
        this.codecs = ImmutableList.copyOf(codecs);
    }

    @VisibleForTesting
    static ImmutableList<WeightedEncoding> parseAcceptedEncodings(@Nullable String acceptEncodingHeaderValue) {
        acceptEncodingHeaderValue = Strings.nullToEmpty(acceptEncodingHeaderValue).trim();
        if (acceptEncodingHeaderValue.isEmpty()) {
            return ImmutableList.of();
        }
        List<String> weightedEncodings = HttpContentCodecs.parseEncodings(acceptEncodingHeaderValue);
        return weightedEncodings.stream()
                .map(WeightedEncoding::parse)
                .collect(ImmutableList.toImmutableList());

    }

    /**
     * Determines whether the unencoded (i.e. uncompressed) response is to be encoded using the
     * original response encoding, based on whether the new client accepts it. This ignores some
     * parts of the HTTP spec, like how to respond if the client explicitly rejects the 'identity' encoding,
     * which would be very unlikely to occur in practice. For each response content encoding in the given
     * list, we check that the client's accept-encoding specification explicitly accepts it or that
     * it is covered under a wildcard acceptance.
     * @param acceptEncodingHeaderValue the client's Accept-Encoding header value
     * @param parsedResponseContentEncodings the encodings originally applied to the content in the response captured in the HAR
     * @return true iff the client specifies that it can accept the encodings of the original response
     */
    @VisibleForTesting
    static boolean canServeOriginalResponseContentEncoding(List<String> parsedResponseContentEncodings, @Nullable String acceptEncodingHeaderValue) {
        List<WeightedEncoding> acceptsWeighted = parseAcceptedEncodings(acceptEncodingHeaderValue);
        return canServeOriginalResponseContentEncoding(parsedResponseContentEncodings, acceptsWeighted);
    }

    @VisibleForTesting
    static boolean canServeOriginalResponseContentEncoding(List<String> parsedResponseContentEncodingsList, List<WeightedEncoding> acceptsWeighted) {
        Set<String> parsedResponseContentEncodings = ImmutableSet.copyOf(parsedResponseContentEncodingsList);
        if (parsedResponseContentEncodings.isEmpty() || onlyContains(parsedResponseContentEncodings, HttpContentCodecs.CONTENT_ENCODING_IDENTITY)) {
            return true;
        }
        if (acceptsWeighted.isEmpty()) {
            return false;
        }
        for (String encoding : parsedResponseContentEncodings) {
            if (HttpContentCodecs.CONTENT_ENCODING_IDENTITY.equals(encoding)) {
                continue;
            }
            boolean canServeSingle = canServeResponseContentEncoding(encoding, acceptsWeighted);
            if (!canServeSingle) {
                return false;
            }
        }
        // if we're here, we know the client can accept each content encoding specified
        return true;
    }

    static boolean canServeResponseContentEncoding(String encoding, List<WeightedEncoding> acceptsWeighted) {
        WeightedEncoding star = null;
        for (WeightedEncoding we : acceptsWeighted) {
            AcceptDecision decision = we.accepts(encoding);
            if (decision == AcceptDecision.ACCEPT) {
                return true;
            }
            if (decision == AcceptDecision.REJECT) {
                return false;
            }
            if ("*".equals(we.encoding)) {
                star = we;
            }
        }
        // invariant: `encoding` never mentioned in list of weighted encodings
        if (star != null) {
            return star.isPositive();
        }
        return false;
    }

    static <T, U extends T> boolean onlyContains(Set<T> set, U element) {
        return set.size() == 1 && set.contains(element);
    }

    @Override
    public HarResponseData transformUnencoded(HarResponseData unencoded) {
        // TODO acutally do the encoding
        return unencoded;
    }

    static HarResponseEncoding fromHeaderValues(List<String> contentEncodings, @Nullable String acceptEncoding) {
        return NONE;
    }

    public static HarResponseEncoding fromHeaderValues(@Nullable String contentEncoding, @Nullable String acceptEncoding) {
        return NONE;
    }

    private static final HarResponseEncoding NONE = new WrappingResponseEncoding(Collections.emptyList());
}
