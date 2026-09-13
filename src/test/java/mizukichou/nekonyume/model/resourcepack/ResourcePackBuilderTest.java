package mizukichou.nekonyume.model.resourcepack;

import mizukichou.nekonyume.model.ModelDefinition;
import mizukichou.nekonyume.model.ResourceId;
import mizukichou.nekonyume.model.bbmodel.BBModelException;
import mizukichou.nekonyume.model.bbmodel.BBModelImporter;
import mizukichou.nekonyume.model.bbmodel.ImportResult;
import mizukichou.nekonyume.model.bbmodel.json.JsonArray;
import mizukichou.nekonyume.model.bbmodel.json.JsonObject;
import mizukichou.nekonyume.model.bbmodel.json.JsonParser;
import mizukichou.nekonyume.model.bbmodel.json.JsonParseException;
import mizukichou.nekonyume.model.bbmodel.json.JsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 资源包生成：目录结构 / JSON schema（parse 断言）/ 冲突检测。
 *
 * <p>
 * 知识包 P2-15：JSON 断言必须 parse 后断言对象结构与
 * 字段类型，禁止只做字符串 contains。
 * </p>
 */
class ResourcePackBuilderTest {

    private static final Logger LOGGER =
            Logger.getAnonymousLogger();

    @TempDir
    Path outputDir;

    private ModelDefinition definition;

    private ImportResult result;

    @BeforeEach
    void setUp() throws BBModelException {

        result =
                BBModelImporter.importJson(
                        minimalCatModel(),
                        ResourceId.parse("cats:test_cat"),
                        "test.bbmodel",
                        LOGGER
                );

        definition =
                result.getDefinition();
    }

    private void build(
            PackFormat format
    ) throws Exception {

        ResourcePackBuilder.build(
                definition,
                result.getEmbeddedTextures(),
                outputDir,
                format,
                LOGGER
        );
    }

    private JsonObject parse(
            Path file
    ) throws Exception {

        try {

            return JsonParser.parse(
                            Files.readString(file)
                    )
                    .asObject();

        } catch (JsonParseException exception) {

            throw new AssertionError(
                    "Invalid JSON in " + file,
                    exception
            );
        }
    }

    @Test
    void buildsFullPackStructure() throws Exception {

        build(
                MinecraftResourcePackVersion.MINECRAFT_26_2
        );

        Path root =
                outputDir.resolve(
                        "assets/nekonyume"
                );

        assertTrue(
                Files.exists(
                        outputDir.resolve("pack.mcmeta")
                )
        );

        /*
         * 三个骨骼：Root / Body / Head。
         */
        for (String bone :
                java.util.Arrays.asList(
                        "root",
                        "body",
                        "head"
                )) {

            assertTrue(
                    Files.exists(
                            root.resolve(
                                    "items/cats/test_cat/" +
                                            bone + ".json"
                            )
                    ),
                    "items: " + bone
            );

            assertTrue(
                    Files.exists(
                            root.resolve(
                                    "models/cats/test_cat/" +
                                            bone + ".json"
                            )
                    ),
                    "models: " + bone
            );
        }

        /*
         * 纹理 PNG。
         */
        assertTrue(
                Files.exists(
                        root.resolve(
                                "textures/model/cats/test_cat/skin.png"
                        )
                )
        );

        /*
         * 图集源声明（26.2 声明式图集：SpriteSourceList
         * 只读取 minecraft 命名空间的 atlases/*.json；
         * model/ 目录必须注册为 sprite 来源，否则贴图
         * 显示紫黑块。blocks 与 items 两个图集都要。
         */
        for (String atlasName :
                new String[] {
                        "blocks",
                        "items"
                }) {

            Path atlas =
                    outputDir.resolve(
                            "assets/minecraft/atlases/" +
                                    atlasName + ".json"
                    );

            assertTrue(
                    Files.exists(
                            atlas
                    ),
                    "atlases/" + atlasName + ".json 必须存在"
            );

            JsonObject atlasJson =
                    JsonParser.parse(
                            Files.readString(
                                    atlas,
                                    java.nio.charset.StandardCharsets.UTF_8
                            )
                    ).asObject();

            JsonValue sources =
                    atlasJson.get(
                            "sources"
                    );

            assertEquals(
                    "minecraft:directory",
                    sources.asArray()
                            .items()
                            .get(0)
                            .asObject()
                            .get("type")
                            .asString()
                            .value()
            );
            assertEquals(
                    "model",
                    sources.asArray()
                            .items()
                            .get(0)
                            .asObject()
                            .get("source")
                            .asString()
                            .value()
            );
            assertEquals(
                    "model/",
                    sources.asArray()
                            .items()
                            .get(0)
                            .asObject()
                            .get("prefix")
                            .asString()
                            .value()
            );
        }
    }

