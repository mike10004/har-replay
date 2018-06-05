package io.github.mike10004.vhs;

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.AtomicLongMap;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.net.URI;

public interface ReplaySessionState {
    static ReplaySessionState stateless() {
        return new ReplaySessionState() {
            @Override
            public void register(ParsedRequest r) {
            }

            @Override
            public int query(ParsedRequest request) {
                return 0;
            }
        };
    }

    void register(ParsedRequest request);

    int query(ParsedRequest request);

    static ReplaySessionState countingUrlMethodPairs() {
        return new CountingUrlMethodPairs();
    }

    class CountingUrlMethodPairs implements ReplaySessionState {

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
            return Ints.saturatedCast(requestCouples.get(requestCouple));
        }
    }
}
