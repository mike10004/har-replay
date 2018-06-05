package io.github.mike10004.vhs;

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.AtomicLongMap;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.LoggerFactory;

import java.net.URI;

class BasicRequestTrackingState implements ReplaySessionState {

    private final AtomicLongMap<ImmutablePair<HttpMethod, URI>> requestCouples = AtomicLongMap.create();

    @Override
    public void register(ParsedRequest request) {
        ImmutablePair<HttpMethod, URI> requestCouple = pairify(request);
        requestCouples.getAndIncrement(requestCouple);
    }

    private ImmutablePair<HttpMethod, URI> pairify(ParsedRequest request) {
        return ImmutablePair.of(request.method, request.url);
    }

    @Override
    public int query(ParsedRequest request) {
        ImmutablePair<HttpMethod, URI> requestCouple = pairify(request);
        long value = requestCouples.get(requestCouple);
        if (value == 0L) {
            LoggerFactory.getLogger(getClass()).info("queried request that was never registered: {}", request);
        }
        return Ints.saturatedCast(value - 1L);
    }

    @Override
    public String toString() {
        return String.format("BasicRequestTrackingState{registered=%d}", requestCouples.size());
    }
}