    @Test
    void itemDefinitionUsesModernSchema() throws Exception {

        build(
                MinecraftResourcePackVersion.MINECRAFT_26_2
        );

        JsonObject itemDefinition =
                parse(
                        outputDir.resolve(
                                "assets/nekonyume/items/" +
                                        "cats/test_cat/body.json"
                        )
                );

        /*
         * P0-1：现代 item definition ——
         * {"model": {"type": "minecraft:model", "model": "..."}}
         */
        JsonObject model =
                itemDefinition.getObject(
                        "model"
                );

        assertEquals(
                "minecraft:model",
                model.getString(
                        "type"
                ),
                "item model type"
        );

        assertEquals(
                "nekonyume:cats/test_cat/body",
                model.getString(
                        "model"
                ),
                "model reference"
        );
    }

    @Test
    void modelJsonHasElementsFacesAndTextureSlots() throws Exception {

        build(
                MinecraftResourcePackVersion.MINECRAFT_26_2
        );

        JsonObject model =
                parse(
                        outputDir.resolve(
                                "assets/nekonyume/models/" +
                                        "cats/test_cat/body.json"
                        )
                );

        /*
         * 纹理槽：#tex0 → 模型作用域纹理路径。
         */
        assertEquals(
                "nekonyume:model/cats/test_cat/skin",
                model.getObject("textures")
                        .getString("tex0")
        );

        JsonArray elements =
                model.getArray(
                        "elements"
                );

        assertEquals(
                1,
                elements.size(),
                "Body 有一个带纹理立方体"
        );

        JsonObject element =
                elements.get(0)
                        .asObject();

        JsonArray from =
                element.getArray("from");

        assertEquals(-4.0, from.get(0).asNumber().value(), 1e-9);
        assertEquals(-8.0, from.get(1).asNumber().value(), 1e-9);
        assertEquals(-8.0, from.get(2).asNumber().value(), 1e-9);

        JsonArray to =
                element.getArray("to");

        assertEquals(4.0, to.get(0).asNumber().value(), 1e-9);
        assertEquals(0.0, to.get(1).asNumber().value(), 1e-9);
        assertEquals(8.0, to.get(2).asNumber().value(), 1e-9);

        /*
         * 面 UV 为 4 值 [u0, v0, u1, v1]。
         */
        JsonObject north =
                element.getObject("faces")
                        .getObject("north");

        JsonArray uv =
                north.getArray("uv");

        assertEquals(0.0, uv.get(0).asNumber().value(), 1e-9);
        assertEquals(0.0, uv.get(1).asNumber().value(), 1e-9);
        assertEquals(8.0, uv.get(2).asNumber().value(), 1e-9);
        assertEquals(16.0, uv.get(3).asNumber().value(), 1e-9);

        assertEquals(
                "#tex0",
                north.getString(
                        "texture"
                )
        );
    }

    @Test
    void boneWithoutCubesHasNoElements() throws Exception {

        build(
                MinecraftResourcePackVersion.MINECRAFT_26_2
        );

        JsonObject head =
                parse(
                        outputDir.resolve(
                                "assets/nekonyume/models/" +
                                        "cats/test_cat/head.json"
                        )
                );

        assertEquals(
                0,
                head.getArray("elements")
                        .size()
        );
    }

    @Test
    void packMetaUsesModernMajorMinorSchema() throws Exception {

        ResourcePackBuilder.build(
                definition,
                result.getEmbeddedTextures(),
                outputDir,
                new PackFormat(99, 0),
                LOGGER
        );

        JsonObject meta =
                parse(
                        outputDir.resolve(
                                "pack.mcmeta"
                        )
                );

        JsonObject pack =
                meta.getObject(
                        "pack"
                );

        /*
         * P0-2：major.minor 数组形式 + min/max_format
         * （legacy pack_format 已省略，0.9.0更新）。
         */
        assertVersion(
                pack.getArray("min_format"),
                99,
                0
        );

        assertVersion(
                pack.getArray("max_format"),
                99,
                0
        );

        assertEquals(
                PackMetadataBuilder.DESCRIPTION,
                pack.getString(
                        "description"
                )
        );
    }

    @Test
    void mc262PackFormatIs880() {

        assertEquals(
                88,
                MinecraftResourcePackVersion
                        .MINECRAFT_26_2
                        .major()
        );

        assertEquals(
                0,
                MinecraftResourcePackVersion
                        .MINECRAFT_26_2
                        .minor()
        );
    }

    private static void assertVersion(
            JsonArray version,
            int expectedMajor,
            int expectedMinor
    ) {

        assertEquals(
                2,
                version.size()
        );

        assertEquals(
                expectedMajor,
                version.get(0)
                        .asNumber()
                        .intValue()
        );

        assertEquals(
                expectedMinor,
                version.get(1)
                        .asNumber()
                        .intValue()
        );
    }

