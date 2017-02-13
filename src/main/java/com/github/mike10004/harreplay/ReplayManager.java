package com.github.mike10004.harreplay;

import com.github.mike10004.nativehelper.ProgramWithOutputFiles;
import com.github.mike10004.nativehelper.ProgramWithOutputFilesResult;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

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
     * @param executorService the executor service to use
     * @param sessionConfig the session configuration
     * @return a future representing the server replay process
     * @throws IOException if an I/O error occurs
     */
    public ListenableFuture<ProgramWithOutputFilesResult> startAsync(ExecutorService executorService, ReplaySessionConfig sessionConfig) throws IOException {
        Path serverReplayDir = replayManagerConfig.serverReplayClientDirProvider.provide(sessionConfig.scratchDir);
        File configJsonFile = File.createTempFile("server-replay-config", ".json", sessionConfig.scratchDir.toFile());
        writeConfig(sessionConfig.serverReplayConfig, Files.asCharSink(configJsonFile, UTF_8));
        File cliJsFile = serverReplayDir.resolve("node_modules/server-replay/cli.js").toFile();
        File stdoutFile = File.createTempFile("server-replay-stdout", ".txt", sessionConfig.scratchDir.toFile());
        File stderrFile = File.createTempFile("server-replay-stderr", ".txt", sessionConfig.scratchDir.toFile());
        ProgramWithOutputFiles program = replayManagerConfig.makeProgramBuilder()
                .from(sessionConfig.scratchDir.toFile())
                .arg(cliJsFile.getAbsolutePath())
                .args("--config", configJsonFile.getAbsolutePath())
                .args("--port", String.valueOf(sessionConfig.port))
                .arg("--debug")
                .arg(sessionConfig.harFile.getAbsolutePath())
                .outputToFiles(stdoutFile, stderrFile);
        ListenableFuture<ProgramWithOutputFilesResult> future = program.executeAsync(executorService);
        addTailers(sessionConfig.stdoutListeners, stdoutFile, future);
        addTailers(sessionConfig.stderrListeners, stderrFile, future);
        pollUntilListening(HostAndPort.fromParts("localhost", sessionConfig.port), future);
        return future;
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

    /**
     * Polls until the server is actually listening. This usually takes less than 100ms.
     * The reason the server may not be listening after the process has been executed is that the
     * call to Node's HTTP.Server.listen is asynchronous. Therefore, our future is returned before
     * the server actually starts.
     *
     * <p>Node doesn't have (to my knowledge) a facility for synchronous waiting like Java's Object.wait() and
     * notify()</p>
     *
     * <p>A better solution would be for the server-replay module to provide listen() a callback
     * function that would print a unique client-provided string on standard output. By doing that,
     * the client could tail the stdout file to wait for the string to be printed.</p>
     * @param server the server
     * @param future the future; it will be checked for cancellation/doneness
     */
    private void pollUntilListening(HostAndPort server, Future<?> future) throws ServerFailedToStartException {
        int numPolls = 0;
        if (replayManagerConfig.serverReadinessMaxPolls == 0) {
            return;
        }
        while (numPolls < replayManagerConfig.serverReadinessMaxPolls) {
            if (future.isCancelled()) {
                throw new ServerFailedToStartException("server was terminated");
            }
            if (future.isDone()) {
                throw new ServerFailedToStartException("server was stopped unexpectedly");
            }
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                LoggerFactory.getLogger(getClass()).debug("confirmed server listening on {} by opening {}", server, socket);
                return;
            } catch (IOException e) {
                LoggerFactory.getLogger(getClass()).trace("could not make connection to {} due to {}", server, e.toString());
            }
            numPolls++;
            try {
                Thread.sleep(replayManagerConfig.serverReadinessPollIntervalMillis);
            } catch (InterruptedException e) {
                throw new ServerFailedToStartException(e);
            }
        }
        throw new ServerFailedToStartException(String.format("failed to start after polling %d times at intervals of %d milliseconds", numPolls, replayManagerConfig.serverReadinessPollIntervalMillis));
    }

    private void addTailers(Iterable<TailerListener> tailerListeners, File file, ListenableFuture<?> future) {
        for (TailerListener tailerListener : tailerListeners) {
            Tailer tailer = org.apache.commons.io.input.Tailer.create(file, tailerListener);
            Futures.addCallback(future, new TailerStopper(tailer));
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
