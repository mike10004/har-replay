package io.github.mike10004.harreplay.tests;

import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import com.google.common.net.HostAndPort;
import io.github.bonigarcia.wdm.ChromeDriverManager;
import io.github.mike10004.harreplay.tests.ImmutableHttpResponse.Builder;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

public class Tests {

    public static final String SYSPROP_CHROMEDRIVER_VERSION = "har-replay.chromedriver.version";

    private static final String _RECOMMENDED_CHROME_DRIVER_VERSION = "2.39";

    private Tests () {

    }

    public static int findOpenPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static boolean isHttpErrorCode(int statuscode) {
        return statuscode / 100 >= 4;
    }

    public static Proxy toProxy(HostAndPort socketAddress) {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(socketAddress.getHost(), socketAddress.getPort()));
    }

    public static ImmutableHttpResponse fetch(HostAndPort proxy, URI url) throws IOException {
        return fetch(toProxy(proxy), url);
    }

    public static ImmutableHttpResponse fetchWithNoAcceptEncodingRequestHeader(Proxy proxy, URI url) throws IOException {
        return fetch(proxy, url);
    }

    public static ImmutableHttpResponse fetchWithNoAcceptEncodingRequestHeader(HostAndPort proxy, URI url) throws IOException {
        return fetch(proxy, url);
    }

    /**
     * Fetches with {@link URL#openConnection()}.
     * @param proxy the proxy to use
     * @param url the URL to fetch from
     * @return response
     * @throws IOException if something goes awry
     */
    public static ImmutableHttpResponse fetch(Proxy proxy, URI url) throws IOException {
        checkArgument("http".equals(url.getScheme()) || "https".equals(url.getScheme()));
        HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection(proxy);
        try {
            return captureResponse(conn);
        } finally {
            conn.disconnect();
        }
    }

    public static ImmutableHttpResponse captureResponse(HttpURLConnection conn) throws IOException {
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
    }

    @Nullable
    public static <V> V getCaseInsensitively(Map<String, V> map, String key) {
        return getCaseInsensitively(map.entrySet().stream(), key);
    }

    public static <V> V getCaseInsensitively(Stream<Map.Entry<String, V>> mapEntries, String key) {
        return mapEntries.filter(entry -> key.equalsIgnoreCase(entry.getKey()))
                .map(Entry::getValue)
                .findFirst().orElse(null);
    }

    public static String getRecommendedChromeDriverVersion() {
        return System.getProperty(SYSPROP_CHROMEDRIVER_VERSION, _RECOMMENDED_CHROME_DRIVER_VERSION);
    }

    public static void dump(Multimap<String, CharSource> sources, PrintStream out) {
        sources.forEach((tag, source) -> {
            out.format("%n================================================================================%n");
            out.format("%n==== start %-16s ================================================%n", tag);
            out.format("%n================================================================================%n");
            try {
                source.copyTo(out);
            } catch (IOException e) {
                System.err.println(e.toString());
            }
            out.format("%n================================================================================%n");
            out.format("%n==== end   %-16s ================================================%n", tag);
            out.format("%n================================================================================%n");
        });
    }

}
