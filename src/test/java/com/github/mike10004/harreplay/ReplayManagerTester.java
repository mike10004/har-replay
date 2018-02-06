package com.github.mike10004.harreplay;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ScopedProcessTracker;
import com.google.common.base.Strings;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplayManagerTester {

    private static final String SYSPROP_SERVER_REPLAY_HTTP_PORT = "server-replay.port"; // see pom.xml build-helper-plugin

    private final Path tempDir;
    private final File harFile;

    public ReplayManagerTester(Path tempDir, File harFile) {
        this.tempDir = tempDir;
        this.harFile = harFile;
    }

    public interface ReplayClient<T> {
        T useReplayServer(Path tempDir, HostAndPort proxy, ProcessMonitor<?, ?> programFuture) throws Exception;
    }

    protected ServerReplayConfig configureReplayModule() {
        return ServerReplayConfig.empty();
    }

    public <T> T exercise(ReplayClient<T> client, @Nullable Integer httpPort) throws Exception {
        ReplayManagerConfig replayManagerConfig = ReplayManagerConfig.auto();
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
        T result;
        try (ScopedProcessTracker processTracker = new ScopedProcessTracker()){
            ProcessMonitor<File, File> processMonitor = replay.startAsync(processTracker, sessionParams);
            result = client.useReplayServer(tempDir, proxy, processMonitor);
            processMonitor.destructor().sendTermSignal().timeout(1000, TimeUnit.MILLISECONDS).kill().awaitKill();
            assertFalse("process still alive", processMonitor.process().isAlive());
            System.out.println("program future has finished");
        } catch (Exception e) {
            System.err.format("exercise() aborting abnormally due to %s%n", e.toString());
            throw e;
        } finally {
            infoCallback.await(5, TimeUnit.SECONDS);
        }
        assertTrue("callback executed", infoCallback.wasExecuted());
        return result;
    }

    private static class ProgramInfoFutureCallback implements FutureCallback<ProcessResult<File, File>> {

        private final CountDownLatch latch = new CountDownLatch(1);

        public void await(long duration, TimeUnit unit) throws InterruptedException {
            latch.await(duration, unit);
        }

        public boolean wasExecuted() {
            return latch.getCount() == 0;
        }

        @Override
        public final void onSuccess(ProcessResult<File, File> result) {
            try {
                System.out.println("program finished: " + result);
                if (result.exitCode() != 143) { // represents correct signal sent to kill process
                    try {
                        Files.asByteSource(result.content().stdout()).copyTo(System.out);
                        Files.asByteSource(result.content().stderr()).copyTo(System.err);
                    } catch (IOException e) {
                        e.printStackTrace(System.err);
                    }
                }
            } finally {
                always();
            }
        }

        private void always() {
            latch.countDown();
        }

        @Override
        public final void onFailure(Throwable t) {
            try {
                if (!(t instanceof java.util.concurrent.CancellationException)) {
                    System.err.println("program exited unexpectedly");
                    t.printStackTrace(System.err);
                } else {
                    System.out.println("program was cancelled");
                }
            } finally {
                always();
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
