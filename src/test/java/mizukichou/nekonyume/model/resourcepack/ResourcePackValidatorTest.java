package mizukichou.nekonyume.model.resourcepack;

import mizukichou.nekonyume.model.ModelDefinition;
import mizukichou.nekonyume.model.ResourceId;
import mizukichou.nekonyume.model.bbmodel.BBModelImporter;
import mizukichou.nekonyume.model.bbmodel.ImportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resource Pack 自检器（STATIC VERIFIED 一环）。
 *
 * <p>
 * SYNTHETIC FIXTURE：本测试使用的 .bbmodel 文本为
 * 手写最小结构，仅用于验证自检器逻辑；真实 Blockbench
 * 5.x 回归由 RealBbmodelFixtureTest 覆盖。
 * </p>
 */
class ResourcePackValidatorTest {

    @TempDir
    Path tempDir;

    private final Logger logger =
            Logger.getAnonymousLogger();

    private static final String MINIMAL_MODEL =
            """
                    {
                      "meta": { "format_version": "4.10" },
                      "model_format": "java_block",
                      "name": "test",
                      "outliner": [
                        {
                          "name": "Root",
                          "pivot": [0, 0, 0],
                          "rotation": [0, 0, 0],
                          "origin": [0, 0, 0],
                          "children": [
                            {
                              "name": "Body",
                              "pivot": [0, 8, 0],
                              "rotation": [0, 0, 0],
                              "origin": [0, 0, 0],
                              "children": [
                                {
                                  "name": "body_cube",
                                  "type": "cube",
                                  "from": [-4, 0, -8],
                                  "to": [4, 8, 8],
                                  "faces": {
                                    "north": { "uv": [0, 0], "uv_size": [8, 16], "texture": 0 },
                                    "east":  { "uv": [8, 0], "uv_size": [16, 16], "texture": 0 },
                                    "south": { "uv": [24, 0], "uv_size": [8, 16], "texture": 0 },
                                    "west":  { "uv": [32, 0], "uv_size": [16, 16], "texture": 0 },
                                    "up":    { "uv": [8, 8], "uv_size": [8, 16], "texture": 0 },
                                    "down":  { "uv": [24, 8], "uv_size": [8, 16], "texture": 0 }
                                  },
                                  "mirror": false
                                }
                              ]
                            }
                          ]
                        }
                      ],
                      "textures": [
                        { "name": "skin", "source": "data:image/png;base64,iVBORw0KGgo=" }
                      ]
                    }
                    """;

    private Path buildPack(
    ) throws Exception {

        ImportResult imported =
                BBModelImporter.importJson(
                        MINIMAL_MODEL,
                        ResourceId.parse(
                                "cats:test_cat"
                        ),
                        "synthetic.bbmodel",
                        logger
                );

        ModelDefinition definition =
                imported.getDefinition();

        Map<ResourceId, byte[]> textures =
                imported.getEmbeddedTextures();

        Path packDirectory =
                tempDir.resolve(
                        "pack"
                );

        ResourcePackBuilder.build(
                definition,
                textures,
                packDirectory,
                MinecraftResourcePackVersion
                        .MINECRAFT_26_2,
                logger
        );

        return packDirectory;
    }

    @Test
    void validatesCleanPack(
    ) throws Exception {

        ValidationResult result =
                ResourcePackValidator.validate(
                        buildPack(),
                        MinecraftResourcePackVersion
                                .MINECRAFT_26_2
                );

        assertTrue(
                result.valid()
        );
    }

    @Test
    void reportsMissingModelReference(
    ) throws Exception {

        Path pack =
                buildPack();

        Files.delete(
                pack.resolve(
                        "assets/nekonyume/models/"
                                + "cats/test_cat/body.json"
                )
        );

        ValidationResult result =
                ResourcePackValidator.validate(
                        pack,
                        MinecraftResourcePackVersion
                                .MINECRAFT_26_2
                );

        assertFalse(
                result.valid()
        );

        assertTrue(
                result.issues()
                        .stream()
                        .anyMatch(
                                issue -> issue.problem()
                                        .contains(
                                                "missing reference"
                                        )
                        )
        );
    }

