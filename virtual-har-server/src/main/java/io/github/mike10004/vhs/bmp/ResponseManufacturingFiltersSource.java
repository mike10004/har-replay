package io.github.mike10004.vhs.bmp;

import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.filters.HttpsAwareFiltersAdapter;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.impl.ProxyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

class ResponseManufacturingFiltersSource extends HttpFiltersSourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(ResponseManufacturingFiltersSource.class);

    private final BmpResponseManufacturer responseManufacturer;
    private final HostRewriter hostRewriter;
    private final BmpResponseListener bmpResponseListener;
    private final PassthruPredicate passthruPredicate;

    public ResponseManufacturingFiltersSource(BmpResponseManufacturer responseManufacturer, HostRewriter hostRewriter, BmpResponseListener bmpResponseListener, PassthruPredicate passthruPredicate) {
        this.responseManufacturer = requireNonNull(responseManufacturer);
        this.hostRewriter = requireNonNull(hostRewriter);
        this.bmpResponseListener = requireNonNull(bmpResponseListener);
        this.passthruPredicate = requireNonNull(passthruPredicate);
    }

    public interface PassthruPredicate {
        boolean isForwardable(HttpRequest originalRequest, @Nullable ChannelHandlerContext ctx);
    }

    @Override
    public HttpFilters filterRequest(HttpRequest originalRequest) {
        return doFilterRequest(originalRequest, null);
    }

    @Override
    public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {
        return doFilterRequest(originalRequest, ctx);
    }

    private HttpFilters doFilterRequest(HttpRequest originalRequest, @Nullable ChannelHandlerContext ctx) {
        if (passthruPredicate.isForwardable(originalRequest, ctx)) {
            log.debug("passes through: %s %s", originalRequest.getMethod(), originalRequest.getUri());
            return null;
        }
        if (ProxyUtils.isCONNECT(originalRequest)) {
            return createHostRewriteFilter(originalRequest, ctx, hostRewriter);
        } else {
            return createResponseManufacturingFilter(originalRequest, ctx, responseManufacturer, bmpResponseListener);
        }
    }

    /* package */ HostRewriteFilter createHostRewriteFilter(HttpRequest originalRequest, ChannelHandlerContext ctx, HostRewriter hostRewriter) {
        return new HostRewriteFilter(originalRequest, ctx, hostRewriter);
    }

    /* package */ ResponseManufacturingFilter createResponseManufacturingFilter(HttpRequest originalRequest, ChannelHandlerContext ctx, BmpResponseManufacturer responseManufacturer, BmpResponseListener bmpResponseListener) {
        return new ResponseManufacturingFilter(originalRequest, ctx, responseManufacturer, bmpResponseListener);
    }

    @VisibleForTesting
    static class HostRewriteFilter extends HttpsAwareFiltersAdapter {

        private final HostRewriter hostRewriter;

        public HostRewriteFilter(HttpRequest originalRequest, ChannelHandlerContext ctx, HostRewriter hostRewriter) {
            super(originalRequest, ctx);
            this.hostRewriter = requireNonNull(hostRewriter);
        }

        @Override
        public HttpResponse clientToProxyRequest(HttpObject httpObject) {
            if (httpObject instanceof HttpRequest) {
                HttpRequest req = (HttpRequest) httpObject;
                hostRewriter.replaceHost(req);
            }
            return super.clientToProxyRequest(httpObject);
        }
    }

    @Override
    public int getMaximumRequestBufferSizeInBytes() {
        return 0;
    }

    @Override
    public int getMaximumResponseBufferSizeInBytes() {
        return 0;
    }
}
