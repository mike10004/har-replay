package com.github.mike10004.harreplay;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputFiles;
import com.github.mike10004.nativehelper.ProgramWithOutputFilesResult;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

public class NodeServerReplay {

    private final ReplayManagerConfig replayManagerConfig;

    public NodeServerReplay(ReplayManagerConfig replayManagerConfig) {
        this.replayManagerConfig = checkNotNull(replayManagerConfig);
    }

    private void writeConfig(ServerReplayConfig config, CharSink sink) throws IOException {
        try (Writer out = sink.openStream()) {
            new Gson().toJson(config, out);
        }
    }

    public ListenableFuture<ProgramWithOutputFilesResult> startAsync(ExecutorService executorService, ReplaySessionConfig session) throws IOException {
        File configJsonFile = File.createTempFile("server-replay-config", ".json", session.scratchDir.toFile());
        writeConfig(session.serverReplayConfig, Files.asCharSink(configJsonFile, UTF_8));
        File cliJsFile = replayManagerConfig.serverReplayDir.resolve("node_modules/server-replay/cli.js").toFile();
        File stdoutFile = File.createTempFile("server-replay-stdout", ".txt", session.scratchDir.toFile());
        File stderrFile = File.createTempFile("server-replay-stderr", ".txt", session.scratchDir.toFile());
        ProgramWithOutputFiles program = Program.running("node")
                .from(session.scratchDir.toFile())
                .arg(cliJsFile.getAbsolutePath())
                .args("--config", configJsonFile.getAbsolutePath())
                .args("--port", String.valueOf(session.port))
                .arg("--debug")
                .arg(session.harFile.getAbsolutePath())
                .outputToFiles(stdoutFile, stderrFile);
        Tailer stdoutTailer = org.apache.commons.io.input.Tailer.create(stdoutFile, new TailerListenerAdapter() {
            @Override
            public void handle(String line) {
                System.out.format("server-replay-stdout: %s%n", line);
            }
        });
        Tailer stderrTailer = org.apache.commons.io.input.Tailer.create(stderrFile, new TailerListenerAdapter() {
            @Override
            public void handle(String line) {
                System.out.format("server-replay-stderr: %s%n", line);
            }
        });
        ListenableFuture<ProgramWithOutputFilesResult> future = program.executeAsync(executorService);
        Futures.addCallback(future, new TailerStopper(stdoutTailer));
        Futures.addCallback(future, new TailerStopper(stderrTailer));
        return future;
    }

    private static class TailerStopper implements FutureCallback<Object> {

        private final Tailer tailer;

        private TailerStopper(Tailer tailer) {
            this.tailer = tailer;
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
