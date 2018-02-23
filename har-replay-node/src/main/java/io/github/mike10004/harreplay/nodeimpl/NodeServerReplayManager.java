package io.github.mike10004.harreplay.nodeimpl;

import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import io.github.mike10004.harreplay.ReplaySessionConfig.ServerTerminationCallback;
import io.github.mike10004.harreplay.ReplaySessionControl;
import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ProcessStillAliveException;
import com.github.mike10004.nativehelper.subprocess.ProcessTracker;
import com.github.mike10004.nativehelper.subprocess.ScopedProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.Gson;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
import org.apache.commons.io.input.TailerListenerAdapter;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class NodeServerReplayManager implements ReplayManager {

    private final NodeServerReplayManagerConfig replayManagerConfig;

    /**
     * Constructs a new instance.
     * @param replayManagerConfig configuration
     */
    public NodeServerReplayManager(NodeServerReplayManagerConfig replayManagerConfig) {
        this.replayManagerConfig = checkNotNull(replayManagerConfig);
    }

    private void writeConfig(ReplayServerConfig config, CharSink sink) throws IOException {
        try (Writer out = sink.openStream()) {
            new Gson().toJson(config, out);
        }
    }

    private static class ScopedProcessTrackerSessionControl implements ReplaySessionControl {

        private static final long SIGINT_TIMEOUT_MILLIS = 500;
        private static final long SIGTERM_TIMEOUT_MILLIS = 500;

        private final ScopedProcessTracker processTracker;
        private final ProcessMonitor<File, File> processMonitor;
        private final int port;

        protected ScopedProcessTrackerSessionControl(ScopedProcessTracker processTracker, ProcessMonitor<File, File> processMonitor, int port) {
            this.processTracker = requireNonNull(processTracker);
            this.processMonitor = requireNonNull(processMonitor);
            this.port = port;
        }

        private ProcessMonitor<File, File> getProcessMonitor() {
            return processMonitor;
        }

        @Override
        public int getListeningPort() {
            return port;
        }

        @Override
        public void stop() throws ProcessStillAliveException {
            getProcessMonitor().destructor()
                    .sendTermSignal().timeout(SIGINT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    .kill().timeoutOrThrow(SIGTERM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        @Override
        public boolean isAlive() {
            return getProcessMonitor().process().isAlive();
        }

        @Override
        public void close() throws IOException {
            try {
                stop();
            } catch (ProcessStillAliveException e) {
                throw new IOException("failed to kill server process", e);
            } finally {
                processTracker.close();
            }
        }
    }

    /**
     * Starts a server replay process on a separate thread. Writes out several configuration files first.
     * @param sessionConfig the session configuration
     * @return a future representing the server replay process
     * @throws IOException if an I/O error occurs
     */
    @Override
    public ReplaySessionControl start(ReplaySessionConfig sessionConfig) throws IOException {
        ScopedProcessTracker processTracker = new ScopedProcessTracker();
        ProcessMonitor<File, File> processMonitor = startAsyncWithTracker(processTracker, sessionConfig);
        return new ScopedProcessTrackerSessionControl(processTracker, processMonitor, sessionConfig.port);
    }

    /**
     * Starts a server replay process using the given process tracker.
     * @param processTracker the process tracker
     * @param sessionConfig the session configuration
     * @return a process monitor for the server process
     * @throws IOException if an I/O error occurs
     */
    protected ProcessMonitor<File, File> startAsyncWithTracker(ProcessTracker processTracker, ReplaySessionConfig sessionConfig) throws IOException {
        if (!sessionConfig.harFile.isFile()) {
            throw new FileNotFoundException(sessionConfig.harFile.getAbsolutePath());
        }
        checkHarFile(sessionConfig.harFile);
        Path serverReplayDir = replayManagerConfig.harReplayProxyDirProvider.provide(sessionConfig.scratchDir);
        File configJsonFile = File.createTempFile("server-replay-config", ".json", sessionConfig.scratchDir.toFile());
        writeConfig(sessionConfig.replayServerConfig, Files.asCharSink(configJsonFile, UTF_8));
        File cliJsFile = serverReplayDir.resolve("cli.js").toFile();
        File stdoutFile = File.createTempFile("server-replay-stdout", ".txt", sessionConfig.scratchDir.toFile());
        File stderrFile = File.createTempFile("server-replay-stderr", ".txt", sessionConfig.scratchDir.toFile());
        Subprocess program = replayManagerConfig.makeProgramBuilder()
                .from(sessionConfig.scratchDir.toFile())
                .arg(cliJsFile.getAbsolutePath())
                .args("--config", configJsonFile.getAbsolutePath())
                .args("--port", String.valueOf(sessionConfig.port))
                .arg("--debug")
                .arg(sessionConfig.harFile.getAbsolutePath())
                .build();
        ProcessMonitor<File, File> monitor = program.launcher(processTracker)
                .outputFiles(stdoutFile, stderrFile)
                .launch();
        Executor directExecutor = getTerminationCallbackExecutor();
        ListenableFuture<ProcessResult<File, File>> future = monitor.future();
        for (ServerTerminationCallback terminationCallback : sessionConfig.serverTerminationCallbacks) {
            Futures.addCallback(monitor.future(), new TerminationCallbackWrapper(terminationCallback), directExecutor);
        }
        final CountDownLatch listeningLatch = new CountDownLatch(1);
        final Tailer listeningWatch = Tailer.create(stdoutFile, new TailerListenerAdapter(){
            @Override
            public void handle(String line) {
                boolean listeningNotificationLine = isServerListeningNotificationLine(line);
                if (listeningNotificationLine) {
                    listeningLatch.countDown();
                }
            }
        }, replayManagerConfig.serverReadinessPollIntervalMillis, false); // false => tail from beginning of file
        List<TailerListener> stdoutListeners = replayManagerConfig.stdoutListeners.stream()
                .map(factory -> factory.createTailer(sessionConfig))
                .collect(Collectors.toList());
        List<TailerListener> stderrListeners = replayManagerConfig.stderrListeners.stream()
                .map(factory -> factory.createTailer(sessionConfig))
                .collect(Collectors.toList());
        addTailers(stdoutListeners, stdoutFile, future);
        addTailers(stderrListeners, stderrFile, future);
        boolean foundListeningLine = Uninterruptibles.awaitUninterruptibly(listeningLatch, replayManagerConfig.serverReadinessTimeoutMillis, TimeUnit.MILLISECONDS);
        listeningWatch.stop();
        if (!foundListeningLine) {
            throw new ServerFailedToStartException("timed out while waiting for server to start");
        }
        return monitor;
    }

    protected Executor getTerminationCallbackExecutor() {
        return MoreExecutors.directExecutor();
    }

    static boolean isServerListeningNotificationLine(String line) {
        return line.matches("^har-replay-proxy: Listening on localhost:\\d+$");
    }

    private void checkHarFile(File harFile) throws IOException {
        if (harFile.length() == 0) {
            throw new IOException("har file malformed; length = 0");
        }
    }

    @SuppressWarnings("unused")
    private static class ServerFailedToStartException extends IOException {

        public ServerFailedToStartException() {
        }

        public ServerFailedToStartException(String message) {
            super(message);
        }

        public ServerFailedToStartException(String message, Throwable cause) {
            super(message, cause);
        }

        public ServerFailedToStartException(Throwable cause) {
            super(cause);
        }
    }

    private void addTailers(Iterable<TailerListener> tailerListeners, File file, ListenableFuture<?> future) {
        Executor directExecutor = MoreExecutors.directExecutor();
        for (TailerListener tailerListener : tailerListeners) {
            Tailer tailer = org.apache.commons.io.input.Tailer.create(file, tailerListener);
            Futures.addCallback(future, new TailerStopper(tailer), directExecutor);
        }
    }

    private static class TailerStopper implements FutureCallback<Object> {

        private final Tailer tailer;

        private TailerStopper(Tailer tailer) {
            this.tailer = checkNotNull(tailer);
        }

        @Override
        public void onSuccess(@Nullable Object result) {
            tailer.stop();
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public void onFailure(Throwable t) {
            tailer.stop();
        }
    }

    private static class TerminationCallbackWrapper implements FutureCallback<ProcessResult<File, File>> {

        private final ServerTerminationCallback wrapped;

        private TerminationCallbackWrapper(ServerTerminationCallback wrapped) {
            this.wrapped = requireNonNull(wrapped);
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public void onSuccess(ProcessResult<File, File> result) {
            wrapped.terminated(null);
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public void onFailure(Throwable t) {
            wrapped.terminated(t);
        }
    }
}
