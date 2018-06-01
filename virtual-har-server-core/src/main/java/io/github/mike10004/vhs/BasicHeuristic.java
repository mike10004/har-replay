package io.github.mike10004.vhs;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.harbridge.FormDataPart;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.repackaged.org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * Implementation of a heuristic that compares request headers, parameters, and bodies.
 * This is inspired by the JavaScript implementation of https://github.com/Stuk/server-replay.
 */
public class BasicHeuristic implements Heuristic {

    /**
     * Default character encoding for {@code x-www-form-urlencoded}.
     *
     * <p>Browsermob has this note about default charsets:
     * <blockquote>
     * The default charset when the Content-Type header does not specify a charset. According to RFC 7231 Appendix B:
     * <pre>
     *     The default charset of ISO-8859-1 for text media types has been
     *     removed; the default is now whatever the media type definition says.
     *     Likewise, special treatment of ISO-8859-1 has been removed from the
     *     Accept-Charset header field.
     * </pre>
     *
     * Technically, we would have to determine the charset on a per-content-type basis, but generally speaking, UTF-8 is a
     * pretty safe default. (NOTE: In the previous HTTP/1.1 spec, section 3.7.1, the default charset was defined as ISO-8859-1.)
     * </blockquote>
     *
     * The media type {@code x-www-form-urlencoded} is not technically a text media type, but URLs
     * are supposed to be ASCII safe, and the media type definition https://www.iana.org/assignments/media-types/application/x-www-form-urlencoded
     * specifies the "7bit" encoding consideration. We default to UTF-8 because it is a superset of
     * ASCII and supports more characters than ISO-8859-1.
     */
    private static final Charset DEFAULT_FORM_DATA_CHARSET = StandardCharsets.UTF_8;

    public static final int DEFAULT_THRESHOLD_EXCLUSIVE = 0;

    private static final int DEFAULT_INCREMENT = 100;
    private final int increment;
    private final int halfIncrement;
    private final FormDataDecoder formDataDecoder;

    public BasicHeuristic() {
        this(DEFAULT_INCREMENT, new RepackagedHttpClientFormDataDecoder());
    }

    @SuppressWarnings("unused")
    public BasicHeuristic(int increment) {
        this(increment, new RepackagedHttpClientFormDataDecoder());
    }

    public BasicHeuristic(int increment, FormDataDecoder formDataDecoder) {
        this.increment = increment;
        checkArgument(increment % 2 == 0, "increment must be even: %s", increment);
        this.halfIncrement = this.increment / 2;
        this.formDataDecoder = requireNonNull(formDataDecoder);
    }

    interface FormDataDecoder {
        Multimap<String, Optional<String>> decode(ByteSource body, MediaType contentType) throws IOException;
    }

    static class RepackagedHttpClientFormDataDecoder implements FormDataDecoder {

        @Override
        public Multimap<String, Optional<String>> decode(ByteSource body, MediaType mediaType) throws IOException {
            /*
             * Content-type magic here is a little weird. I *think* that form data should
             * always be US-ASCII, just like query parameters are supposed to be. The request
             * body ought to be accompanied by a content-type header that specifies the charset,
             * but if the charset is not specified, what do we do? Assume ISO-8859-1 as we do
             * for other HTTP-transported data? Or assume UTF-8 as we frequently do with query
             * parameters? Currently, we're defaulting to ISO-8859-1, because that would seem
             * to be more in line with the HTTP spec, and I have no strong opinion.
             */


                Charset charset = mediaType.charset().or(DEFAULT_FORM_DATA_CHARSET);
                String queryString = body.asCharSource(charset).read();
                // It's possible that the charset for decoding parameters, specified as an argument
                // here, is not necessarily the same as the content-type charset
                List<Map.Entry<String, String>> params = URLEncodedUtils.parse(queryString, charset);
                if (params != null) {
                    Multimap<String, Optional<String>> mm = ArrayListMultimap.create();
                    params.stream()
                            .map(p -> new SimpleImmutableEntry<>(p.getKey(), Optional.ofNullable(p.getValue())))
                            .forEach(p -> mm.put(p.getKey(), p.getValue()));
                    return mm;
                } else {
                    return ImmutableMultimap.of();
                }

        }
    }

    protected int rateQuerySameness(@Nullable Multimap<String, Optional<String>> entryQuery, @Nullable Multimap<String, Optional<String>> requestQuery) {
        int points = 0;
        if (entryQuery == null && requestQuery == null) {
            points += increment;
        } else {
            //noinspection ConstantConditions
            entryQuery = MoreObjects.firstNonNull(entryQuery, ImmutableMultimap.of());
            //noinspection ConstantConditions
            requestQuery = MoreObjects.firstNonNull(requestQuery, ImmutableMultimap.of());
            for (String name : requestQuery.keySet()) {
                if (!entryQuery.containsKey(name)) {
                    points -= halfIncrement;
                } else {
                    Multiset<Optional<String>> entryParamValues = stripProtocolFromOptionals(entryQuery.get(name));
                    Multiset<Optional<String>> requestParamValues = stripProtocolFromOptionals(requestQuery.get(name));
                    if (entryParamValues.equals(requestParamValues)) {
                        points += increment;
                    }
                }
            }

            for (String name : entryQuery.keySet()) {
                if (!requestQuery.containsKey(name)) {
                    points -= halfIncrement;
                }
            }
        }
        return Math.max(0, points);
    }

