package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.net.HostAndPort;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.ReplayServerConfig.Replacement;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import io.github.mike10004.harreplay.ReplaySessionControl;
import io.github.mike10004.vhs.BasicHeuristic;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.HeuristicEntryMatcher;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import io.github.mike10004.vhs.nanohttpd.NanochampVirtualHarServer;
import io.github.mike10004.vhs.nanohttpd.ReplayingRequestHandler;
import io.github.mike10004.vhs.nanohttpd.ResponseInterceptor;
import io.github.mike10004.vhs.nanohttpd.ResponseManager;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class VhsReplayManager implements ReplayManager {

    private VhsReplayManagerConfig config;

    public VhsReplayManager() {
        this(VhsReplayManagerConfig.getDefault());
    }

    public VhsReplayManager(VhsReplayManagerConfig config) {
        this.config = requireNonNull(config, "config");
    }

    @Override
    public ReplaySessionControl start(ReplaySessionConfig sessionConfig) throws IOException {
        List<HarEntry> entries;
        try {
            entries = new de.sstoehr.harreader.HarReader().readFromFile(sessionConfig.harFile).getLog().getEntries();
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
        // TODO apply sessionConfig.replayServerConfig to entry matching and such
        EntryParser<HarEntry> parser = new HarBridgeEntryParser<>(new SstoehrHarBridge());
        EntryMatcherFactory entryMatcherFactory = HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
        EntryMatcher harEntryMatcher = entryMatcherFactory.createEntryMatcher(entries, parser);
        EntryMatcher compositeEntryMatcher = buildEntryMatcher(harEntryMatcher, sessionConfig.replayServerConfig);
        List<ResponseInterceptor> interceptors = new ArrayList<>();
        //noinspection CollectionAddAllCanBeReplacedWithConstructor
        interceptors.addAll(buildInterceptors(sessionConfig.replayServerConfig.replacements));
        ReplayingRequestHandler rh = new ReplayingRequestHandler(compositeEntryMatcher, interceptors, ResponseManager.identity());
        int port = sessionConfig.port;
        NanochampVirtualHarServer vhs = new NanochampVirtualHarServer(rh, port);
        VirtualHarServerControl ctrl = vhs.start();
        Runnable stopListener = () -> {
            sessionConfig.serverTerminationCallbacks.forEach(c -> {
                c.terminated(null);
            });
        };
        return new VhsReplaySessionControl(ctrl, true, stopListener);
    }

    protected List<ResponseInterceptor> buildInterceptors(Collection<Replacement> replacements) {
        return replacements.stream().map(ReplacingInterceptor::new).collect(Collectors.toList());
    }

    protected EntryMatcher buildEntryMatcher(EntryMatcher harEntryMatcher, ReplayServerConfig serverConfig) {
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
