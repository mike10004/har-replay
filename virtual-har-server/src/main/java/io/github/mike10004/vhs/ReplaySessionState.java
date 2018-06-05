package io.github.mike10004.vhs;

import io.github.mike10004.vhs.harbridge.ParsedRequest;

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

            @Override
            public String toString() {
                return String.format("ReplaySessionState{STATELESS}@%08x", System.identityHashCode(this));
            }
        };
    }

    void register(ParsedRequest request);

    int query(ParsedRequest request);

    static ReplaySessionState countingUrlMethodPairs() {
        return new BasicRequestTrackingState();
    }

}
