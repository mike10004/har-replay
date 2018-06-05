package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.net.HostAndPort;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.ReplayServerConfig.Replacement;
import io.github.mike10004.harreplay.ReplayServerConfig.ResponseHeaderTransform;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import io.github.mike10004.harreplay.ReplaySessionControl;
import io.github.mike10004.vhs.BasicHeuristic;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.HarResponseEncoderFactory;
import io.github.mike10004.vhs.HeuristicEntryMatcher;
import io.github.mike10004.vhs.ResponseInterceptor;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.bmp.BmpResponseListener;
import io.github.mike10004.vhs.bmp.BmpResponseManufacturer;
import io.github.mike10004.vhs.bmp.BrowsermobVhsConfig;
import io.github.mike10004.vhs.bmp.BrowsermobVirtualHarServer;
import io.github.mike10004.vhs.bmp.HarReplayManufacturer;
import io.github.mike10004.vhs.bmp.KeystoreData;
import io.github.mike10004.vhs.bmp.NanohttpdTlsEndpointFactory;
import io.github.mike10004.vhs.bmp.ScratchDirProvider;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class VhsReplayManager implements ReplayManager {

    private VhsReplayManagerConfig config;
    private final EntryMatcherFactory entryMatcherFactory;

    public VhsReplayManager() {
        this(VhsReplayManagerConfig.getDefault());
    }

    public VhsReplayManager(VhsReplayManagerConfig config) {
        this(config, HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE));
    }

    protected VhsReplayManager(VhsReplayManagerConfig config, EntryMatcherFactory entryMatcherFactory) {
        this.config = requireNonNull(config, "config");
        this.entryMatcherFactory = requireNonNull(entryMatcherFactory, "entryMatcherFactory");
    }

    protected EntryParser<HarEntry> createHarEntryParser() {
        HarResponseEncoderFactory<HarEntry> responseEncoderFactory = HarResponseEncoderFactory.alwaysIdentityEncoding();
        return new HarBridgeEntryParser<>(new SstoehrHarBridge(), responseEncoderFactory);
    }

    protected EntryMatcher buildHarEntryMatcher(ReplaySessionConfig sessionConfig) throws IOException {
        HarReader harReader = config.harReaderFactory.createReader();
        List<HarEntry> entries;
        try {
            entries = harReader.readFromFile(sessionConfig.harFile).getLog().getEntries();
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
        EntryParser<HarEntry> parser = createHarEntryParser();
        EntryMatcher harEntryMatcher = entryMatcherFactory.createEntryMatcher(entries, parser);
        return harEntryMatcher;
    }

    @Override
    public ReplaySessionControl start(ReplaySessionConfig sessionConfig) throws IOException {
        EntryMatcher harEntryMatcher = buildHarEntryMatcher(sessionConfig);
        EntryMatcher compositeEntryMatcher = enhanceEntryMatcherFromConfig(harEntryMatcher, sessionConfig.replayServerConfig);
        List<ResponseInterceptor> interceptors = new ArrayList<>();
        interceptors.addAll(buildInterceptorsForReplacements(sessionConfig.replayServerConfig.replacements));
        interceptors.addAll(buildInterceptorsForTransforms(sessionConfig.replayServerConfig.responseHeaderTransforms));
        int port = sessionConfig.port;
        VirtualHarServer vhs = createVirtualHarServer(port, sessionConfig.scratchDir, compositeEntryMatcher, interceptors, config.bmpResponseListener);
        VirtualHarServerControl ctrl = vhs.start();
        Runnable stopListener = () -> {
            sessionConfig.serverTerminationCallbacks.forEach(c -> {
                c.terminated(null);
            });
        };
        return new VhsReplaySessionControl(ctrl, true, stopListener);
    }

    protected BmpResponseManufacturer createResponseManufacturer(EntryMatcher entryMatcher, Iterable<ResponseInterceptor> responseInterceptors) {
        return new HarReplayManufacturer(entryMatcher, responseInterceptors);
    }

    protected VirtualHarServer createVirtualHarServer(int port, Path scratchParentDir, EntryMatcher entryMatcher, Iterable<ResponseInterceptor> responseInterceptors, BmpResponseListener bmpResponseListener) throws IOException {
        try {
            KeystoreData keystoreData = config.keystoreGenerator.generate("localhost");
            BmpResponseManufacturer responseManufacturer = createResponseManufacturer(entryMatcher, responseInterceptors);
            BrowsermobVhsConfig.Builder configBuilder = BrowsermobVhsConfig.builder(responseManufacturer)
                    .port(port)
                    .responseListener(bmpResponseListener)
                    .tlsEndpointFactory(NanohttpdTlsEndpointFactory.create(keystoreData, null))
                    .scratchDirProvider(ScratchDirProvider.under(scratchParentDir));
            BrowsermobVhsConfig config = configBuilder.build();
            return new BrowsermobVirtualHarServer(config);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    protected List<ResponseInterceptor> buildInterceptorsForReplacements(Collection<Replacement> replacements) {
        return replacements.stream().map(replacement ->  new ReplacingInterceptor(config, replacement)).collect(Collectors.toList());
    }

    protected List<ResponseInterceptor> buildInterceptorsForTransforms(Collection<ResponseHeaderTransform> headerTransforms) {
        return headerTransforms.stream().map(headerTransform -> new HeaderTransformInterceptor(config, headerTransform)).collect(Collectors.toList());
    }

    protected EntryMatcher enhanceEntryMatcherFromConfig(EntryMatcher harEntryMatcher, ReplayServerConfig serverConfig) {
        MappingEntryMatcher mappingEntryMatcher = new MappingEntryMatcher(serverConfig.mappings, config.mappedFileResolutionRoot);
        return new CompositeEntryMatcher(Arrays.asList(mappingEntryMatcher, harEntryMatcher));
    }

    private static class VhsReplaySessionControl implements ReplaySessionControl {

        private final VirtualHarServerControl ctrl;
        private volatile boolean alive;
        private final Runnable stopListener;

        private VhsReplaySessionControl(VirtualHarServerControl ctrl, boolean alive, Runnable stopListener) {
            this.ctrl = ctrl;
            this.alive = alive;
            this.stopListener = stopListener;
        }

        @Override
        public HostAndPort getSocketAddress() {
            return ctrl.getSocketAddress();
        }

        @Override
        public int getListeningPort() {
            return ctrl.getSocketAddress().getPort();
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public void stop() {
            try {
                ctrl.close();
            } catch (IOException e) {
                LoggerFactory.getLogger(VhsReplaySessionControl.class).warn("close() failed", e);
            } finally {
                alive = false;
                stopListener.run();
            }
        }
    }
}
