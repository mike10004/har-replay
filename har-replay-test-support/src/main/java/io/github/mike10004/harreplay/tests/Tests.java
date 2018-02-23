package io.github.mike10004.harreplay.tests;

import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.net.HostAndPort;
import io.github.mike10004.harreplay.tests.ImmutableHttpResponse.Builder;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class Tests {

    private Tests () {

    }

    private static boolean isHttpErrorCode(int statuscode) {
        return statuscode / 100 >= 4;
    }

    public static ImmutableHttpResponse fetch(HostAndPort proxy, URI url) throws IOException {
        return fetch(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.getHost(), proxy.getPort())), url);
    }

    public static ImmutableHttpResponse fetch(Proxy proxy, URI url) throws IOException {
        checkArgument("http".equals(url.getScheme()) || "https".equals(url.getScheme()));
        HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection(proxy);
        try {
            int status = conn.getResponseCode();
            Builder b = ImmutableHttpResponse.builder(status);
            conn.getHeaderFields().forEach((name, values) -> {
                if (name != null) {
                    values.forEach(value -> b.header(name, value));
                }
            });
            byte[] data;
            try (InputStream stream = isHttpErrorCode(status) ? conn.getErrorStream() : conn.getInputStream()) {
                data = ByteStreams.toByteArray(stream);
            }
            b.data(ByteSource.wrap(data));
            return b.build();
        } finally {
            conn.disconnect();
        }
    }


}
