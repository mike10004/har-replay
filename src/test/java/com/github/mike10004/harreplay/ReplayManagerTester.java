package com.github.mike10004.harreplay;

import com.github.mike10004.harreplay.ReplayManager.ProcessMonitorReplaySessionControl;
import com.github.mike10004.harreplay.ReplayManager.ReplaySessionControl;
import com.github.mike10004.harreplay.ReplaySessionConfig.ServerTerminationCallback;
import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.FutureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReplayManagerTester {

    private static final String SYSPROP_SERVER_REPLAY_HTTP_PORT = "server-replay.port"; // see pom.xml build-helper-plugin
    static final String SYSPROP_NODE_EXECUTABLE = "har-replay.node.executable";

    private final Path tempDir;
    private final File harFile;

    public ReplayManagerTester(Path tempDir, File harFile) {
        this.tempDir = tempDir;
        this.harFile = harFile;
    }

    public interface ReplayClient<T> {
        T useReplayServer(Path tempDir, HostAndPort proxy, ReplaySessionControl sessionControl) throws Exception;
    }

    protected ServerReplayConfig configureReplayModule() {
        return ServerReplayConfig.empty();
    }

    protected ReplayManagerConfig.Builder createReplayManagerConfigBuilder() {
        ReplayManagerConfig.Builder builder = ReplayManagerConfig.builder();
        String nodeExecutablePath = System.getProperty(SYSPROP_NODE_EXECUTABLE, "");
        if (!nodeExecutablePath.isEmpty()) {
            builder.nodeExecutable(new File(nodeExecutablePath));
        }
        return builder;
    }

    public <T> T exercise(ReplayClient<T> client, @Nullable Integer httpPort) throws Exception {
        ReplayManagerConfig replayManagerConfig = createReplayManagerConfigBuilder().build();
        ReplayManager replay = new ReplayManager(replayManagerConfig);
        ProgramInfoFutureCallback infoCallback = new ProgramInfoFutureCallback();
        ReplaySessionConfig.Builder rscb = ReplaySessionConfig.builder(tempDir)
                .config(configureReplayModule())
                .onTermination(infoCallback)
                .addOutputEchoes();
        if (httpPort != null) {
            rscb.port(httpPort);
        }
        ReplaySessionConfig sessionParams = rscb.build(harFile);
        HostAndPort proxy = HostAndPort.fromParts("localhost", sessionParams.port);
        System.out.format("exercise: proxy = %s%n", proxy);
        @SuppressWarnings("OptionalAssignedToNull")
        Optional<T> result = null;
        Exception exception = null;
        try (ReplaySessionControl sessionControl = replay.start(sessionParams)) {
            result = Optional.ofNullable(client.useReplayServer(tempDir, proxy, sessionControl));
            sessionControl.stop();
            ProcessMonitor<File, File> processMonitor = ((ProcessMonitorReplaySessionControl)sessionControl).getProcessMonitor();
            assertFalse("process still alive", processMonitor.process().isAlive());
            System.out.println("program future has finished");
        } catch (Exception e) {
            System.err.format("exercise() aborting abnormally due to exception%n");
            e.printStackTrace(System.err);
            exception = e;
        } finally {
            System.out.println("awaiting execution of process callback");
            infoCallback.await(5, TimeUnit.SECONDS);
        }
        assertTrue("callback executed", infoCallback.wasExecuted());
        assertNull("exception was thrown, probably from useReplayServer", exception);
        checkState(result != null, "result never set");
        return result.orElse(null);
    }

    private static class ProgramInfoFutureCallback implements ServerTerminationCallback {

        private final CountDownLatch latch = new CountDownLatch(1);

        public void await(long duration, TimeUnit unit) throws InterruptedException {
            latch.await(duration, unit);
        }

        public boolean wasExecuted() {
            return latch.getCount() == 0;
        }

        private static final int SIGKILL = 9, SIGINT = 15;
        private static final ImmutableSet<Integer> normalExitCodes = ImmutableSet.of(128 + SIGINT, 128 + SIGKILL);

        @Override
        public void terminated(int exitCode, ByteSource stdout, ByteSource stderr) {
            try {
                System.out.println("program finished: " + exitCode);
                if (!normalExitCodes.contains(exitCode)) {
                    try {
                        stdout.copyTo(System.out);
                        stderr.copyTo(System.err);
                    } catch (IOException e) {
                        e.printStackTrace(System.err);
                    }
                }
            } finally {
                latch.countDown();
            }
        }

    }

    private static final Logger log = LoggerFactory.getLogger(ReplayManagerTester.class);

    public static int findHttpPortToUse() throws IOException {
        return findPortToUse(SYSPROP_SERVER_REPLAY_HTTP_PORT);
    }

    private static int findPortToUse(String systemPropertyName) throws IOException {
        String portStr = System.getProperty(systemPropertyName);
        if (Strings.isNullOrEmpty(portStr)) { // probably running with IDE test runner, not Maven
            log.trace("unit test port not reserved by build process; will try to find open port");
            try (ServerSocket socket = new ServerSocket(0)) {
                int reservedPort = socket.getLocalPort();
                log.debug("found open port {} by opening socket %s%n", reservedPort, socket);
                return reservedPort;
            }
        } else {
            return Integer.parseInt(portStr);
        }
    }

}
