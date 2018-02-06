package com.github.mike10004.harreplay;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Class that represents a manager of a server replay process.
 */
public class ReplayManager {

    private final ReplayManagerConfig replayManagerConfig;

    /**
     * Constructs a new instance.
     * @param replayManagerConfig configuration
     */
    public ReplayManager(ReplayManagerConfig replayManagerConfig) {
        this.replayManagerConfig = checkNotNull(replayManagerConfig);
    }

    private void writeConfig(ServerReplayConfig config, CharSink sink) throws IOException {
        try (Writer out = sink.openStream()) {
            new Gson().toJson(config, out);
        }
    }

    /**
     * Starts a server replay process on a separate thread. Writes out several configuration files first.
     * @param processTracker the process tracker to use for launch
     * @param sessionConfig the session configuration
     * @return a future representing the server replay process
     * @throws IOException if an I/O error occurs
     */
    public ProcessMonitor<File, File> startAsync(ProcessTracker processTracker, ReplaySessionConfig sessionConfig) throws IOException {
        if (!sessionConfig.harFile.isFile()) {
            throw new FileNotFoundException(sessionConfig.harFile.getAbsolutePath());
        }
        checkHarFile(sessionConfig.harFile);
        Path serverReplayDir = replayManagerConfig.harReplayProxyDirProvider.provide(sessionConfig.scratchDir);
        File configJsonFile = File.createTempFile("server-replay-config", ".json", sessionConfig.scratchDir.toFile());
        writeConfig(sessionConfig.serverReplayConfig, Files.asCharSink(configJsonFile, UTF_8));
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
        Executor directExecutor = MoreExecutors.directExecutor();
        ListenableFuture<ProcessResult<File, File>> future = monitor.future();
        for (FutureCallback<? super ProcessResult<File, File>> terminationCallback : sessionConfig.serverTerminationCallbacks) {
            Futures.addCallback(monitor.future(), terminationCallback, directExecutor);
        }
        final Object listenLock = new Object();
        final AtomicBoolean heardListeningNotification = new AtomicBoolean(false);
        final Tailer listeningWatch = Tailer.create(stdoutFile, new TailerListenerAdapter(){
            @Override
            public void handle(String line) {
                boolean changed = heardListeningNotification.compareAndSet(false, isServerListeningNotificationLine(line));
                if (changed) {
                    synchronized (listenLock) {
                        listenLock.notifyAll();
                    }
                }
            }
        }, replayManagerConfig.serverReadinessPollIntervalMillis, false); // false => tail from beginning of file
        addTailers(sessionConfig.stdoutListeners, stdoutFile, future);
        addTailers(sessionConfig.stderrListeners, stderrFile, future);
        synchronized (listenLock) {
            long waitedMillis = 0;
            long waitingStart = System.currentTimeMillis();
            while (!heardListeningNotification.get()) {
                long remainingWait = replayManagerConfig.serverReadinessTimeoutMillis - waitedMillis;
                if (remainingWait > 0) {
                    try {
                        listenLock.wait(remainingWait);
                    } catch (InterruptedException e) {
                        LoggerFactory.getLogger(getClass()).info("interrupted while waiting", e);
                    }
                }
                waitedMillis = System.currentTimeMillis() - waitingStart;
            }
        }
        listeningWatch.stop();
        if (!heardListeningNotification.get()) {
            throw new ServerFailedToStartException("timed out while waiting for server to start");
        }
        return monitor;
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

        @Override
        public void onFailure(Throwable t) {
            tailer.stop();
        }
    }

}
