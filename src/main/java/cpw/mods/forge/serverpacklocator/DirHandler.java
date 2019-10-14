package cpw.mods.forge.serverpacklocator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DirHandler {
    public static Path createOrGetDirectory(final Path root, final String name) {
        final Path newDir = root.resolve(name);
        if (Files.exists(newDir) && Files.isDirectory(newDir)) {
            return newDir;
        }

        try {
            Files.createDirectory(newDir);
            return newDir;
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    public static Path createDirIfNeeded(final Path file) {
        try {
            Files.createDirectories(file);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
