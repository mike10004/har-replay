package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.harreplay.ReplayServerConfig.ResponseHeaderTransform;
import io.github.mike10004.harreplay.ReplayServerConfig.StringLiteral;
import io.github.mike10004.harreplay.tests.ReplayManagerTestBase;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.apache.http.HttpStatus;
import org.junit.Test;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;

import static org.junit.Assert.*;

public class HeaderTransformInterceptorTest {

    @Test
    public void intercept() {
        String expectedValueAfterReplacement = "https://www.homestarrunner.com/";
        ResponseHeaderTransform headerTransform = ResponseHeaderTransform.valueByName(StringLiteral.of(HttpHeaders.LOCATION), StringLiteral.of(expectedValueAfterReplacement));
        Multimap<String, String> headers = ImmutableMultimap.of(HttpHeaders.LOCATION, "https://www.jeopardy.com/");
        String actualValueAfterReplacement = testIntercept(headers, headerTransform, HttpHeaders.LOCATION);
        assertEquals("location header value", expectedValueAfterReplacement, actualValueAfterReplacement);
    }

    @Nullable
    private String testIntercept(Multimap<String, String> headersBefore, ResponseHeaderTransform headerTransform, String targetHeaderName) {
        HeaderTransformInterceptor interceptor = new HeaderTransformInterceptor(VhsReplayManagerConfig.getDefault(), headerTransform);
        ParsedRequest request = ParsedRequest.inMemory(HttpMethod.GET, URI.create("http://www.example.com/"), ImmutableMultimap.of(), ImmutableMultimap.of(), null);
        HttpRespondable response = HttpRespondable.inMemory(HttpStatus.SC_OK, headersBefore, MediaType.OCTET_STREAM, new byte[16]);
        HttpRespondable transformed = interceptor.intercept(request, response);
        String targetHeaderValue = transformed.streamHeaders().filter(header -> targetHeaderName.equalsIgnoreCase(header.getKey()))
                .findFirst().map(java.util.Map.Entry::getValue).orElse(null);
        return targetHeaderValue;
    }

    @Test
    public void isAnyTransformRequired() {
        testIsAnyTransformRequired(ResponseHeaderTransform.name(StringLiteral.of("X"), StringLiteral.of("Y")), true, "W", "www", "X", "xxx", "Z", "zzz");
        testIsAnyTransformRequired(ResponseHeaderTransform.name(StringLiteral.of("X"), StringLiteral.of("Y")), false, "W", "www", "Y", "yyy", "Z", "zzz");
        ResponseHeaderTransform locationHeaderDropHttps = ReplayManagerTestBase.createLocationHttpsToHttpTransform();
        testIsAnyTransformRequired(locationHeaderDropHttps, true, HttpHeaders.LOCATION, "https://www.example.com/to");
    }

    @Test
    public void isAnyTransformRequired_header() {
        Map.Entry<String, String> header = new SimpleImmutableEntry<>(HttpHeaders.LOCATION, "https://www.mmm.com/");
        ResponseHeaderTransform locationHeaderDropHttps = ReplayManagerTestBase.createLocationHttpsToHttpTransform();
        assertTrue("name match", locationHeaderDropHttps.getNameMatch().isMatchingHeaderName(header.getKey()));
        assertTrue("value match", locationHeaderDropHttps.getValueMatch().isMatchingHeaderValue(header.getKey(), header.getValue()));
        HeaderTransformInterceptor interceptor = newInterceptor(locationHeaderDropHttps);
        boolean anyRequired = interceptor.isAnyTransformRequired(header);
        assertTrue("transform required on " + header, anyRequired);
    }

    @Test
    public void applyRegexTransform() {
        ResponseHeaderTransform locationHeaderDropHttps = ReplayManagerTestBase.createLocationHttpsToHttpTransform();
        HeaderTransformInterceptor interceptor = newInterceptor(locationHeaderDropHttps);
        Map.Entry<String, String> locationHeaderBefore = new SimpleImmutableEntry<>(HttpHeaders.LOCATION, "https://www.mmm.com/");
        Map.Entry<String, String> expectedAfter = new SimpleImmutableEntry<>(HttpHeaders.LOCATION, "http://www.mmm.com/");
        Map.Entry<String, String> actualAfter = interceptor.transform(locationHeaderBefore);
        assertEquals("transformed location header", expectedAfter, actualAfter);
    }

    private static HeaderTransformInterceptor newInterceptor(ResponseHeaderTransform transform) {
        return new HeaderTransformInterceptor(VhsReplayManagerConfig.getDefault(), transform);
    }

    private void testIsAnyTransformRequired(ResponseHeaderTransform transform, boolean expected, String...headerNamesAndValues) {
        Multimap<String, String> headers = ArrayListMultimap.create();
        for (int i = 0; i < headerNamesAndValues.length; i += 2) {
            String name = headerNamesAndValues[i], value = "";
            if ((i + 1) < headerNamesAndValues.length) {
                value = headerNamesAndValues[i + 1];
            }
            headers.put(name, value);
        }
        HttpRespondable response = HttpRespondable.inMemory(HttpStatus.SC_OK, headers, MediaType.OCTET_STREAM, new byte[0]);
        HeaderTransformInterceptor interceptor = newInterceptor(transform);
        boolean actual = interceptor.isAnyTransformRequired(response);
        assertEquals(String.format("%s on %s", transform, headers), expected, actual);
    }

}