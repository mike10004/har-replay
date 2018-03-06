package io.github.mike10004.harreplay.tests;

import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import io.github.mike10004.harreplay.ReplaySessionConfig.Builder;
import io.github.mike10004.harreplay.ReplaySessionConfig.ServerTerminationCallback;
import io.github.mike10004.harreplay.ReplaySessionControl;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HostAndPort;
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

public abstract class ReplayManagerTester {

    private final Path tempDir;
    private final File harFile;

    public ReplayManagerTester(Path tempDir, File harFile) {
        this.tempDir = tempDir;
        this.harFile = harFile;
    }

    public interface ReplayClient<T> {
        T useReplayServer(Path tempDir, HostAndPort proxy, ReplaySessionControl sessionControl) throws Exception;
    }

    protected ReplayServerConfig configureReplayModule() {
        return ReplayServerConfig.empty();
    }

    protected abstract ReplayManager createReplayManager();

    public <T> T exercise(ReplayClient<T> client, @Nullable Integer httpPort) throws Exception {

        ReplayManager replay = createReplayManager();
        AwaitableServerTerminationCallback infoCallback = new AwaitableServerTerminationCallback();
        Builder rscb = ReplaySessionConfig.builder(tempDir)
                .config(configureReplayModule())
                .onTermination(infoCallback);
        if (httpPort != null) {
            rscb.port(httpPort);
        }
        ReplaySessionConfig sessionParams = rscb.build(harFile);
        HostAndPort proxy = HostAndPort.fromParts("localhost", sessionParams.port);
        System.out.format("exercise: proxy = %s%n", proxy);
        @SuppressWarnings("OptionalAssignedToNull")
        Optional<T> result = null;
        Exception exception = null;
        ReplaySessionControl sessionControlCopy = null;
        boolean infoCallbackAwaitSucceeded;
        try (ReplaySessionControl sessionControl = replay.start(sessionParams)) {
            sessionControlCopy = sessionControl;
            result = Optional.ofNullable(client.useReplayServer(tempDir, proxy, sessionControl));
        } catch (Exception e) {
            System.err.format("exercise() aborting abnormally due to exception%n");
            e.printStackTrace(System.err);
            exception = e;
        } finally {
            if (sessionControlCopy != null) {
                assertFalse("process still alive", sessionControlCopy.isAlive());
            }
            System.out.println("awaiting execution of process callback");
            infoCallbackAwaitSucceeded = infoCallback.await(3, TimeUnit.SECONDS);
        }
        assertTrue("callback executed", infoCallback.wasExecuted());
        assertTrue("infoCallbackAwaitSucceeded", infoCallbackAwaitSucceeded);
        if (exception != null) {
            exception.printStackTrace(System.err);
        }
        assertNull("exception was thrown, probably from useReplayServer", exception);
        checkState(result != null, "result never set");
        return result.orElse(null);
    }

    private static class AwaitableServerTerminationCallback implements ServerTerminationCallback {

        private final CountDownLatch latch = new CountDownLatch(1);

        public boolean await(long duration, TimeUnit unit) throws InterruptedException {
            return latch.await(duration, unit);
        }

        public boolean wasExecuted() {
            return latch.getCount() == 0;
        }

        private static final int SIGKILL = 9, SIGINT = 15;
        private static final ImmutableSet<Integer> normalExitCodes = ImmutableSet.of(128 + SIGINT, 128 + SIGKILL);

        @Override
        public void terminated(@Nullable Throwable cause) {
            try {
                System.out.println("program finished: " + cause);
            } finally {
                latch.countDown();
            }
        }

    }

    private static final Logger log = LoggerFactory.getLogger(ReplayManagerTester.class);

    public static int findOpenPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public static int findReservedPort(String systemPropertyName) throws IOException {
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
