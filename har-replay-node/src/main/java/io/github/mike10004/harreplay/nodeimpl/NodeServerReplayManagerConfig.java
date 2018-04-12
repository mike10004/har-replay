package io.github.mike10004.harreplay.nodeimpl;

import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import org.apache.commons.io.input.TailerListener;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class NodeServerReplayManagerConfig {

    public static final int DEFAULT_SERVER_READINESS_POLL_INTERVAL_MILLIS = 100;
    public static final int DEFAULT_SERVER_READINESS_TIMEOUT_MILLIS = 3000;

    private static final NodeServerReplayManagerConfig AUTO_CONFIG_INSTANCE = NodeServerReplayManagerConfig.builder().build();

    /**
     * Pathname of the Node executable. If null, the system path is queried for the executable.
     */
    @Nullable
    private final File nodeExecutable;

    /**
     * Client module directory provider. The client module directory must contain a
     * node_modules directory with the server-replay module code.
     */
    public final ResourceDirectoryProvider harReplayProxyDirProvider;

    /**
     * Length of the interval between polls for server readiness. The har-replay-proxy module
     * prints a message on stdout when the proxy server has started listening, and we poll
     * for that message by tailing the file containing standard output from the process.
     * This parameter defines how long to wait between polls, in milliseconds.
     * This is not currently configurable.
     */
    public final long serverReadinessPollIntervalMillis;

    /**
     * Length of time to wait for the server to become ready, in milliseconds.
     * This is not currently configurable, but should be.
     */
    public final int serverReadinessTimeoutMillis;

    public final ImmutableList<TailerFactory> stdoutListeners;
    public final ImmutableList<TailerFactory> stderrListeners;

    final ReadinessCheckEcho readinessCheckEcho;

    private NodeServerReplayManagerConfig(Builder builder) {
        nodeExecutable = builder.nodeExecutable;
        harReplayProxyDirProvider = builder.harReplayProxyDirProvider;
        stdoutListeners = ImmutableList.copyOf(builder.stdoutListeners);
        stderrListeners = ImmutableList.copyOf(builder.stderrListeners);
        serverReadinessPollIntervalMillis = builder.serverReadinessPollIntervalMillis;
        serverReadinessTimeoutMillis = builder.serverReadinessTimeoutMillis;
        readinessCheckEcho = builder.readinessCheckEcho;
    }

    interface ReadinessCheckEcho {
        void examinedLine(String line, boolean signalsReadiness);
    }

    /**
     * Constructs and returns a new builder.
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Constructs and returns a program builder configured to use whatever node executable
     * this configuration defines (or elects not to define).
     * @return the program builder
     */
    Subprocess.Builder makeProgramBuilder() {
        if (nodeExecutable == null) {
            return Subprocess.running("node");
        } else {
            return Subprocess.running(nodeExecutable);
        }
    }

    public interface ResourceDirectoryProvider {
        Path provide(Path scratchDir) throws IOException;
    }

    /**
     * Constructs a configuration with best-guess strategies for the fields. This is the default
     * configuration, so this returns the same config you would get by invoking
     * {@link NodeServerReplayManagerConfig.Builder#build()} on a new builder instance.
     * @return the configuration
     */
    public static NodeServerReplayManagerConfig auto() {
        return AUTO_CONFIG_INSTANCE;
    }

    static class EmbeddedClientDirProvider implements ResourceDirectoryProvider {

        static final String ZIP_ROOT = "har-replay-proxy";
        private static final String ZIP_RESOURCE_PATH = "/har-replay-proxy-0.0.1.zip";

        public static ResourceDirectoryProvider getInstance() {
            return instance;
        }

        private static final EmbeddedClientDirProvider instance = new EmbeddedClientDirProvider();

        protected URL getZipResource() throws FileNotFoundException {
            URL url = getClass().getResource(ZIP_RESOURCE_PATH);
            if (url == null) {
                throw new FileNotFoundException("classpath:" + ZIP_RESOURCE_PATH);
            }
            return url;
        }

        @Override
        public Path provide(Path scratchDir) throws IOException {
            File zipFile = File.createTempFile("har-replay-proxy", ".zip", scratchDir.toFile());
            ByteSource zipSrc = Resources.asByteSource(getZipResource());
            zipSrc.copyTo(Files.asByteSink(zipFile));
            Path parentDir = java.nio.file.Files.createTempDirectory(scratchDir, "har-replay-proxy-parent");
            try (ZipFile z = new ZipFile(zipFile)) {
                for (Iterator<? extends ZipEntry> it = Iterators.forEnumeration(z.entries()); it.hasNext();) {
                    ZipEntry entry = it.next();
                    if (!entry.isDirectory()) {
                        String name = entry.getName();
                        if (name.startsWith(File.separator)) {
                            throw new IOException("zip has malformed entry name " + StringUtils.abbreviate(name, 32));
                        }
                        Path entryFile = parentDir.resolve(name);
                        Files.createParentDirs(entryFile.toFile());
                        try (InputStream entryInput = z.getInputStream(entry)) {
                            java.nio.file.Files.copy(entryInput, entryFile);
                        }
                    }
                }
            }
            Path serverReplayClientDir = parentDir.resolve(ZIP_ROOT);
            return serverReplayClientDir;
        }
    }

    /**
     * Builder of replay manager configuration objects.
     * @see NodeServerReplayManagerConfig
     */
    public static final class Builder {
        @Nullable
        private File nodeExecutable = null;
        private ResourceDirectoryProvider harReplayProxyDirProvider = EmbeddedClientDirProvider.getInstance();
        private final List<TailerFactory> stdoutListeners = new ArrayList<>();
        private final List<TailerFactory> stderrListeners = new ArrayList<>();
        private long serverReadinessPollIntervalMillis = DEFAULT_SERVER_READINESS_POLL_INTERVAL_MILLIS;
        private int serverReadinessTimeoutMillis = DEFAULT_SERVER_READINESS_TIMEOUT_MILLIS;
        private ReadinessCheckEcho readinessCheckEcho = (line, result) -> {};

        private Builder() {
        }

        Builder readinessCheckEcho(ReadinessCheckEcho readinessCheckEcho) {
            this.readinessCheckEcho = requireNonNull(readinessCheckEcho);
            return this;
        }

        public Builder serverReadinessPolling(int timeoutMillis, int pollIntervalMillis) {
            checkArgument(pollIntervalMillis > 0, "pollIntervalMillis > 0 is required: %s", pollIntervalMillis);
            this.serverReadinessTimeoutMillis = timeoutMillis;
            this.serverReadinessPollIntervalMillis = pollIntervalMillis;
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder addStdoutListener(TailerFactory val) {
            stdoutListeners.add(requireNonNull(val));
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder addStderrListener(TailerFactory val) {
            stderrListeners.add(requireNonNull(val));
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder nodeExecutable(@Nullable File executableFile) {
            nodeExecutable = executableFile;
            if (executableFile != null) {
                checkArgument(executableFile.canExecute(), "not executable: %s", executableFile);
            }
            return this;
        }

        @SuppressWarnings("unused")
        public Builder harReplayProxyDirProvider(ResourceDirectoryProvider val) {
            harReplayProxyDirProvider = requireNonNull(val);
            return this;
        }

        public Builder addOutputEchoes() {
            addStdoutListener(config -> new PrintStreamTailerListener(System.out));
            addStderrListener(config -> new PrintStreamTailerListener(System.err));
            return this;
        }

        public NodeServerReplayManagerConfig build() {
            return new NodeServerReplayManagerConfig(this);
        }
    }

    private static class PrintStreamTailerListener extends TailerListenerAdapter {

        private final PrintStream destination;

        private PrintStreamTailerListener(PrintStream destination) {
            this.destination = requireNonNull(destination);
        }

        @Override
        public void handle(String line) {
            destination.println(line);
        }
    }

    public interface TailerFactory {

        TailerListener createTailer(ReplaySessionConfig sessionConfig);

    }
}
