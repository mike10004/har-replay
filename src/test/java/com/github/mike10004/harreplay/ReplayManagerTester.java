package com.github.mike10004.harreplay;

import com.github.mike10004.nativehelper.ProgramWithOutputFilesResult;
import com.github.mike10004.nativehelper.ProgramWithOutputResult;
import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ReplayManagerTester {

    private static final String SYSPROP_SERVER_REPLAY_HTTP_PORT = "server-replay.http.port"; // see pom.xml build-helper-plugin

    private final Path tempDir;
    private final File harFile;

    public ReplayManagerTester(Path tempDir, File harFile) {
        this.tempDir = tempDir;
        this.harFile = harFile;
    }

    public interface ReplayClient<T> {
        T useReplayServer(Path tempDir, HostAndPort proxy, Future<?> programFuture) throws Exception;
    }

    protected ServerReplayConfig configureReplayModule() {
        return ServerReplayConfig.empty();
    }

    public <T> T exercise(ReplayClient<T> client, @Nullable Integer httpPort) throws Exception {
        ReplayManagerConfig replayManagerConfig = ReplayManagerConfig.auto();
        ReplayManager replay = new ReplayManager(replayManagerConfig);
        ReplaySessionConfig.Builder rscb = ReplaySessionConfig.builder(tempDir)
                .config(configureReplayModule())
                .onTermination(new ProgramInfoFutureCallback())
                .addOutputEchoes();
        if (httpPort != null) {
            rscb.port(httpPort);
        }
        ReplaySessionConfig sessionParams = rscb.build(harFile);
        HostAndPort proxy = HostAndPort.fromParts("localhost", sessionParams.port);
        System.out.format("exercise: proxy = %s%n", proxy);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        T result = null;
        boolean executed = false;
        try {
            ListenableFuture<ProgramWithOutputFilesResult> programFuture = replay.startAsync(executorService, sessionParams);
            if (!programFuture.isDone() && !programFuture.isCancelled()) {
                result = client.useReplayServer(tempDir, proxy, programFuture);
                executed = true;
            }
            programFuture.cancel(true);
            if (!programFuture.isDone()) {
                try {
                    programFuture.get(3, TimeUnit.SECONDS);
                } catch (java.util.concurrent.CancellationException ignore) {
                }
            }
            System.out.println("program future has finished");
        } catch (Exception e) {
            System.err.format("exercise() aborting abnormally due to %s%n", e.toString());
            throw e;
        } finally {
            if (!executorService.isShutdown()) {
                System.out.println("shutting down");
                executorService.shutdownNow();
                System.out.println("finished shutdown");
            }
        }
        if (!executed) {
            throw new NeverExecutedException();
        }
        return result;
    }

    private static class NeverExecutedException extends Exception {
        public NeverExecutedException() {
            super("client never used replay server because process finished before it could");
        }
    }

    private static class ProgramInfoFutureCallback implements FutureCallback<ProgramWithOutputResult> {
        @Override
        public void onSuccess(ProgramWithOutputResult result) {
            System.out.println("program finished: " + result);
            if (result.getExitCode() != 0) {
                try {
                    result.getStdout().copyTo(System.out);
                    result.getStderr().copyTo(System.err);
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                }
            }
        }

        @Override
        public void onFailure(Throwable t) {
            if (!(t instanceof java.util.concurrent.CancellationException)) {
                System.err.println("program exited unexpectedly");
                t.printStackTrace(System.err);
            } else {
                System.out.println("program was cancelled");
            }
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ReplayManagerTester.class);

    public static int findHttpPortToUse() throws IOException {
        return findPortToUse(SYSPROP_SERVER_REPLAY_HTTP_PORT);
    }

    public static class Https {

        private Https() {}

        private static File getResourceAsFile(String resourcePath) throws IOException {
            URL url = Https.class.getResource(resourcePath);
            if (url == null) {
                throw new FileNotFoundException("classpath:" + resourcePath);
            }
            try {
                return new File(url.toURI());
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        }

        public static File getTestKeyFile() throws IOException {
            return getResourceAsFile("/snakeoil/har-replay-key.pem");
        }

        public static File getTestCertificateFile() throws IOException {
            return getResourceAsFile("/snakeoil/har-replay-cert.pem");
        }

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

    public static File getHttpsExampleFile() {
        try {
            return new File(ReplayManagerTester.class.getResource("/https.www.example.com.har").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public static File getHttpExampleFile() {
        try {
            return new File(ReplayManagerTester.class.getResource("/http.www.example.com.har").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String getHttpExamplePageTitle() {
        return "ABCDEFG Domain";
    }

    public static String getHttpsExamplePageTitle() {
        return "Example Abcdef";
    }

}