    @Override
    public int rate(ParsedRequest entryRequest, ParsedRequest request) {
        // String name;
        URI requestUrl = request.url;
        Multimap<String, String> requestHeaders = request.indexedHeaders;
        // method, host and pathname must match
        if (requestUrl == null) {
            return 0;
        }
        if (entryRequest.method != request.method) {
            return 0;
        }
        if (!entryRequest.url.getHost().equals(requestUrl.getHost())) {
            return 0;
        }
        if (!entryRequest.url.getPath().equals(requestUrl.getPath())) {
            return 0;
        }
        int points = increment; // One point for matching above requirements
        points += rateQuerySameness(entryRequest.query, request.query);

        // each header
        Multimap<String, String> entryHeaders = entryRequest.indexedHeaders;
        for (String name : requestHeaders.keySet()) {
            if (entryHeaders.containsKey(name)) {
                points += stripProtocolFromStrings(entryHeaders.get(name)).equals(stripProtocolFromStrings(requestHeaders.get(name))) ? increment : 0;
            }
            // TODO handle missing headers and adjust score appropriately
        }

        if (request.method == HttpMethod.POST || request.method == HttpMethod.PUT) {
            if (!request.isBodyPresent() && !entryRequest.isBodyPresent()) {
                points += halfIncrement;
            } else if (request.isBodyPresent() && entryRequest.isBodyPresent()) {
                points += rateBodySameness(entryRequest, request);
            }
        }

        return points;
    }

    protected int rateBodySameness(ParsedRequest entryRequest, ParsedRequest request) {
        ByteSource requestBody = getBodyAsByteSource(request);
        ByteSource entryBody = getBodyAsByteSource(entryRequest);
        @Nullable String entryContentType = entryRequest.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
        @Nullable String requestContentType = request.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
        return rateBodySameness(entryBody, entryContentType, requestBody, requestContentType);
    }

    @Nullable
    private Multimap<String, Optional<String>> parseIfWwwFormData(ByteSource body, @Nullable String contentType) {
        if (contentType != null) {
            try {
                MediaType mediaType = MediaType.parse(contentType);
                if (MediaType.FORM_DATA.withoutParameters().equals(mediaType.withoutParameters())) {
                    return formDataDecoder.decode(body, mediaType);
                }
            } catch (RuntimeException | IOException ignore) {
                LoggerFactory.getLogger(getClass()).debug("failed to decode body as form data params");
            }
        }
        return null;
    }

    protected int rateBodySameness(ByteSource entryBody, @Nullable String entryContentType, ByteSource requestBody, @Nullable String requestContentType) {
        if (entryBody.sizeIfKnown().equals(requestBody.sizeIfKnown())) {
            if (entryBody.sizeIfKnown().isPresent() && entryBody.sizeIfKnown().get().equals(0L)) {
                return increment;
            }
        }
        // TODO examine content types here and return 0 score early if they're very different
        @Nullable Multimap<String, Optional<String>> entryParams = parseIfWwwFormData(entryBody, entryContentType);
        if (entryParams != null) {
            @Nullable Multimap<String, Optional<String>> requestParams = parseIfWwwFormData(requestBody, requestContentType);
            if (requestParams != null) {
                return rateQuerySameness(entryParams, requestParams);
            }
        }
        @Nullable Multiset<FormDataPart> entryFormData = parseIfMultipartFormData(entryBody, entryContentType);
        if (entryFormData != null) {
            @Nullable Multiset<FormDataPart> requestFormData = parseIfMultipartFormData(requestBody, requestContentType);
            if (requestFormData != null) {
                return rateFormDataSameness(entryFormData, requestFormData);
            }
        }
        try {
            return entryBody.contentEquals(requestBody) ? increment : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    @Nullable
    @SuppressWarnings("unused")
    protected Multiset<FormDataPart> parseIfMultipartFormData(ByteSource body, @Nullable String contentType) {
        // TODO implement multipart/form-data parsing and comparison
        //      and then remove "unused" warning suppression
        return null;
    }

    @SuppressWarnings("unused")
    protected int rateFormDataSameness(Multiset<FormDataPart> entryFormData, Multiset<FormDataPart> requestFormData) {
        // TODO implement rating form-data sameness
        //      and then remove "unused" warning suppression
        return 0;
    }

    private static ByteSource getBodyAsByteSource(ParsedRequest request) {
        return new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return request.openBodyStream();
            }
        };
    }

    private static Multiset<String> stripProtocolFromStrings(Collection<String> strings) {
        return strings.stream().map(string -> string.replaceAll("^https?", "")).collect(ImmutableMultiset.toImmutableMultiset());
    }

    private static Multiset<Optional<String>> stripProtocolFromOptionals(Collection<Optional<String>> strings) {
        return strings.stream()
                .map(stringOpt -> {
                    if (stringOpt.isPresent()) {
                        String string = stringOpt.get();
                        return Optional.of(string.replaceAll("^https?", ""));
                    } else {
                        return stringOpt;
                    }
                }).collect(ImmutableMultiset.toImmutableMultiset());
    }

}
