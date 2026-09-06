package mizukichou.nekonyume.model.resourcepack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackArchiverTest {

    @TempDir
    Path tempDir;

    @Test
    void archivesDirectoryWithRelativeEntries() throws IOException {

        Path packDir = tempDir.resolve("pack");
        Files.createDirectories(packDir.resolve("assets/nekonyume/textures"));
        Files.writeString(packDir.resolve("pack.mcmeta"), "{}");
        Files.writeString(
                packDir.resolve("assets/nekonyume/textures/skin.png"),
                "fake-png"
        );

        Path zip = tempDir.resolve("out.zip");

        ResourcePackArchiver.archiveDirectory(
                packDir,
                zip
        );

        assertTrue(Files.isRegularFile(zip));

        List<String> entries = new ArrayList<>();

        try (ZipFile zf = new ZipFile(zip.toFile())) {
            zf.stream()
                    .map(ZipEntry::getName)
                    .forEach(entries::add);
        }

        assertEquals(2, entries.size());

        assertTrue(entries.contains("pack.mcmeta"));

        assertTrue(
                entries.contains(
                        "assets/nekonyume/textures/skin.png"
                )
        );
    }

    @Test
    void missingSourceDirectoryThrows() {

        Path zip = tempDir.resolve("out.zip");

        assertThrows(
                IOException.class,
                () -> ResourcePackArchiver.archiveDirectory(
                        tempDir.resolve("no-such-dir"),
                        zip
                )
        );

        assertFalse(Files.exists(zip));
    }

    @Test
    void rejectsNullArguments() {

        assertThrows(
                IllegalArgumentException.class,
                () -> ResourcePackArchiver.archiveDirectory(
                        null,
                        tempDir.resolve("x.zip")
                )
        );
    }
}
