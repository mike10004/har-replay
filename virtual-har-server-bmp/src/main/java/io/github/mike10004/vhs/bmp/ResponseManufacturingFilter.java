package io.github.mike10004.vhs.bmp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import io.github.mike10004.vhs.repackaged.org.apache.http.client.utils.URIBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import net.lightbody.bmp.filters.ClientRequestCaptureFilter;
import net.lightbody.bmp.filters.HttpsAwareFiltersAdapter;
import org.littleshoot.proxy.impl.ProxyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * Filters implementation that short-circuits all requests.
 *
 * <p>This is a modification of {@link net.lightbody.bmp.filters.HarCaptureFilter} that
 * only captures the response and delegates to a {@link BmpResponseManufacturer} to
 * produce a response.
 */
class ResponseManufacturingFilter extends HttpsAwareFiltersAdapter {

    private static final Logger log = LoggerFactory.getLogger(ResponseManufacturingFilter.class);

    private transient final Object responseLock = new Object();
    private final transient AtomicBoolean unreachableExceptionThrown = new AtomicBoolean(false);
    private volatile boolean responseSent;
    private final RequestAccumulator requestAccumulator;
    private final BmpResponseListener responseListener;

    /**
     * The requestCaptureFilter captures all request content, including headers, trailing headers, and content. This filter
     * delegates to it when the clientToProxyRequest() callback is invoked. If this request does not need content capture, the
     * ClientRequestCaptureFilter filter will not be instantiated and will not capture content.
     */
    private final ClientRequestCaptureFilter requestCaptureFilter;

    private final BmpResponseManufacturer responseManufacturer;

    /**
     * Create a new instance.
     * @param originalRequest the original HttpRequest from the HttpFiltersSource factory
     * @param ctx channel handler context
     * @throws IllegalArgumentException if request method is {@code CONNECT}
     */
    public ResponseManufacturingFilter(HttpRequest originalRequest, ChannelHandlerContext ctx, BmpResponseManufacturer responseManufacturer, BmpResponseListener responseListener) {
        super(originalRequest, ctx);
        if (ProxyUtils.isCONNECT(originalRequest)) {
            throw new IllegalArgumentException("HTTP CONNECT requests not supported by these filters");
        }
        requestAccumulator = new RequestAccumulator(originalRequest.getProtocolVersion());
        requestCaptureFilter = new ClientRequestCaptureFilter(originalRequest);
        this.responseManufacturer = requireNonNull(responseManufacturer);
        this.responseListener = requireNonNull(responseListener);
    }

    @Override
    public HttpResponse clientToProxyRequest(HttpObject httpObject) {
        return interceptRequest(httpObject);
    }

    protected void captureRequest(HttpObject httpObject) {
        requestCaptureFilter.clientToProxyRequest(httpObject);
        if (httpObject instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) httpObject;
            // associate this request's HarRequest object with the har entry
            captureMethodAndUrl(httpRequest);
            captureRequestHeaders(httpRequest);
        }

