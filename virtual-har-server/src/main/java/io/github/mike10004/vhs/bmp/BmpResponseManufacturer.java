package io.github.mike10004.vhs.bmp;

/**
 * Interface that defines a method to manufacture responses that the proxy
 * will send to the client.
 */
public interface BmpResponseManufacturer<S> {

    /**
     * Manufactures a response for a given request.
     * @param capture the request
     * @return the response
     */
    ResponseCapture manufacture(S state, RequestCapture capture);

    S createFreshState();

    default WithState<S> withFreshState() {
        return withState(createFreshState());
    }

    default WithState<S> withState(S state) {
        return request -> {
            return manufacture(state, request);
        };
    }

    interface WithState<S> {
        ResponseCapture invoke(RequestCapture request);
    }
}
