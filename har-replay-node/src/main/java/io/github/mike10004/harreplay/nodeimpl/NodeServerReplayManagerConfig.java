package io.github.mike10004.harreplay.nodeimpl;

import io.github.mike10004.harreplay.ReplaySessionConfig;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
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
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;

public class NodeServerReplayManagerConfig {

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
    public final long serverReadinessPollIntervalMillis = 20;

    /**
     * Length of time to wait for the server to become ready, in milliseconds.
     * This is not currently configurable, but should be.
     */
    public final int serverReadinessTimeoutMillis = 3000;

    public final ImmutableList<TailerFactory> stdoutListeners;
    public final ImmutableList<TailerFactory> stderrListeners;

    private NodeServerReplayManagerConfig(Builder builder) {
        nodeExecutable = builder.nodeExecutable;
        harReplayProxyDirProvider = builder.harReplayProxyDirProvider;
        stdoutListeners = ImmutableList.copyOf(builder.stdoutListeners);
        stderrListeners = ImmutableList.copyOf(builder.stderrListeners);
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
        private static final String ZIP_RESOURCE_PATH = "/har-replay-proxy-0.0.5.zip";

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

        private Builder() {
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder addStdoutListener(TailerFactory val) {
            stdoutListeners.add(val);
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder addStderrListener(TailerFactory val) {
            stderrListeners.add(val);
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
            harReplayProxyDirProvider = checkNotNull(val);
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder addOutputEchoes() {
            addStdoutListener(config -> new PrintStreamTailerListener(System.out, file -> {}));
            addStderrListener(config -> new PrintStreamTailerListener(System.err, file -> {}));
            return this;
        }

        public NodeServerReplayManagerConfig build() {
            return new NodeServerReplayManagerConfig(this);
        }
    }

    static class PrintStreamTailerListener extends TailerListenerAdapter implements LogTailerListener {

        private final PrintStream destination;
        private final Consumer<? super File> tailerStoppedCallback;

        private PrintStreamTailerListener(PrintStream destination, Consumer<? super File> tailerStoppedCallback) {
            this.destination = requireNonNull(destination);
            this.tailerStoppedCallback = requireNonNull(tailerStoppedCallback);
        }

        @Override
        public void handle(String line) {
            destination.println(line);
        }


        @Override
        public void tailerStopped(File file) {
            tailerStoppedCallback.accept(file);
        }
    }

    public interface LogTailerListener extends TailerListener {
        void tailerStopped(File file);
    }

    public interface TailerFactory {

        LogTailerListener createTailer(ReplaySessionConfig sessionConfig);

    }
}
