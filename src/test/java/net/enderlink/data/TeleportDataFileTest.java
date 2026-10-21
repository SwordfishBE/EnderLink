package net.enderlink.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class TeleportDataFileTest {
    @TempDir
    Path directory;

    @Test
    void preservesOriginalBytesWhenDecodingFails() throws IOException {
        Path path = directory.resolve("teleports.json");
        String original = "{\"pads\": [broken";
        Files.writeString(path, original);
        TeleportDataFile file = new TeleportDataFile(path);
        assertThrows(IllegalArgumentException.class, () -> file.load(json -> {
            throw new IllegalArgumentException("Invalid data");
        }));
        assertFalse(file.isWritable());
        assertThrows(IOException.class, () -> file.save("{\"pads\": []}"));
        assertEquals(original, Files.readString(path));
    }

    @Test
    void keepsPreviousContentsAsBackup() throws IOException {
        Path path = directory.resolve("teleports.json");
        Files.writeString(path, "existing pads and links");
        TeleportDataFile file = new TeleportDataFile(path);
        assertEquals("existing pads and links", file.load(Function.identity()));
        file.save("updated pads and links");
        assertEquals("updated pads and links", Files.readString(path));
        assertEquals("existing pads and links", Files.readString(directory.resolve("teleports.json.bak")));
        try (var files = Files.list(directory)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    void newWorldDoesNotReadOrModifyAnotherWorld() throws IOException {
        Path first = directory.resolve("first/enderlink/teleports.json");
        Path second = directory.resolve("second/enderlink/teleports.json");
        TeleportDataFile firstFile = new TeleportDataFile(first);
        assertNull(firstFile.load(Function.identity()));
        firstFile.save("first world");
        TeleportDataFile secondFile = new TeleportDataFile(second);
        assertNull(secondFile.load(Function.identity()));
        secondFile.save("second world");
        assertEquals("first world", Files.readString(first));
        assertEquals("second world", Files.readString(second));
    }

    @Test
    void backupFailurePreservesOriginalAndLocksFurtherWrites() throws IOException {
        Path path = directory.resolve("teleports.json");
        Files.writeString(path, "original");
        TeleportDataFile file = new TeleportDataFile(path);
        file.load(Function.identity());
        Path blockedBackup = Files.createDirectory(directory.resolve("teleports.json.bak"));
        Files.writeString(blockedBackup.resolve("keep"), "blocked");
        assertThrows(IOException.class, () -> file.save("replacement"));
        assertEquals("original", Files.readString(path));
        assertFalse(file.isWritable());
        assertThrows(IOException.class, () -> file.save("another replacement"));
    }

    @Test
    void cannotSaveBeforeLoadOrAfterNullDecode() throws IOException {
        Path path = directory.resolve("teleports.json");
        TeleportDataFile file = new TeleportDataFile(path);
        assertThrows(IOException.class, () -> file.save("unloaded"));
        Files.writeString(path, "null");
        assertThrows(NullPointerException.class, () -> file.load(json -> null));
        assertFalse(file.isWritable());
        assertEquals("null", Files.readString(path));
    }
}
