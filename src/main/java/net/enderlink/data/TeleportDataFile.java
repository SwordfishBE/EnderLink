package net.enderlink.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Function;

/** Only allows replacement after the existing file has been successfully decoded. */
final class TeleportDataFile {
    private final Path path;
    private boolean writable;

    TeleportDataFile(Path path) {
        this.path = path;
    }

    <T> T load(Function<String, T> decoder) throws IOException {
        writable = false;
        String contents;
        try {
            contents = Files.readString(path);
        } catch (NoSuchFileException exception) {
            writable = true;
            return null;
        }
        T result = Objects.requireNonNull(decoder.apply(contents), "Empty teleport data.");
        writable = true;
        return result;
    }

    boolean isWritable() {
        return writable;
    }

    void save(String contents) throws IOException {
        if (!writable) {
            throw new IOException("Teleport data is read-only after a load or save failure.");
        }

        Path temporary = null;
        Path backupTemporary = null;
        try {
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), "teleports-", ".tmp");
            Files.writeString(temporary, contents);
            if (Files.exists(path)) {
                backupTemporary = Files.createTempFile(path.getParent(), "teleports-backup-", ".tmp");
                Files.copy(path, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
                Files.move(backupTemporary, path.resolveSibling(path.getFileName() + ".bak"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            // Fail closed on filesystems without atomic replacement; never truncate the original.
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            writable = false;
            throw exception;
        } finally {
            if (temporary != null) {
                Files.deleteIfExists(temporary);
            }
            if (backupTemporary != null) {
                Files.deleteIfExists(backupTemporary);
            }
        }
    }
}
