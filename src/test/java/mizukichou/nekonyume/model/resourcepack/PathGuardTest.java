package mizukichou.nekonyume.model.resourcepack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路径沙箱测试（0.9.0更新）：
 * 外部纹理等不可信路径的穿越/绝对/盘符/UNC 防御。
 */
class PathGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsParentTraversal() throws IOException {

        Path base =
                tempDir.resolve(
                        "models"
                );

        Files.createDirectories(
                base
        );

        for (String evil :
                new String[]{
                        "../outside.png",
                        "a/../../outside.png",
                        "a/b/../../../outside.png"
                }) {

            assertThrows(
                    IOException.class,
                    () -> PathGuard.resolveWithin(
                            base,
                            evil
                    ),
                    "must reject: " + evil
            );
        }
    }

    @Test
    void rejectsAbsoluteAndDriveLetterPaths() throws IOException {

        Path base =
                tempDir.resolve(
                        "models"
                );

        Files.createDirectories(
                base
        );

        for (String evil :
                new String[]{
                        "/etc/passwd",
                        "C:\\secret\\data.png",
                        "C:/secret/data.png",
                        "\\\\server\\share\\file.png",
                        "//server/share/file.png"
                }) {

            assertThrows(
                    IOException.class,
                    () -> PathGuard.resolveWithin(
                            base,
                            evil
                    ),
                    "must reject: " + evil
            );
        }
    }

    @Test
    void rejectsDotSegments() throws IOException {

        Path base =
                tempDir.resolve(
                        "models"
                );

        Files.createDirectories(
                base
        );

        assertThrows(
                IOException.class,
                () -> PathGuard.resolveWithin(
                        base,
                        "./skin.png"
                )
        );

        assertThrows(
                IOException.class,
                () -> PathGuard.resolveWithin(
                        base,
                        "a//b.png"
                )
        );
    }

    @Test
    void acceptsLegalRelativePath() throws IOException {

        Path base =
                tempDir.resolve(
                        "models"
                );

        Files.createDirectories(
                base
        );

        Path resolved =
                PathGuard.resolveWithin(
                        base,
                        "textures/skin.png"
                );

        assertTrue(
                resolved.startsWith(
                        base.toAbsolutePath()
                                .normalize()
                )
        );

        assertEquals(
                base.toAbsolutePath()
                        .normalize()
                        .resolve(
                                "textures/skin.png"
                        ),
                resolved
        );
    }
}