    @Test
    void reportsMalformedItemJson(
    ) throws Exception {

        Path pack =
                buildPack();

        Files.writeString(
                pack.resolve(
                        "assets/nekonyume/items/"
                                + "cats/test_cat/body.json"
                ),
                "{ not json"
        );

        ValidationResult result =
                ResourcePackValidator.validate(
                        pack,
                        MinecraftResourcePackVersion
                                .MINECRAFT_26_2
                );

        assertFalse(
                result.valid()
        );

        assertTrue(
                result.issues()
                        .stream()
                        .anyMatch(
                                issue -> issue.problem()
                                        .contains(
                                                "JSON 解析失败"
                                        )
                        )
        );
    }

    @Test
    void reportsOrphanTexture(
    ) throws Exception {

        Path pack =
                buildPack();

        Path orphan =
                pack.resolve(
                        "assets/nekonyume/textures/model/"
                                + "cats/test_cat/orphan.png"
                );

        Files.createDirectories(
                orphan.getParent()
        );

        Files.write(
                orphan,
                new byte[]{1, 2, 3}
        );

        ValidationResult result =
                ResourcePackValidator.validate(
                        pack,
                        MinecraftResourcePackVersion
                                .MINECRAFT_26_2
                );

        assertFalse(
                result.valid()
        );

        assertTrue(
                result.issues()
                        .stream()
                        .anyMatch(
                                issue -> issue.problem()
                                        .contains(
                                                "orphan asset"
                                        )
                        )
        );
    }

    @Test
    void reportsIllegalNamespace(
    ) throws Exception {

        Path pack =
                buildPack();

        Files.createDirectories(
                pack.resolve(
                        "assets/Bad NS"
                )
        );

        ValidationResult result =
                ResourcePackValidator.validate(
                        pack,
                        MinecraftResourcePackVersion
                                .MINECRAFT_26_2
                );

        assertFalse(
                result.valid()
        );

        assertTrue(
                result.issues()
                        .stream()
                        .anyMatch(
                                issue -> issue.problem()
                                        .contains(
                                                "命名空间不合法"
                                        )
                        )
        );
    }

    @Test
    void issueReportContainsRequiredSections(
    ) throws Exception {

        Path pack =
                buildPack();

        Files.writeString(
                pack.resolve(
                        "assets/nekonyume/items/"
                                + "cats/test_cat/body.json"
                ),
                "{ not json"
        );

        ValidationResult result =
                ResourcePackValidator.validate(
                        pack,
                        MinecraftResourcePackVersion
                                .MINECRAFT_26_2
                );

        assertFalse(
                result.valid()
        );

        String report =
                result.issues()
                        .get(0)
                        .format();

        for (String section :
                new String[]{
                        "文件：",
                        "问题：",
                        "原因：",
                        "建议："
                }) {

            assertTrue(
                    report.contains(
                            section
                    ),
                    "报告必须包含" + section
                            + "，实际：" + report
            );
        }
    }

    @Test
    void zipValidationRejectsDuplicateEntries(
    ) throws Exception {

        Path zip =
                tempDir.resolve(
                        "dup.zip"
                );

        try (ZipOutputStream out =
                     new ZipOutputStream(
                             Files.newOutputStream(
                                     zip
                             )
                     )) {

            ZipEntry first =
                    new ZipEntry(
                            "pack.mcmeta"
                    );

            ZipEntry duplicate =
                    new ZipEntry(
                            "./pack.mcmeta"
                    );

            out.putNextEntry(
                    first
            );

            out.write(
                    "{}".getBytes()
            );

            out.closeEntry();

            out.putNextEntry(
                    duplicate
            );

            out.write(
                    "{}".getBytes()
            );

            out.closeEntry();
        }

        ValidationResult result =
                ResourcePackValidator.validateZip(
                        zip,
                        MinecraftResourcePackVersion
                                .MINECRAFT_26_2
                );

        assertFalse(
                result.valid()
        );

        assertTrue(
                result.issues()
                        .stream()
                        .anyMatch(
                                issue -> issue.problem()
                                        .contains(
                                                "重复 entry"
                                        )
                        )
        );
    }

