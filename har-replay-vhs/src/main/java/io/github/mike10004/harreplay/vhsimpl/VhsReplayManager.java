package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.net.HostAndPort;
import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import io.github.mike10004.harreplay.ReplaySessionControl;
import io.github.mike10004.harreplay.ReplaySessions;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.bmp.BmpResponseManufacturer;
import io.github.mike10004.vhs.bmp.BrowsermobVhsConfig;
import io.github.mike10004.vhs.bmp.BrowsermobVirtualHarServer;
import io.github.mike10004.vhs.bmp.KeystoreData;
import io.github.mike10004.vhs.bmp.NanohttpdTlsEndpointFactory;
import io.github.mike10004.vhs.bmp.ScratchDirProvider;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

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
        BmpResponseManufacturer responseManufacturer = config.responseManufacturerProvider.create(sessionConfig.harFile,
                sessionConfig.replayServerConfig);
        int port = ReplaySessions.getPortOrFindOpenPort(sessionConfig);
        VirtualHarServer vhs = createVirtualHarServer(port, sessionConfig.scratchDir, responseManufacturer);
        VirtualHarServerControl ctrl = vhs.start();
        Runnable stopListener = () -> {
            sessionConfig.serverTerminationCallbacks.forEach(c -> {
                c.terminated(null);
            });
        };
        return new VhsReplaySessionControl(ctrl, true, stopListener);
    }

    protected VirtualHarServer createVirtualHarServer(int port, Path scratchParentDir, BmpResponseManufacturer responseManufacturer) throws IOException {
        try {
            KeystoreData keystoreData = config.keystoreGenerator.generate("localhost");
            BrowsermobVhsConfig.Builder configBuilder = BrowsermobVhsConfig.builder(responseManufacturer)
                    .port(port)
                    .tlsEndpointFactory(NanohttpdTlsEndpointFactory.create(keystoreData, null))
                    .scratchDirProvider(ScratchDirProvider.under(scratchParentDir));
            BrowsermobVhsConfig config = configBuilder.build();
            return new BrowsermobVirtualHarServer(config);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
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
