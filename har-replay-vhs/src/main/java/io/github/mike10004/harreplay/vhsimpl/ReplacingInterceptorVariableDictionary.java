package io.github.mike10004.harreplay.vhsimpl;

import io.github.mike10004.harreplay.VariableDictionary;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class ReplacingInterceptorVariableDictionary implements VariableDictionary {

    public static final String KEY_REQUEST_URL = "request.url";
    public static final String KEY_REQUEST_METHOD = "request.method";
    public static final String PREFIX_KEY_REQUEST_HEADER = "request.header.";
    public static final String PREFIX_KEY_REQUEST_QUERY = "request.query.";

    private final ParsedRequest request;

    public ReplacingInterceptorVariableDictionary(ParsedRequest request) {
        this.request = requireNonNull(request);
    }

    @Nullable
    @Override
    public Optional<String> substitute(String variableName) {
        try {
            @Nullable String substitution = substituteOrThrow(variableName);
            return Optional.ofNullable(substitution);
        } catch (UnknownVariableNameException e) {
            //noinspection OptionalAssignedToNull
            return null;
        }
    }

    static class UnknownVariableNameException extends IllegalArgumentException {
        public UnknownVariableNameException(String variableName) {
            super(StringUtils.abbreviate(variableName, 128));
        }
    }

    /**
     *
     * @param variableName the variable name
     * @return the substituted value, or null if not defined
     * @throws UnknownVariableNameException if variable name is not supported
     */
    protected String substituteOrThrow(String variableName) {
        requireNonNull(variableName, "variableName");
        switch (variableName) {
            case KEY_REQUEST_URL:
                return request.url.toString();
            case KEY_REQUEST_METHOD:
                return request.method.name();
        }
        if (variableName.startsWith(PREFIX_KEY_REQUEST_HEADER)) {
            String headerName = StringUtils.removeStart(variableName, PREFIX_KEY_REQUEST_HEADER);
            return request.getFirstHeaderValue(headerName);
        }
        if (variableName.startsWith(PREFIX_KEY_REQUEST_QUERY)) {
            String queryParamName = StringUtils.removeStart(variableName, PREFIX_KEY_REQUEST_QUERY);
            if (request.query != null) {
                return NameValuePairList.caseInsensitive(request.query.entries(), Map.Entry::getKey, Map.Entry::getValue).getFirstValue(queryParamName);
            } else {
                return null;
            }
        }
        throw new UnknownVariableNameException(variableName);
    }
}