    @Test
    void boneNameCollisionRejected() throws Exception {

        /*
         * "Head" 与 "head" 合法并存（validator 大小写敏感），
         * 小写后资源路径冲突。
         */
        ImportResult colliding =
                BBModelImporter.importJson(
                        collidingBoneNames(),
                        ResourceId.parse("cats:test_cat"),
                        "test.bbmodel",
                        LOGGER
                );

        assertThrows(
                ResourcePackException.class,
                () -> ResourcePackBuilder.build(
                        colliding.getDefinition(),
                        result.getEmbeddedTextures(),
                        outputDir,
                        MinecraftResourcePackVersion.MINECRAFT_26_2,
                        LOGGER
                )
        );
    }

    @Test
    void textureNameCollisionRejected() throws Exception {

        /*
         * 纹理名 sanitize 后同名（"Skin A" / "skin a"
         * → skin_a.png）。
         */
        ImportResult real =
                BBModelImporter.importJson(
                        twoTexturesSameBase(),
                        ResourceId.parse("cats:test_cat"),
                        "test.bbmodel",
                        LOGGER
                );

        assertThrows(
                ResourcePackException.class,
                () -> ResourcePackBuilder.build(
                        real.getDefinition(),
                        real.getEmbeddedTextures(),
                        outputDir,
                        MinecraftResourcePackVersion.MINECRAFT_26_2,
                        LOGGER
                )
        );
    }

    private static String collidingBoneNames() {

        return """
                {
                  "meta": {
                    "format_version": "4.10",
                    "model_format": "java_block"
                  },
                  "name": "test cat",
                  "outliner": [
                    {
                      "name": "Root",
                      "pivot": [0, 0, 0],
                      "rotation": [0, 0, 0],
                      "origin": [0, 0, 0],
                      "children": [
                        {
                          "name": "Head",
                          "pivot": [0, 16, -6],
                          "rotation": [0, 0, 0],
                          "origin": [0, 0, 0],
                          "children": []
                        },
                        {
                          "name": "head",
                          "pivot": [0, 18, -6],
                          "rotation": [0, 0, 0],
                          "origin": [0, 0, 0],
                          "children": []
                        }
                      ]
                    }
                  ],
                  "textures": []
                }
                """;
    }

    private static String twoTexturesSameBase() {

        return """
                {
                  "meta": {
                    "format_version": "4.10",
                    "model_format": "java_block"
                  },
                  "name": "test cat",
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
                                "north": {"uv": [0, 0],  "uv_size": [8, 16],  "texture": 0},
                                "east":  {"uv": [8, 0],  "uv_size": [16, 16], "texture": 0},
                                "south": {"uv": [24, 0], "uv_size": [8, 16],  "texture": 0},
                                "west":  {"uv": [32, 0], "uv_size": [16, 16], "texture": 0},
                                "up":    {"uv": [8, 8],  "uv_size": [8, 16],  "texture": 0},
                                "down":  {"uv": [24, 8], "uv_size": [8, 16],  "texture": 0}
                              },
                              "mirror": false
                            }
                          ]
                        }
                      ]
                    }
                  ],
                  "textures": [
                    {
                      "name": "Skin A",
                      "source": "data:image/png;base64,iVBORw0KGgo="
                    },
                    {
                      "name": "skin a",
                      "source": "data:image/png;base64,iVBORw0KGgo="
                    }
                  ]
                }
                """;
    }

    private static String minimalCatModel() {

        return """
                {
                  "meta": {
                    "format_version": "4.10",
                    "model_format": "java_block"
                  },
                  "name": "test cat",
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
                                "north": {"uv": [0, 0],  "uv_size": [8, 16],  "texture": 0},
                                "east":  {"uv": [8, 0],  "uv_size": [16, 16], "texture": 0},
                                "south": {"uv": [24, 0], "uv_size": [8, 16],  "texture": 0},
                                "west":  {"uv": [32, 0], "uv_size": [16, 16], "texture": 0},
                                "up":    {"uv": [8, 8],  "uv_size": [8, 16],  "texture": 0},
                                "down":  {"uv": [24, 8], "uv_size": [8, 16],  "texture": 0}
                              },
                              "mirror": false
                            }
                          ]
                        },
                        {
                          "name": "Head",
                          "pivot": [0, 16, -6],
                          "rotation": [0, 0, 0],
                          "origin": [0, 0, 0],
                          "children": []
                        }
                      ]
                    }
                  ],
                  "textures": [
                    {
                      "name": "skin",
                      "source": "data:image/png;base64,iVBORw0KGgo="
                    }
                  ]
                }
                """;
    }
}
