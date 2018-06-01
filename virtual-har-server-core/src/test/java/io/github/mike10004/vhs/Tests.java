package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

public class Tests {

    private Tests() {}

    public static ParsedRequest createRequest(String method, String url) {
        URI uri = URI.create(url);
        return ParsedRequest.inMemory(HttpMethod.valueOf(method), uri, HttpRequests.parseQuery(uri), ImmutableMultimap.of(), null);
    }

    public static String readAsString(HttpRespondable response) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MediaType contentType = response.writeBody(baos);
        baos.flush();
        assert contentType.charset().isPresent();
        return new String(baos.toByteArray(), contentType.charset().get());
    }

}