        if (httpObject instanceof LastHttpContent) {
            LastHttpContent lastHttpContent = (LastHttpContent) httpObject;
            captureTrailingHeaders(lastHttpContent);
            captureRequestContent(requestCaptureFilter.getFullRequestContents());
        }
    }

    protected HttpResponse interceptRequest(HttpObject httpObject) {
        captureRequest(httpObject);
        synchronized (responseLock) {
            checkState(!responseSent, "response already sent");
            log.debug("producing response for {}", describe(httpObject));
            responseSent = true;
        }
        HttpResponse response = produceResponse(freezeRequestCapture());
        return response;
    }

    @Nullable
    private static String describe(@Nullable HttpObject object) {
        if (object != null) {
            if (object instanceof HttpRequest) {
                String uri = ((HttpRequest)object).getUri();
                String method = ((HttpRequest)object).getMethod().name();
                return String.format("%s %s", uri, method);
            }
        }
        return null;
    }

    @VisibleForTesting
    RequestCapture freezeRequestCapture() {
        return requestAccumulator.freeze();
    }

    protected HttpResponse produceResponse(RequestCapture bmpRequest) {
        ResponseCapture responseCapture = responseManufacturer.manufacture(bmpRequest);
        responseListener.responding(bmpRequest, responseCapture);
        return responseCapture.response;
    }

    @Override
    public final HttpObject proxyToClientResponse(HttpObject httpObject) {
        return super.proxyToClientResponse(httpObject);
    }

    /**
     * Populates a HarRequest object using the method, url, and HTTP version of the specified request.
     * @param httpRequest HTTP request on which the HarRequest will be based
     */
    private void captureMethodAndUrl(HttpRequest httpRequest) {
        requestAccumulator.setMethod(httpRequest.getMethod().toString());
        requestAccumulator.setUrl(reconstructUrlFromRequest(httpRequest));
    }

    static boolean isDefaultPortForScheme(@Nullable String scheme, int port) {
        @Nullable Integer defaultPort = PortsAndSchemes.getDefaultPort(scheme);
        return defaultPort != null && port == defaultPort.intValue();
    }

    private static void cleanHostAndPort(URIBuilder uriBuilder, String hostHeaderValue, @Nullable String scheme) {
        HostAndPort hap = HostAndPort.fromString(hostHeaderValue);
        uriBuilder.setHost(hap.getHost());
        uriBuilder.setPort(-1);
        if (hap.hasPort() && !isDefaultPortForScheme(scheme, hap.getPort())) {
            uriBuilder.setPort(hap.getPort());
        }
    }

    protected String reconstructUrlFromRequest(HttpRequest request) {
        // the HAR spec defines the request.url field as:
        //     url [string] - Absolute URL of the request (fragments are not included).
        // the URI on the httpRequest may only identify the path of the resource, so find the full URL.
        // the full URL consists of the scheme + host + port (if non-standard) + path + query params + fragment.
        String fullUrl = getFullUrl(request);
        @Nullable String hostHeader = request.headers().get(com.google.common.net.HttpHeaders.HOST);
        return reconstructUrlFromFullUrlAndHostHeader(fullUrl, hostHeader);
    }

    protected String reconstructUrlFromFullUrlAndHostHeader(String fullUrl, @Nullable String hostHeader) {
        if (isHttps()) {
            if (hostHeader != null) {
                try {
                    URI fullUrlUri = new URI(fullUrl);
                    URIBuilder uriBuilder = new URIBuilder(fullUrlUri);
                    cleanHostAndPort(uriBuilder, hostHeader, fullUrlUri.getScheme());
                    fullUrl = uriBuilder.build().toString();
                } catch (URISyntaxException e) {
                    log.info("failed to reconstruct URL with proper host", e);
                }
            } else {
                log.info("no host header in request {} {}", requestAccumulator.getMethod(), fullUrl);
            }
        }
        return fullUrl;
    }

    protected void captureRequestHeaders(HttpRequest httpRequest) {
        HttpHeaders headers = httpRequest.headers();
        captureHeaders(headers);
    }

    protected void captureTrailingHeaders(LastHttpContent lastHttpContent) {
        HttpHeaders headers = lastHttpContent.trailingHeaders();
        captureHeaders(headers);
    }

    protected void captureHeaders(HttpHeaders headers) {
        for (Map.Entry<String, String> header : headers.entries()) {
            requestAccumulator.addHeader(header.getKey(), header.getValue());
        }
    }

    protected void captureRequestContent(byte[] fullMessage) {
        requestAccumulator.setBody(fullMessage);
    }

    /**
     * Exception thrown by methods that should not be reached because a response
     * should have been returned earlier. This comprises any method invoked between the
     * proxy and the remote server.
     */
    static class UnreachableCallbackException extends IllegalStateException {}

    private void raiseUnreachable() {
        HttpRequest captured = originalRequest;
        if (captured != null) {
            log.error("should be unreachable: (original) {} {}", captured.getMethod(), captured.getUri());
        } else {
            log.error("should be unreachable; no request captured");
        }
        if (!unreachableExceptionThrown.getAndSet(true)) {
            throw new UnreachableCallbackException();
        }
    }

    @Override
    public HttpObject serverToProxyResponse(HttpObject httpObject) {
        raiseUnreachable();
        return super.serverToProxyResponse(httpObject);
    }

    @Override
    public void proxyToServerResolutionSucceeded(String serverHostAndPort, InetSocketAddress resolvedRemoteAddress) {
        raiseUnreachable();
    }

    @Override
    public void proxyToServerRequestSending() {
        raiseUnreachable();
    }

    @Override
    public void proxyToServerResolutionFailed(String hostAndPort) {
        raiseUnreachable();
    }


    @Override
    public void proxyToServerConnectionFailed() {
        raiseUnreachable();
    }

    @Override
    public void serverToProxyResponseTimedOut() {
        raiseUnreachable();
    }

    @Override
    public HttpResponse proxyToServerRequest(HttpObject httpObject) {
        raiseUnreachable();
        return super.proxyToServerRequest(httpObject);
    }

    @Override
    public void proxyToServerRequestSent() {
        raiseUnreachable();
    }

    @Override
    public void serverToProxyResponseReceiving() {
        raiseUnreachable();
    }

    @Override
    public void serverToProxyResponseReceived() {
        raiseUnreachable();
    }

    @Override
    public void proxyToServerConnectionQueued() {
        raiseUnreachable();
    }

    @Override
    public InetSocketAddress proxyToServerResolutionStarted(String resolvingServerHostAndPort) {
        raiseUnreachable();
        return super.proxyToServerResolutionStarted(resolvingServerHostAndPort);
    }

    @Override
    public void proxyToServerConnectionStarted() {
        raiseUnreachable();
    }

    @Override
    public void proxyToServerConnectionSSLHandshakeStarted() {
        raiseUnreachable();
    }

    @Override
    public void proxyToServerConnectionSucceeded(ChannelHandlerContext serverCtx) {
        raiseUnreachable();
    }

    private static class PortsAndSchemes {

        @Nullable
        public static Integer getDefaultPort(@Nullable String scheme) {
            if (scheme == null) {
                return null;
            }
            return defaultPortByScheme.get(scheme.toLowerCase());
        }

        // https://gist.github.com/mahmoud/2fe281a8daaff26cfe9c15d2c5bf5c8b
        private static final ImmutableMap<String, Integer> defaultPortByScheme = ImmutableMap.<String, Integer>builder()
                .put("acap", 674)
                .put("afp", 548)
                .put("dict", 2628)
                .put("dns", 53)
                .put("ftp", 21)
                .put("git", 9418)
                .put("gopher", 70)
                .put("http", 80)
                .put("https", 443)
                .put("imap", 143)
                .put("ipp", 631)
                .put("ipps", 631)
                .put("irc", 194)
                .put("ircs", 6697)
                .put("ldap", 389)
                .put("ldaps", 636)
                .put("mms", 1755)
                .put("msrp", 2855)
                .put("mtqp", 1038)
                .put("nfs", 111)
                .put("nntp", 119)
                .put("nntps", 563)
                .put("pop", 110)
                .put("prospero", 1525)
                .put("redis", 6379)
                .put("rsync", 873)
                .put("rtsp", 554)
                .put("rtsps", 322)
                .put("rtspu", 5005)
                .put("sftp", 22)
                .put("smb", 445)
                .put("snmp", 161)
                .put("ssh", 22)
                .put("svn", 3690)
                .put("telnet", 23)
                .put("ventrilo", 3784)
                .put("vnc", 5900)
                .put("wais", 210)
                .put("ws", 80)
                .put("wss", 443)
                .build();

        private PortsAndSchemes() {
        }
    }
}
