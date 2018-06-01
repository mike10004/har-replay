package io.github.mike10004.vhs.bmp;

import org.apache.commons.io.FileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface that defines a method to get a temporary directory for files that
 * can be deleted shortly.
 */
public interface ScratchDirProvider {

    /**
     * Interface for an object representing a scratch directory on disk where
     * temporary files are to be written and ultimately deleted.
     * Files are deleted when {@link #close()} is invoked.
     */
    interface Scratch extends java.io.Closeable {

        /**
         * Gets the directory where files can be written.
         * @return the directory
         */
        Path getRoot();

        /**
         * Deletes the directory.
         * @throws IOException if deletion fails
         */
        default void close() throws IOException {
            try {
                FileUtils.forceDelete(getRoot().toFile());
            } catch (FileNotFoundException ignore) {
            }
        }
    }

    /**
     * Creates a temporary directory on disk.
     * @return the scratch instance
     * @throws IOException if the directory could not be created
     */
    Scratch createScratchDir() throws IOException;

    /**
     * Returns an instance that creates a directory beneath the given pathname.
     * @param parent the parent directory
     * @return the provider instance
     * @see #under(Path, String)
     */
    static ScratchDirProvider under(Path parent) {
        return under(parent, "virtual-har-server");
    }

    /**
     * Returns an instance that creates a directory beneath the given pathname. The directory name
     * will have the given prefix.
     * @param parent the parent directory
     * @param prefix the name prefix
     * @return the provider instance
     */
    static ScratchDirProvider under(Path parent, String prefix) {
        return new TempScratchDirProvider(parent, prefix);
    }

}
