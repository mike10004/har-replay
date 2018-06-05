package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.repackaged.org.apache.http.client.utils.URLEncodedUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.annotation.Nullable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class CompositeEntryMatcherTest<T> {

    private final TestCase<T> testCase;

    public CompositeEntryMatcherTest(TestCase<T> testCase) {
        this.testCase = testCase;
    }

    @Test
    public <S> void findTopEntry() {
        CompositeEntryMatcher<T> matcher = new CompositeEntryMatcher<>(testCase.components);
        HttpRespondable response = matcher.findTopEntry(testCase.state, testCase.request);
        assertEquals(testCase.describe(), testCase.expectedResponse, response);
    }

    @Parameters
    public static List<TestCase<?>> testCases() {
        List<TestCase<?>> testCases = new ArrayList<>();
        ParsedRequest getExample = newRequest(HttpMethod.GET, "https://www.example.com/");
        HttpRespondable response1 = newResponse(200);
        HttpRespondable response2 = newResponse(201);
        testCases.add(new TestCase<>(new Object(), getExample, response1, constant(response1)));
        testCases.add(new TestCase<>(new Object(), getExample, null, Collections.emptyList()));
        testCases.add(new TestCase<>(new Object(), getExample, response1, constant(null), constant(response1)));
        testCases.add(new TestCase<>(new Object(), getExample, response1, constant(response1), constant(response2)));
        testCases.add(new TestCase<>(new Object(), getExample, response2, constant(response2), constant(response1)));
        testCases.add(new TestCase<>(new Object(), getExample, response1, constant(null), constant(response1), constant(null)));
        return testCases;
    }

    private static <S> EntryMatcher<S> constant(@Nullable HttpRespondable response) {
        return new EntryMatcher<S>() {
            @Nullable
            @Override
            public HttpRespondable findTopEntry(S state, ParsedRequest parsedRequest) {
                return response;
            }
        };
    }

    @SuppressWarnings("SameParameterValue")
    private static HttpRespondable newResponse(int status) {
        return HttpRespondable.inMemory(status, ImmutableMultimap.of(), MediaType.OCTET_STREAM, ByteSource.empty());
    }

    @SuppressWarnings("SameParameterValue")
    private static ParsedRequest newRequest(HttpMethod method, String urlStr) {
        URI url = URI.create(urlStr);
        String query = url.getQuery();
        Multimap<String, Optional<String>> queryMap_ = null;
        if (query != null) {
            Multimap<String, Optional<String>> queryMap = ArrayListMultimap.create();
            URLEncodedUtils.parse(url, StandardCharsets.UTF_8).forEach(pair -> {
                queryMap.put(pair.getKey(), Optional.ofNullable(pair.getValue()));
            });
            queryMap_ = queryMap;
        }
        Multimap<String, String> headers = ArrayListMultimap.create();
        return ParsedRequest.inMemory(method, url, queryMap_, headers, null);
    }

    private static class TestCase<S> {

        public final ParsedRequest request;
        @Nullable
        public final HttpRespondable expectedResponse;
        public final Iterable<EntryMatcher<? super S>> components;
        public final S state;

        public TestCase(S state, ParsedRequest request, @Nullable HttpRespondable expectedResponse, EntryMatcher<? super S>...components) {
            this(state, request, expectedResponse, Arrays.asList(components));
        }

        public TestCase(S state, ParsedRequest request, @Nullable HttpRespondable expectedResponse, Iterable<EntryMatcher<? super S>> components) {
            this.request = requireNonNull(request);
            this.expectedResponse = expectedResponse;
            this.components = requireNonNull(components);
            this.state = state;
        }

        public String describe() {
            return String.format("response to %s from %s", request, components);
        }
    }
}