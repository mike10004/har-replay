package io.github.mike10004.harreplay.tests;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.net.HostAndPort;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarResponse;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.ReplayServerConfig.RegexHolder;
import io.github.mike10004.harreplay.ReplayServerConfig.ResponseHeaderTransform;
import io.github.mike10004.harreplay.ReplayServerConfig.StringLiteral;
import io.github.mike10004.harreplay.ReplaySessionControl;
import io.github.mike10004.harreplay.tests.Fixtures.FixturesRule;
import io.github.mike10004.harreplay.tests.ReplayManagerTester.ReplayClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.StatusLine;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.hamcrest.Description;
import org.hamcrest.DiagnosingMatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public abstract class ReplayManagerTestFoundation {

    protected static final boolean debug = false;
    protected static final boolean dumpHarResponseComparison = false;

    @ClassRule
    public static FixturesRule fixturesRule = Fixtures.asRule();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    protected abstract ReplayManagerTester createTester(Path tempDir, File harFile, ReplayServerConfig config) throws IOException;

    protected abstract String getReservedPortSystemPropertyName();

    protected static void dumpTagged(String tag, String content, PrintStream out) {
        out.println("============================================================");
        out.format("===================== start %s: %3d characters ============%n", tag, content.length());
        out.println("============================================================");
        out.print(content);
        out.println("============================================================");
        out.format("===================== end %s ===========================%n", tag);
        out.println("============================================================");
    }

    protected static <T> Predicate<T> describing(Predicate<T> predicate, String description) {
        return new Predicate<T>() {
            @Override
            public boolean test(T t) {
                return predicate.test(t);
            }

            @Override
            public String toString() {
                return description;
            }
        };
    }

    protected static class MyMatcher extends DiagnosingMatcher<String> {

        public final String statement;
        private final Predicate<? super String> predicate;

        public MyMatcher(String statement, Predicate<? super String> predicate) {
            this.statement = statement;
            this.predicate = predicate;
        }

        @Override
        protected boolean matches(Object item, Description mismatchDescription) {
            if (!(item instanceof String)) {
                mismatchDescription.appendText("not a string");
                return false;
            }
            boolean ok = predicate.test((String) item);
            if (!ok) {
                mismatchDescription.appendText(StringUtils.abbreviateMiddle(item.toString(), "[...]", 1024));
            }
            return ok;
        }

        @Override
        public void describeTo(Description d) {
            d.appendText(predicate.toString());
        }
    }

    protected static MyMatcher matcher(String description, Predicate<? super String> predicate) {
        return new MyMatcher(description, predicate);
    }

    protected static MyMatcher matchHarResponse(Har har, URI uri) {
        HarEntry basicEntry = har.getLog().getEntries().stream().filter(entry -> uri.getPath().equals(URI.create(entry.getRequest().getUrl()).getPath())).findFirst().get();
        String expectedText = basicEntry.getResponse().getContent().getText();
        if (dumpHarResponseComparison) {
            dumpTagged("expected", expectedText, System.out);
        }
        return matcher("har response content", describing(expectedText::equals, "har response content incorrect; expected " + StringUtils.abbreviateMiddle(expectedText, "[...]", 256)));
    }

    protected void fetchAndCompareToHar(File harFile, URI uri) throws Exception {
        Har har = new HarReader().readFromFile(harFile);
        MyMatcher checker = matchHarResponse(har, uri);
        fetchAndExamine(harFile, uri, newApacheClient(uri, false), ReplayServerConfig.empty(), checker);
    }

    protected void dumpHar(File harFile, PrintStream out) throws IOException {
        List<HarEntry> entries;
        try {
            entries = new HarReader().readFromFile(harFile, HarReaderMode.LAX).getLog().getEntries();
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
        out.format("%s contents:%n", harFile);
        entries.forEach(entry -> {
            String reqDescription = String.format("%s %s", entry.getRequest().getMethod(), entry.getRequest().getUrl());
            HarResponse rsp = entry.getResponse();
            String rspDescription;
            if (rsp != null) {
                rspDescription = String.format("%s %s %s %s", rsp.getStatus(), rsp.getStatusText(), rsp.getContent().getMimeType(), rsp.getBodySize());
            } else {
                rspDescription = "<absent>";
            }
            out.format("    %s -> %s%n", reqDescription, rspDescription);
        });
    }

    protected ResponseSummary fetch(File harFile, URI uri, ApacheRecordingClient client, ReplayServerConfig config) throws Exception {
        Path tempDir = temporaryFolder.getRoot().toPath();
        ReplayManagerTester tester = createTester(tempDir, harFile, config);
        int port = ReplayManagerTester.findReservedPort(getReservedPortSystemPropertyName());
        Multimap<URI, ResponseSummary> responses = tester.exercise(client, port);
        Collection<ResponseSummary> responsesForUri = responses.get(uri);
        assertFalse("no response for uri " + uri, responsesForUri.isEmpty());
        ResponseSummary response = responsesForUri.iterator().next();
        System.out.format("response: %s%n", response.statusLine);
        return response;
    }

    protected void fetchAndExamine(File harFile, URI uri, ApacheRecordingClient client, ReplayServerConfig config, MyMatcher responseContentChecker) throws Exception {
        if (debug) {
            dumpHar(harFile, System.out);
            System.out.println();
        }
        ResponseSummary response = fetch(harFile, uri, client, config);
        if (dumpHarResponseComparison) {
            dumpTagged("actual", response.entity, System.out);
        }
        checkState(HttpStatus.SC_OK == response.statusLine.getStatusCode(), "should have received '200 OK' response but was '%s'", response.statusLine);
        System.out.println(StringUtils.abbreviate(response.entity, 128));
        assertThat(responseContentChecker.statement, response.entity, responseContentChecker);
    }

    public static ResponseHeaderTransform createLocationHttpsToHttpTransform() {
        return ResponseHeaderTransform.valueByNameAndValue(StringLiteral.of(org.apache.http.HttpHeaders.LOCATION),
                RegexHolder.of("^https://(.+)"), StringLiteral.of("http://$1"));
    }

    protected static class ApacheRecordingClient implements ReplayClient<Multimap<URI, ResponseSummary>> {

        private final ImmutableList<URI> urisToGet;
        private final boolean transformHttpsToHttp;

        ApacheRecordingClient(Iterable<URI> urisToGet, boolean transformHttpsToHttp) {
            this.urisToGet = ImmutableList.copyOf(urisToGet);
            this.transformHttpsToHttp = transformHttpsToHttp;
        }

        private URI transformUri(URI uri) { // equivalent of switcheroo extension
            if (transformHttpsToHttp && "https".equals(uri.getScheme())) {
                try {
                    return new URIBuilder(uri).setScheme("http").build();
                } catch (URISyntaxException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                return uri;
            }
        }

        @Override
        public Multimap<URI, ResponseSummary> useReplayServer(Path tempDir, ReplaySessionControl sessionControl) throws Exception {
            HostAndPort proxy = sessionControl.getSocketAddress();
            Multimap<URI, ResponseSummary> result = ArrayListMultimap.create();
            RedirectStrategy redirectStrategy = new DefaultRedirectStrategy() {
                @Override
                public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
                    HttpUriRequest redirect = super.getRedirect(request, response, context);
                    System.out.format("redirected to %s %s%n", redirect.getMethod(), redirect.getURI());
                    return redirect;
                }
            };
            try (CloseableHttpClient client = HttpClients.custom()
                    // TODO figure out why we need to disable compression here;
                    //      it shouldn't be necessary but without it
                    // .disableContentCompression()
                    .setProxy(new HttpHost(proxy.getHost(), proxy.getPort()))
                    .setRedirectStrategy(redirectStrategy)
                    .setRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
                    .build()) {
                for (URI uri : urisToGet) {
                    URI transformedUri = transformUri(uri);
                    System.out.format("fetching %s as %s%n", uri, transformedUri);
                    HttpGet get = new HttpGet(transformedUri);
                    try (CloseableHttpResponse response = client.execute(get)) {
                        StatusLine statusLine = response.getStatusLine();
                        String entity = toString(response);
                        result.put(uri, new ResponseSummary(statusLine, entity));
                    }
                }
            }
            return result;
        }

        private static String toString(HttpResponse response) throws IOException {
            String entityContent = EntityUtils.toString(response.getEntity());
            if (debug) {
                Header[] allHeaders = response.getAllHeaders();
                HttpEntity entity = response.getEntity();
                Long contentLengthValue = null;
                String contentTypeValue = null;
                if (entity != null) {
                    Header contentType = entity.getContentType();
                    if (contentType != null) {
                        contentTypeValue = contentType.getValue();
                    }
                    contentLengthValue = entity.getContentLength();
                }
                System.out.format("toString: %s, length = %s%n", contentTypeValue, contentLengthValue);
                System.out.format("toString: %d headers%n", allHeaders.length);
                Stream.of(allHeaders).forEach(header -> {
                    System.out.format("%s: %s%n", header.getName(), header.getValue());
                });
            }
            return entityContent;
        }

    }

    protected ApacheRecordingClient newApacheClient(URI uri, boolean transformHttpsToHttp) {
        return new ApacheRecordingClient(ImmutableList.of(uri), transformHttpsToHttp);
    }

    protected static class ResponseSummary {
        public final StatusLine statusLine;
        public final String entity;

        private ResponseSummary(StatusLine statusLine, String entity) {
            this.statusLine = statusLine;
            this.entity = entity;
        }
    }

}