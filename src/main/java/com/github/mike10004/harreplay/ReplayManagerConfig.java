package com.github.mike10004.harreplay;

import com.github.mike10004.nativehelper.Program;
import com.google.common.collect.Iterators;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.google.common.base.Preconditions.checkArgument;

public class ReplayManagerConfig {

    @Nullable
    private final File nodeExecutable;
    public final ServerReplayClientDirProvider serverReplayClientDirProvider;

    private ReplayManagerConfig(Builder builder) {
        nodeExecutable = builder.nodeExecutable;
        serverReplayClientDirProvider = builder.serverReplayClientDirProvider;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Program.Builder makeProgramBuilder() {
        if (nodeExecutable == null) {
            return Program.running("node");
        } else {
            return Program.running(nodeExecutable);
        }
    }

    public interface ServerReplayClientDirProvider {
        Path provide(Path scratchDir) throws IOException;
    }

    public static ReplayManagerConfig auto() {
        return ReplayManagerConfig.builder().build();
    }

    static class EmbeddedClientDirProvider implements ServerReplayClientDirProvider {

        private static final String ZIP_ROOT = "server-replay-client";

        public static ServerReplayClientDirProvider getInstance() {
            return instance;
        }

        private static final EmbeddedClientDirProvider instance = new EmbeddedClientDirProvider();

        @Override
        public Path provide(Path scratchDir) throws IOException {
            File zipFile = File.createTempFile("server-replay-client", ".zip", scratchDir.toFile());
            ByteSource zipSrc = Resources.asByteSource(getClass().getResource("/server-replay-client.zip"));
            zipSrc.copyTo(Files.asByteSink(zipFile));
            Path parentDir = java.nio.file.Files.createTempDirectory(scratchDir, "client-parent");
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

    public static final class Builder {
        private File nodeExecutable = null;
        private ServerReplayClientDirProvider serverReplayClientDirProvider = EmbeddedClientDirProvider.getInstance();

        private Builder() {
        }

        public Builder nodeExecutable(File executableFile) {
            nodeExecutable = executableFile;
            if (executableFile != null) {
                checkArgument(executableFile.canExecute(), "not executable: %s", executableFile);
            }
            return this;
        }

        public Builder serverReplayClientDirProvider(ServerReplayClientDirProvider val) {
            serverReplayClientDirProvider = val;
            return this;
        }

        public ReplayManagerConfig build() {
            return new ReplayManagerConfig(this);
        }
    }
}
