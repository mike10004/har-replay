package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.Uninterruptibles;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD;
import com.browserup.bup.BrowserUpProxy;
import com.browserup.bup.BrowserUpProxyServer;
import com.browserup.harreader.model.Har;
import com.browserup.harreader.model.HarEntry;
import com.browserup.harreader.model.HarHeader;
import com.browserup.harreader.model.HarRequest;
import com.browserup.bup.proxy.CaptureType;
import io.github.mike10004.seleniumcapture.BrowserUpHars;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;

public class HarMaker {

    public static class EntrySpec {
        public final RequestSpec request;
        public final Duration preResponseDelay;
        public final NanoHTTPD.Response response;
        public final Duration postResponseDelay;

        public EntrySpec(RequestSpec request, NanoHTTPD.Response response) {
            this(request, Duration.ofMillis(50), response, Duration.ofMillis(50));
        }

        public EntrySpec(RequestSpec request, Duration preResponseDelay, NanoHTTPD.Response response, Duration postResponseDelay) {
            this.request = request;
            this.preResponseDelay = preResponseDelay;
            this.response = response;
            this.postResponseDelay = postResponseDelay;
        }

        private void provideBody(HttpRequestBase request) {
            if ("POST".equalsIgnoreCase(this.request.method) || "PUT".equalsIgnoreCase(this.request.method)) {
                if (this.request.body != null) {
                    ((HttpEntityEnclosingRequestBase) request).setEntity(new ByteArrayEntity(this.request.body));
                }
            }
        }

        public HttpUriRequest toHttpUriRequest(HostAndPort localSocket, UUID id) throws URISyntaxException {
            URI uri = new URIBuilder(request.url)
                    .setHost(localSocket.getHost())
                    .setPort(localSocket.getPort())
                    .setScheme("http").build();
            HttpRequestBase base;
            switch (request.method.toUpperCase()) {
                case "GET":
                    base = new HttpGet(uri);
                    break;
                case "POST":
                    base = new HttpPost(uri);
                    break;
                case "DELETE":
                    base = new HttpDelete(uri);
                    break;
                case "PUT":
                    base = new HttpPut(uri);
                    break;
                default:
                    throw new UnsupportedOperationException("not supported: " + request.method);
            }
            provideBody(base);
            this.request.headers.forEach(base::addHeader);
            base.addHeader(HEADER_ID, id.toString());
            return base;
        }
    }

    public void produceHarFile(List<EntrySpec> specs, HttpCallback callback, File outputHarFile) throws IOException, URISyntaxException {
        BrowserUpHars.writeHar(produceHar(specs, callback), outputHarFile, HAR_OUTPUT_CHARSET);
    }

    private static final Charset HAR_OUTPUT_CHARSET = StandardCharsets.UTF_8;

    public interface HttpCallback {
        void requestSent(RequestSpec requestSpec);
        void responseConsumed();
    }

    public Har produceHar(List<EntrySpec> specs, HttpCallback callback) throws IOException, URISyntaxException {
        Map<UUID, EntrySpec> specsWithIds = new LinkedHashMap<>();
        specs.forEach(spec -> {
            specsWithIds.put(UUID.randomUUID(), spec);
        });
        int port = 59876;
        NanoHTTPD nanoserver = new NanoHTTPD(port) {
            @Override
            public NanoHTTPD.Response serve(NanoHTTPD.IHTTPSession session) {
                System.out.format("starting to serve in response to request %s %s%n", session.getMethod(), session.getUri());
                String id = session.getHeaders().get(HEADER_ID);
                checkArgument(id != null, "expect every request to have %s header", HEADER_ID);
                EntrySpec spec = specsWithIds.get(UUID.fromString(id));
                checkState(spec != null, "no spec for id %s", id);
                Uninterruptibles.sleepUninterruptibly(spec.preResponseDelay.toMillis(), TimeUnit.MILLISECONDS);
                return spec.response;
            }

            @Override
            protected boolean useGzipWhenAccepted(Response r) {
                return false;
            }
        };
        BrowserUpProxy proxy = new BrowserUpProxyServer();
        proxy.enableHarCaptureTypes(EnumSet.allOf(CaptureType.class));
        proxy.newHar();
        proxy.start();
        try {
            nanoserver.start();
            HostAndPort nanoSocketAddress = HostAndPort.fromParts("localhost", nanoserver.getListeningPort());
            try {
                try (CloseableHttpClient client = HttpClients.custom()
                        .setProxy(new HttpHost("localhost", proxy.getPort()))
                        .build()) {
                    for (Map.Entry<UUID, EntrySpec> specWithId : specsWithIds.entrySet()) {
                        EntrySpec spec = specWithId.getValue();
                        HttpUriRequest httpUriRequest = spec.toHttpUriRequest(nanoSocketAddress, specWithId.getKey());
                        try (CloseableHttpResponse response = client.execute(httpUriRequest)) {
                            callback.requestSent(spec.request);
                            EntityUtils.consume(response.getEntity());
                            callback.responseConsumed();
                        }
                        Uninterruptibles.sleepUninterruptibly(spec.postResponseDelay.toMillis(), TimeUnit.MILLISECONDS);
                    }
                }
            } finally {
                nanoserver.stop();
            }
        } finally {
            proxy.stop();
        }
        Har har = proxy.getHar();
        checkTraffic(har, specsWithIds);
        cleanTraffic(har, specsWithIds);
        return har;
    }

    private void checkTraffic(Har har, Map<UUID, EntrySpec> specs) {
        List<HarEntry> entries = har.getLog().getEntries();
        checkArgument(entries.size() == specs.size(), "number of HAR entries does not match number of specs: %s != %s", entries.size(), specs.size());
    }

    private static final String HEADER_ID = "X-Virtual-Har-Server-Id";

    private HarHeader removeHeaderByName(HarRequest request, String headerName) {
        List<HarHeader> headers = request.getHeaders();
        int index = -1;
        for (int i = 0; i < headers.size(); i++) {
            if (headerName.equalsIgnoreCase(headers.get(i).getName())) {
                index = i;
            }
        }
        if (index == -1) {
            throw new IllegalArgumentException("no header by name " + headerName);
        }
        HarHeader header = headers.get(index);
        headers.remove(index);
        return header;
    }

    private void cleanTraffic(Har har, Map<UUID, EntrySpec> specs) throws IOException {
        Objects.requireNonNull(har, "har");
        Objects.requireNonNull(har.getLog(), "har.log");
        Objects.requireNonNull(har.getLog().getEntries(), "har.log.entries");
        List<HarEntry> entries = har.getLog().getEntries();
        for (HarEntry entry : entries) {
            HarRequest request = entry.getRequest();
            HarHeader idHeader = removeHeaderByName(request, HEADER_ID);
            String id = idHeader.getValue();
            checkState(id != null, "expect each request to have header %s", HEADER_ID);
            URI realUri = specs.get(UUID.fromString(id)).request.url;
            request.setUrl(realUri.toString());
        }
    }

}