    @Test
    void zipValidationPassesForArchivedPack(
    ) throws Exception {

        Path pack =
                buildPack();

        Path zip =
                tempDir.resolve(
                        "pack.zip"
                );

        ResourcePackArchiver.archiveDirectory(
                pack,
                zip
        );

        ValidationResult result =
                ResourcePackValidator.validateZip(
                        zip,
                        MinecraftResourcePackVersion
                                .MINECRAFT_26_2
                );

        assertTrue(
                result.valid()
        );
    }

    /*
     * 0.9.0更新：elements 非对象条目 → issue（不静默）。
     */
    @Test
    void elementsNonObjectIsReported() throws Exception {

        Path pack =
                buildPack();

        Path bodyModel =
                pack.resolve(
                        "assets/nekonyume/models/cats/test_cat/body.json"
                );

        Files.writeString(
                bodyModel,
                "{ \"textures\": {}, \"elements\": [ 123, \"hello\" ] }"
        );

        ValidationResult result =
                ResourcePackValidator.validate(
                        pack,
                        MinecraftResourcePackVersion.MINECRAFT_26_2
                );

        assertFalse(
                result.valid()
        );

        assertTrue(
                result.issues()
                        .stream()
                        .anyMatch(
                                issue -> issue.problem()
                                        .contains(
                                                "elements"
                                        )
                        )
        );
    }

    /*
     * 0.9.0更新：未知扩展名 → issue。
     */
    @Test
    void unexpectedExtensionIsReported() throws Exception {

        Path pack =
                buildPack();

        Files.writeString(
                pack.resolve(
                        "assets/nekonyume/models/cats/test_cat/notes.txt"
                ),
                "not a model"
        );

        ValidationResult result =
                ResourcePackValidator.validate(
                        pack,
                        MinecraftResourcePackVersion.MINECRAFT_26_2
                );

        assertFalse(
                result.valid()
        );

        assertTrue(
                result.issues()
                        .stream()
                        .anyMatch(
                                issue -> issue.problem()
                                        .contains(
                                                "扩展名"
                                        )
                        )
        );
    }

    /*
     * 0.9.0更新：texture 非 PNG → issue。
     */
    @Test
    void nonPngTextureIsReported() throws Exception {

        Path pack =
                buildPack();

        Path texture =
                pack.resolve(
                        "assets/nekonyume/textures/model/cats/test_cat/skin.png"
                );

        Files.writeString(
                texture,
                "hello world, not a png"
        );

        ValidationResult result =
                ResourcePackValidator.validate(
                        pack,
                        MinecraftResourcePackVersion.MINECRAFT_26_2
                );

        assertFalse(
                result.valid()
        );
    }

    /*
     * 0.9.0更新：纹理 alias（#）→ unsupported issue（不误报 missing）。
     */
    @Test
    void textureAliasIsExplicitUnsupported() throws Exception {

        Path pack =
                buildPack();

        Path bodyModel =
                pack.resolve(
                        "assets/nekonyume/models/cats/test_cat/body.json"
                );

        Files.writeString(
                bodyModel,
                "{ \"textures\": { \"0\": \"#skin\" }, \"elements\": [] }"
        );

        ValidationResult result =
                ResourcePackValidator.validate(
                        pack,
                        MinecraftResourcePackVersion.MINECRAFT_26_2
                );

        assertFalse(
                result.valid()
        );

        assertTrue(
                result.issues()
                        .stream()
                        .anyMatch(
                                issue -> issue.problem()
                                        .contains(
                                                "变量引用"
                                        )
                        )
        );
    }
}
