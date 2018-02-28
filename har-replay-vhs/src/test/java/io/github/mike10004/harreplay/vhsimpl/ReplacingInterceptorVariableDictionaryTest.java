package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ReplacingInterceptorVariableDictionaryTest {

    private final TestCase testCase;

    public ReplacingInterceptorVariableDictionaryTest(TestCase testCase) {
        this.testCase = testCase;
    }

    private static class TestCase {
        public final ParsedRequest request;
        public final String variableName;
        public final boolean expectNameValid;
        @Nullable
        public final String expectedValueIfValid;

        private TestCase(ParsedRequest request, String variableName, boolean expectNameValid, @Nullable String expectedValueIfValid) {
            this.request = request;
            this.variableName = variableName;
            this.expectNameValid = expectNameValid;
            this.expectedValueIfValid = expectedValueIfValid;
        }

        public static TestCase valid(ParsedRequest request, String variableName, @Nullable String expectedValue) {
            return new TestCase(request, variableName, true, expectedValue);
        }

        public static TestCase invalid(ParsedRequest request, String variableName) {
            return new TestCase(request, variableName, false, null);
        }
    }

    @Parameters
    public static List<TestCase> testCases() {
        String sizeParamValue = "1200x800";
        Multimap<String, String> query = ImmutableMultimap.<String, String>builder()
                .put("size", sizeParamValue)
                .build();
        String contentType = MediaType.JPEG.toString();
        Multimap<String, String> headers = ImmutableMultimap.<String, String>builder()
                .put(HttpHeaders.CONTENT_TYPE, contentType)
                .build();
        URI url = URI.create("https://www.example.com/");
        ParsedRequest request = ParsedRequest.inMemory(HttpMethod.GET, url, query, headers, null);
        return ImmutableList.<TestCase>builder()
                .add(TestCase.valid(request, "request.url", request.url.toString()))
                .add(TestCase.invalid(request, "request.blah"))
                .add(TestCase.invalid(request, "oogabooga"))
                .add(TestCase.invalid(request, "response.status"))
                .add(TestCase.valid(request, "request.method", request.method.name()))
                .add(TestCase.valid(request, "request.headers.content-type", contentType))
                .add(TestCase.valid(request, "request.headers.Content-Type", contentType))
                .add(TestCase.valid(request, "request.headers.via", null))
                .add(TestCase.valid(request, "request.query.size", sizeParamValue))
                .add(TestCase.valid(request, "request.query.Size", null))
                .add(TestCase.valid(request, "request.query.legolas", null))
                .build();
    }

    @Test
    public void substitute() {
        @Nullable
        Optional<String> result = new ReplacingInterceptorVariableDictionary(testCase.request)
                .substitute(testCase.variableName);
        assertEquals("variable name found", testCase.expectNameValid, result != null);
        if (result != null) {
            String value = result.orElse(null);
            assertEquals("substituted value", testCase.expectedValueIfValid, value);
        }
    }
}