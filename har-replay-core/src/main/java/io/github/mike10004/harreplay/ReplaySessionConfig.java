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

    /**
     * Directory for temporary files that can be deleted after the session is complete.
     */
    public final Path scratchDir;

    /**
     * Port the proxy server should listen on.
     */
    @Nullable
    private final Integer port;

    /**
     * HAR file containing responses to be served.
     */
    public final File harFile;

    /**
     * Configuration of the replay server.
     */
    public final ReplayServerConfig replayServerConfig;

    /**
     * Callbacks invoked when the server stops.
     */
    public final ImmutableList<ServerTerminationCallback> serverTerminationCallbacks;

    private ReplaySessionConfig(Builder builder) {
        scratchDir = builder.scratchDir;
        port = builder.port;
        harFile = builder.harFile;
        replayServerConfig = builder.replayServerConfig;
        serverTerminationCallbacks = ImmutableList.copyOf(builder.serverTerminationCallbacks);
    }

    /**
     * Interface representing a callback invoked on termination of the replay server.
     */
    public interface ServerTerminationCallback {

        /**
         * Method invoked when the replay server terminates.
         * @param cause termination cause; null if not terminated due to exception
         */
        void terminated(@Nullable Throwable cause);

    }

    /**
     * Creates a builder instance that uses the system temp directory as the scratch directory.
     * @return the builder
     * @throws IOException on I/O error
     */
    public static Builder usingTempDir() throws IOException {
        File systemTempDir = FileUtils.getTempDirectory();
        return usingNewTempDirUnder(systemTempDir.toPath());
    }

    /**
     * Creates a builder instance that uses a new temporary directory under a given
     * directory as the scratch directory.
     * @return the builder
     * @throws IOException on I/O error
     */
    public static Builder usingNewTempDirUnder(Path tempDir) throws IOException {
        Path child = java.nio.file.Files.createTempDirectory(tempDir, "ServerReplay");
        return builder(child);
    }

    /**
     * Creates a builder instance that uses the given directory as the scratch directory.
     * @return the builder
     */
    public static Builder builder(Path scratchDir) {
        return new Builder(scratchDir);
    }

    /**
     * Builder of replay session configuration instances.
     * @see ReplaySessionConfig
     */
    public static final class Builder {

        private final Path scratchDir;
        @Nullable
        private Integer port;
        private File harFile;
        private ReplayServerConfig replayServerConfig = ReplayServerConfig.empty();
        private final List<ServerTerminationCallback> serverTerminationCallbacks = new ArrayList<>();

        private Builder(Path scratchDir) {
            this.scratchDir = checkNotNull(scratchDir);
        }

        public Builder port(int port) {
            checkArgument(port > 0 && port < 65536);
            this.port = port;
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

    @Nullable
    Integer getPort() {
        return port;
    }
}
