package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.common.net.HostAndPort;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.bmp.ResponseManufacturingFiltersSource.PassthruPredicate;
import io.github.mike10004.vhs.bmp.ScratchDirProvider.Scratch;
import com.browserup.bup.BrowserUpProxy;
import com.browserup.bup.BrowserUpProxyServer;
import com.browserup.bup.mitm.CertificateAndKeySource;
import com.browserup.bup.mitm.TrustSource;
import com.browserup.bup.mitm.manager.ImpersonatingMitmManager;
import com.browserup.bup.proxy.CaptureType;
import org.littleshoot.proxy.MitmManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class BrowsermobVirtualHarServer implements VirtualHarServer {

    private static final Logger log = LoggerFactory.getLogger(BrowsermobVirtualHarServer.class);

    private Supplier<BrowserUpProxy> localProxyInstantiator = BrowserUpProxyServer::new;

    private final BrowsermobVhsConfig config;

    public BrowsermobVirtualHarServer(BrowsermobVhsConfig config) {
        this.config = requireNonNull(config);
    }

    @Override
    public VirtualHarServerControl start() throws IOException {
        List<Closeable> closeables = new ArrayList<>();
        Scratch scratch = config.scratchDirProvider.createScratchDir();
        closeables.add(scratch);
        CertificateAndKeySource certificateAndKeySource;
        BrowserUpProxy proxy;
        Path scratchPath = scratch.getRoot();
        try {
            certificateAndKeySource = config.certificateAndKeySourceFactory.produce(config, scratchPath);
            TlsEndpoint httpsInterceptionServer = config.tlsEndpointFactory.produce(config, scratchPath);
            closeables.add(httpsInterceptionServer);
            TrustSource trustSource = httpsInterceptionServer.getTrustSource();
            proxy = startProxy(config.bmpResponseManufacturer.withFreshState(), httpsInterceptionServer.getSocketAddress(), certificateAndKeySource, trustSource);
        } catch (RuntimeException | IOException e) {
            closeAll(closeables, true);
            throw e;
        }
        return new BrowsermobVhsControl(proxy, closeables);
    }

    private static void closeAll(Iterable<? extends Closeable> closeables, @SuppressWarnings("SameParameterValue") boolean swallowIOException) {
        for (Closeable closeable : closeables) {
            try {
                Closeables.close(closeable, swallowIOException);
            } catch (IOException e) {
                log.warn("failed to close " + closeable, e);
            }
        }
    }

    protected BrowserUpProxy startProxy(BmpResponseManufacturer.WithState<?> responseManufacturer,
                                      HostAndPort httpsHostRewriteDestination,
                                      CertificateAndKeySource certificateAndKeySource,
                                      TrustSource trustSource) throws IOException {
        BrowserUpProxy bmp = instantiateProxy();
        configureProxy(bmp, responseManufacturer, httpsHostRewriteDestination, certificateAndKeySource, config.bmpResponseListener, trustSource);
        bmp.enableHarCaptureTypes(getCaptureTypes());
        bmp.newHar();
        if (config.port == null) {
            bmp.start();
        } else {
            bmp.start(config.port);
        }
        return bmp;
    }

    protected MitmManager createMitmManager(@SuppressWarnings("unused") BrowserUpProxy proxy, CertificateAndKeySource certificateAndKeySource, TrustSource trustSource) {
        MitmManager mitmManager = ImpersonatingMitmManager.builder()
                .rootCertificateSource(certificateAndKeySource)
                .trustSource(trustSource)
                .build();
        return mitmManager;
    }

    protected PassthruPredicate createPassthruPredicate() {
        return DEFAULT_PASSTHRU_PREDICATE;
    }

    protected static final PassthruPredicate DEFAULT_PASSTHRU_PREDICATE = (req, ctx) -> false;

    protected BrowserUpProxy instantiateProxy() {
        return localProxyInstantiator.get();
    }

    protected void configureProxy(BrowserUpProxy bmp,
                                  BmpResponseManufacturer.WithState<?> responseManufacturer,
                                  HostAndPort httpsHostRewriteDestination,
                                  CertificateAndKeySource certificateAndKeySource,
                                  BmpResponseListener bmpResponseListener,
                                  TrustSource trustSource) {
        MitmManager mitmManager = createMitmManager(bmp, certificateAndKeySource, trustSource);
        bmp.setMitmManager(mitmManager);
        HostRewriter hostRewriter = HostRewriter.from(httpsHostRewriteDestination);
        bmp.addFirstHttpFilterFactory(createFirstFiltersSource(responseManufacturer, hostRewriter, bmpResponseListener, createPassthruPredicate()));
    }

    /* package */ ResponseManufacturingFiltersSource createFirstFiltersSource(BmpResponseManufacturer.WithState<?> responseManufacturer, HostRewriter hostRewriter, BmpResponseListener bmpResponseListener, PassthruPredicate passthruPredicate) {
        return new ResponseManufacturingFiltersSource(responseManufacturer, hostRewriter, bmpResponseListener, passthruPredicate);
    }

    static class BrowsermobVhsControl implements VirtualHarServerControl {

        private final BrowserUpProxy proxy;
        private final ImmutableList<Closeable> closeables;

        BrowsermobVhsControl(BrowserUpProxy proxy, Iterable<Closeable> closeables) {
            this.proxy = requireNonNull(proxy);
            this.closeables = ImmutableList.copyOf(closeables);
        }

        @Override
        public final HostAndPort getSocketAddress() {
            return HostAndPort.fromParts("localhost", proxy.getPort());
        }

        @Override
        public void close() throws IOException {
            closeAll(closeables, true);
            proxy.stop();
        }
    }

    private static final Set<CaptureType> ALL_CAPTURE_TYPES = EnumSet.allOf(CaptureType.class);

    protected Set<CaptureType> getCaptureTypes() {
        return ALL_CAPTURE_TYPES;
    }

}
