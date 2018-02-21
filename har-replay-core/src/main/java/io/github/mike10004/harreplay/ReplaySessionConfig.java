package io.github.mike10004.harreplay;

import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;

/**
 * Class that represents a configuration of a server replay session.
 */
public class ReplaySessionConfig {

    public final Path scratchDir;
    public final int port;
    public final File harFile;
    public final ReplayServerConfig replayServerConfig;
    public final ImmutableList<ServerTerminationCallback> serverTerminationCallbacks;

    private ReplaySessionConfig(Builder builder) {
        scratchDir = builder.scratchDir;
        port = builder.port;
        harFile = builder.harFile;
        replayServerConfig = builder.replayServerConfig;
        serverTerminationCallbacks = ImmutableList.copyOf(builder.serverTerminationCallbacks);
    }

    public interface ServerTerminationCallback {

        void terminated(@Nullable Throwable cause);

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
        private ReplayServerConfig replayServerConfig = ReplayServerConfig.empty();
        private final List<ServerTerminationCallback> serverTerminationCallbacks = new ArrayList<>();

        private Builder(Path scratchDir) {
            this.scratchDir = checkNotNull(scratchDir);
        }

        public Builder port(int val) {
            checkArgument(port > 0 && port < 65536);
            port = val;
            return this;
        }

        public Builder config(ReplayServerConfig replayServerConfig) {
            this.replayServerConfig = requireNonNull(replayServerConfig);
            return this;
        }

        public ReplaySessionConfig build(File harFile) {
            this.harFile = checkNotNull(harFile);
            return new ReplaySessionConfig(this);
        }

        public Builder onTermination(ServerTerminationCallback terminationCallback) {
            serverTerminationCallbacks.add(terminationCallback);
            return this;
        }

    }

}
