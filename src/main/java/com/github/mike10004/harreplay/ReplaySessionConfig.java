package com.github.mike10004.harreplay;

import com.github.mike10004.harreplay.ReplaySessionConfig.ServerTerminationCallback.SyntheticProgramExitException;
import com.github.mike10004.nativehelper.ProgramWithOutputFilesResult;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.TailerListener;
import org.apache.commons.io.input.TailerListenerAdapter;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Class that represents a configuration of a server replay session.
 */
public class ReplaySessionConfig {

    public final Path scratchDir;
    public final int port;
    public final File harFile;
    public final ServerReplayConfig serverReplayConfig;
    public final ImmutableList<TailerListener> stdoutListeners;
    public final ImmutableList<TailerListener> stderrListeners;
    public final ImmutableList<FutureCallback<? super ProgramWithOutputFilesResult>> serverTerminationCallbacks;

    private ReplaySessionConfig(Builder builder) {
        scratchDir = builder.scratchDir;
        port = builder.port;
        harFile = builder.harFile;
        serverReplayConfig = builder.serverReplayConfig;
        stdoutListeners = ImmutableList.copyOf(builder.stdoutListeners);
        stderrListeners = ImmutableList.copyOf(builder.stderrListeners);
        serverTerminationCallbacks = ImmutableList.copyOf(builder.serverTerminationCallbacks);
    }

    public interface ServerTerminationCallback {
        void terminated(Throwable error);

        class SyntheticProgramExitException extends Exception {

            public final int exitCode;

            public SyntheticProgramExitException(int exitCode) {
                super("exit code " + exitCode);
                this.exitCode = exitCode;
            }
        }
    }

    public static Builder usingTempDir() throws IOException {
        File systemTempDir = FileUtils.getTempDirectory();
        return usingNewTempDirUnder(systemTempDir.toPath());
    }

    public static Builder usingNewTempDirUnder(Path tempDir) throws IOException {
        Path child = java.nio.file.Files.createTempDirectory(tempDir, "ServerReplay");
        return builder(child);
    }

    public static Builder builder(Path scratchDir) {
        return new Builder(scratchDir);
    }

    public static final class Builder {

        public static final int DEFAULT_PORT = 49877;

        private final Path scratchDir;
        private int port = DEFAULT_PORT;
        private File harFile;
        private ServerReplayConfig serverReplayConfig = ServerReplayConfig.empty();
        private final List<TailerListener> stdoutListeners = new ArrayList<>();
        private final List<TailerListener> stderrListeners = new ArrayList<>();
        private final List<FutureCallback<? super ProgramWithOutputFilesResult>> serverTerminationCallbacks = new ArrayList<>();

        private Builder(Path scratchDir) {
            this.scratchDir = checkNotNull(scratchDir);
        }

        public Builder port(int val) {
            checkArgument(port > 0 && port < 65536);
            port = val;
            return this;
        }

        public Builder config(ServerReplayConfig serverReplayConfig) {
            this.serverReplayConfig = serverReplayConfig;
            return this;
        }

        public Builder addStdoutListener(TailerListener val) {
            stdoutListeners.add(val);
            return this;
        }

        public Builder addStderrListener(TailerListener val) {
            stderrListeners.add(val);
            return this;
        }

        public ReplaySessionConfig build(File harFile) {
            this.harFile = checkNotNull(harFile);
            return new ReplaySessionConfig(this);
        }

        public Builder addOutputEchoes() {
            return addStdoutListener(new PrintStreamTailerListener(System.out))
                    .addStderrListener(new PrintStreamTailerListener(System.err));
        }

        public Builder onTermination(FutureCallback<? super ProgramWithOutputFilesResult> callback) {
            serverTerminationCallbacks.add(callback);
            return this;
        }

        public Builder onTermination(ServerTerminationCallback terminationCallback) {
            return onTermination(new FutureCallback<ProgramWithOutputFilesResult>() {
                @Override
                public void onSuccess(ProgramWithOutputFilesResult result) {
                    terminationCallback.terminated(new SyntheticProgramExitException(result.getExitCode()));
                }

                @Override
                public void onFailure(Throwable t) {
                    if (!(t instanceof java.util.concurrent.CancellationException)) {
                        terminationCallback.terminated(t);
                    }
                }
            });
        }

        private static class PrintStreamTailerListener extends TailerListenerAdapter {

            private final PrintStream destination;

            private PrintStreamTailerListener(PrintStream destination) {
                this.destination = checkNotNull(destination);
            }

            @Override
            public void handle(String line) {
                destination.println(line);
            }
        }
    }

}
