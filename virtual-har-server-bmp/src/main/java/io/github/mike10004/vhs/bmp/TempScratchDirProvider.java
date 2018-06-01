package io.github.mike10004.vhs.bmp;

import java.io.IOException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

class TempScratchDirProvider implements ScratchDirProvider {

    private final Path parent;
    private final String prefix;

    public TempScratchDirProvider(Path parent, String prefix) {
        this.parent = requireNonNull(parent);
        requireNonNull(prefix);
        if (prefix.length() < 6) {
            prefix += "scratch";
        }
        this.prefix = prefix;
    }

    @Override
    public Scratch createScratchDir() throws IOException {
        //noinspection ResultOfMethodCallIgnored
        parent.toFile().mkdirs();
        if (!parent.toFile().isDirectory()) {
            throw new IOException(parent + " is not a directory and directory could not be created there");
        }
        Path dir = java.nio.file.Files.createTempDirectory(parent, prefix);
        return new TempScratch(dir);
    }

    private static class TempScratch implements Scratch {

        private final Path root;

        private TempScratch(Path root) {
            this.root = requireNonNull(root);
        }

        @Override
        public Path getRoot() {
            return root;
        }
    }
}
