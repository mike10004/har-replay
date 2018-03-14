package io.github.mike10004.harreplay.vhsimpl;

import io.github.mike10004.harreplay.ReplayServerConfig.ResponseHeaderTransform;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.ResponseInterceptor;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class HeaderTransformInterceptor implements ResponseInterceptor {

    private final ResponseHeaderTransform headerTransform;
    private final Pattern nameMatchRegex;
    private final Pattern valueMatchRegex;

    public HeaderTransformInterceptor(ResponseManufacturerConfig config, ResponseHeaderTransform headerTransform) {
        this.headerTransform = requireNonNull(headerTransform, "header transform");
        nameMatchRegex = headerTransform.getNameMatch().asRegex();
        valueMatchRegex = headerTransform.getValueMatch().asRegex();
    }

    @Override
    public HttpRespondable intercept(ParsedRequest parsedRequest, HttpRespondable httpRespondable) {
        if (isAnyTransformRequired(httpRespondable)) {
            return new HeaderTransformRespondableWrapper(httpRespondable);
        }
        return httpRespondable;
    }

    protected boolean isAnyTransformRequired(Map.Entry<String, String> header) {
        return headerTransform.getNameMatch().isMatchingHeaderName(header.getKey())
                && headerTransform.getValueMatch().isMatchingHeaderValue(header.getKey(), header.getValue());
    }

    protected boolean isAnyTransformRequired(HttpRespondable response) {
        return response.streamHeaders().anyMatch(this::isAnyTransformRequired);
    }

    protected Map.Entry<String, String> transform(Map.Entry<String, String> header) {
        String name = header.getKey(), value = header.getValue();
        String toName = headerTransform.getNameImage().transformHeaderName(name, nameMatchRegex);
        String toValue = headerTransform.getValueImage().transformHeaderValue(name, valueMatchRegex, value);
        if (!Objects.equals(name, toName) || !Objects.equals(value, toValue)) {
            return new SimpleImmutableEntry<>(toName, toValue);
        } else {
            return header;
        }
    }

    class HeaderTransformRespondableWrapper extends HttpRespondableWrapper {

        public HeaderTransformRespondableWrapper(HttpRespondable delegate) {
            super(delegate);
        }

        @Override
        public Stream<? extends Entry<String, String>> streamHeaders() {
            return delegate.streamHeaders()
                    .map(header -> {
                        if (isAnyTransformRequired(header)) {
                            return transform(header);
                        } else {
                            return header;
                        }
                    }).filter(header -> {
                        return header.getKey() != null && header.getValue() != null;
                    });
        }
    }
}
