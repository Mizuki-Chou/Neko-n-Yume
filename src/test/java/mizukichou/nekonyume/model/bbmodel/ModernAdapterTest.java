package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.ModelBone;
import mizukichou.nekonyume.model.ModelDefinition;
import mizukichou.nekonyume.model.ResourceId;
import mizukichou.nekonyume.model.Vec3;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ModernAdapter（Blockbench 5.x）synthetic fixture 测试。
 *
 * <p>
 * <strong>SYNTHETIC FIXTURE</strong>：本类所有 .bbmodel 文本均为
 * 手写 5.x 结构（用于针对性边界单测），不冒充真实
 * Blockbench 回归（真实文件回归见 RealBbmodelFixtureTest）。
 * 注意：JSON 内锚点一律使用无缩进依赖的子串（Java text block
 * 会剥离公共缩进）。
 * </p>
 */
class ModernAdapterTest {

    private static final Logger LOGGER =
            Logger.getAnonymousLogger();

    /**
     * 最小 5.x 结构：groups + outliner UUID 层级 + cube 元素
     * （4 值 UV）+ 内嵌纹理。
     */
    private static final String MINIMAL_5X =
            """
                    {
                      "meta": { "format_version": "5.0" },
                      "model_format": "free",
                      "groups": [
                        { "name": "Root", "uuid": "root-uuid",
                          "origin": [0, 0, 0], "rotation": [0, 0, 0],
                          "export": true },
                        { "name": "Body", "uuid": "body-uuid",
                          "origin": [0, 8, 0], "rotation": [0, 0, 0],
                          "export": true }
                      ],
                      "outliner": [
                        { "uuid": "root-uuid", "children": [
                          { "uuid": "body-uuid", "children": [
                            "cube-uuid"
                          ]}
                        ]}
                      ],
                      "elements": [
                        { "name": "body_cube", "uuid": "cube-uuid",
                          "type": "cube",
                          "from": [-4, 0, -8], "to": [4, 8, 8],
                          "faces": {
                            "north": {"uv": [0, 0, 8, 16], "texture": 0},
                            "east":  {"uv": [8, 0, 24, 16], "texture": 0},
                            "south": {"uv": [24, 0, 32, 16], "texture": 0},
                            "west":  {"uv": [32, 0, 48, 16], "texture": 0},
                            "up":    {"uv": [8, 8, 16, 24], "texture": 0},
                            "down":  {"uv": [16, 8, 24, 24], "texture": 0}
                          }
                        }
                      ],
                      "textures": [
                        { "name": "skin",
                          "source": "data:image/png;base64,iVBORw0KGgo=" }
                      ]
                    }
                    """;

    /**
     * 动画对象本体（不含数组括号）：animator key = UUID、
     * keyframe 级 time、catmullrom。
     */
    private static final String WALK_ANIMATION =
            """
                    {
                      "name": "walk", "loop": "loop", "length": 1.0,
                      "animators": {
                        "body-uuid": {
                          "name": "Body", "type": "bone",
                          "keyframes": [
                            { "channel": "rotation", "time": 0,
                              "interpolation": "catmullrom",
                              "data_points": [
                                {"x": "0", "y": "0", "z": "0"},
                                {"x": "0", "y": "90", "z": "0"}
                              ]},
                            { "channel": "rotation", "time": 1,
                              "interpolation": "catmullrom",
                              "data_points": [
                                {"x": "0", "y": "0", "z": "0"}
                              ]}
                          ]
                        }
                      }
                    }
                    """;

    /**
     * 在 textures 前插入 animations 数组（无缩进依赖锚点）。
     */
    private static String withAnimations(
            String animationObjects
    ) {

        return MINIMAL_5X.replace(
                "\"textures\": [",
                "\"animations\": [" + animationObjects +
                        "],\n                      \"textures\": ["
        );
    }

    private static ModelDefinition importModel(
            String json
    ) {

        try {

            return BBModelImporter.importJson(
                            json,
                            ResourceId.parse(
                                    "cats:test_cat"
                            ),
                            "synthetic-5x.bbmodel",
                            LOGGER
                    )
                    .getDefinition();

        } catch (BBModelException exception) {

            throw new AssertionError(
                    exception
            );
        }
    }

    /*
     * P0-1：5.x 标准 cube 元素被接受（不再是"只认 mesh"）。
     */
    @Test
    void standardFiveZeroCubeElementIsSupported() {

        ModelDefinition definition =
                importModel(
                        MINIMAL_5X
                );

        ModelBone body =
                definition.findBone(
                        "Body"
                );

        assertNotNull(
                body
        );

        assertEquals(
                1,
                body.getCuboids()
                        .size()
        );

        assertEquals(
                1,
                definition.getTextures()
                        .size()
        );
    }

    /*
     * 回归：Blockbench 导出的元素旋转带浮点残差
     * （实测 -1.0E-5 度，本质为 0）不得触发 V1 旋转拒绝。
     */
    @Test
    void residualElementRotationWithinEpsilonIsAccepted() {

        ModelDefinition definition =
                importModel(
                        MESH_RESIDUAL_ROTATION
                );

        ModelBone body =
                definition.findBone(
                        "Body"
                );

        assertNotNull(
                body
        );

        assertEquals(
                1,
                body.getCuboids()
                        .size()
        );
    }

    /*
     * 回归：outliner 字符串引用指向被跳过的元素
     * （visibility=false，实机 jianzhou.bbmodel 触发）时
     * 应静默豁免，不得误报 "unknown child uuid"。
     */
    @Test
    void outlinerReferenceToSkippedInvisibleElementIsExempt() {

        ModelDefinition definition =
                importModel(
                        MINIMAL_5X.replace(
                                "\"type\": \"cube\",",
                                "\"type\": \"cube\", " +
                                        "\"visibility\": false,"
                        )
                );

        ModelBone body =
                definition.findBone(
                        "Body"
                );

        assertNotNull(
                body
        );

        assertEquals(
                0,
                body.getCuboids()
                        .size()
        );
    }

    /*
     * 回归：Root 枢轴不在模型原点（实机 jianzhou 的
     * Root origin = [0, 3, 0]）时，导入器把整个模型平移
     * 归一化：根骨骼局部平移归零，而非拒绝。
     */
    @Test
    void rootPivotOffsetIsNormalizedToOrigin() {

        ModelDefinition definition =
                importModel(
                        MINIMAL_5X.replace(
                                "\"origin\": [0, 0, 0], " +
                                        "\"rotation\": [0, 0, 0],",
                                "\"origin\": [0, 3, 0], " +
                                        "\"rotation\": [0, 0, 0],"
                        )
                );

        ModelBone root =
                definition.findBone(
                        "Root"
                );

        assertNotNull(
                root
        );

        assertEquals(
                Vec3.ZERO,
                root.getLocalTransform()
                        .getTranslation()
        );
    }

    /*
     * 回归：重名骨骼（Blockbench 允许重名；实机 jianzhou
     * 触发）导入期消歧：后续重名改为 name_2，首次出现的
     * 保留原名，不得硬报 Duplicate bone name。
     */
    @Test
    void duplicateBoneNamesAreDisambiguated() {

        ModelDefinition definition =
                importModel(
                        MINIMAL_5X.replace(
                                "\"name\": \"Body\",",
                                "\"name\": \"Root\","
                        )
                );

        ModelBone root =
                definition.findBone(
                        "Root"
                );

        ModelBone renamed =
                definition.findBone(
                        "Root_2"
                );

        assertNotNull(
                root
        );

        assertNotNull(
                renamed
        );
    }

    /**
     * 真实 mesh 结构（取自 basic.bbmodel 腿部）＋旋转残差
     * [-1e-5, 0, 0]：必须在容差内按 0 处理。
     */
    private static final String MESH_RESIDUAL_ROTATION =
            """
                    {
                      "meta": { "format_version": "5.0" },
                      "model_format": "free",
                      "groups": [
                        { "name": "Root", "uuid": "root-uuid",
                          "origin": [0, 0, 0], "rotation": [0, 0, 0],
                          "export": true },
                        { "name": "Body", "uuid": "body-uuid",
                          "origin": [0, 0, 0], "rotation": [0, 0, 0],
                          "export": true }
                      ],
                      "outliner": [
                        { "uuid": "root-uuid", "children": [
                          { "uuid": "body-uuid", "children": [
                            "mesh-uuid"
                          ]}
                        ]}
                      ],
                      "elements": [
                        { "name": "leg_mesh", "uuid": "mesh-uuid",
                          "type": "mesh",
                          "origin": [3, 0, -5],
                          "rotation": [-1e-5, 0, 0],
                          "vertices": {
                            "tGIz": [2, 7, 2], "lf4g": [2, 7, -2],
                            "eeNw": [2, 1, 2], "bhvC": [2, 1, -2],
                            "Rw5d": [-2, 7, 2], "qfmZ": [-2, 7, -2],
                            "XDP8": [-2, 1, 2], "2FVa": [-2, 1, -2]
                          },
                          "faces": {
                            "f1": {
                              "uv": {"bhvC": [4, 40], "eeNw": [0, 40],
                                      "tGIz": [0, 34], "lf4g": [4, 34]},
                              "vertices": ["lf4g", "tGIz", "eeNw", "bhvC"]
                            },
                            "f2": {
                              "uv": {"qfmZ": [0, 41], "lf4g": [4, 41],
                                      "tGIz": [4, 45], "Rw5d": [0, 45]},
                              "vertices": ["Rw5d", "tGIz", "lf4g", "qfmZ"]
                            },
                            "f3": {
                              "uv": {"2FVa": [5, 45], "XDP8": [5, 41],
                                      "eeNw": [9, 41], "bhvC": [9, 45]},
                              "vertices": ["bhvC", "eeNw", "XDP8", "2FVa"]
                            },
                            "f4": {
                              "uv": {"XDP8": [5, 40], "Rw5d": [5, 34],
                                      "tGIz": [9, 34], "eeNw": [9, 40]},
                              "vertices": ["eeNw", "tGIz", "Rw5d", "XDP8"]
                            },
                            "f5": {
                              "uv": {"2FVa": [14, 40], "bhvC": [10, 40],
                                      "lf4g": [10, 34], "qfmZ": [14, 34]},
                              "vertices": ["qfmZ", "lf4g", "bhvC", "2FVa"]
                            },
                            "f6": {
                              "uv": {"2FVa": [15, 40], "qfmZ": [15, 34],
                                      "Rw5d": [19, 34], "XDP8": [19, 40]},
                              "vertices": ["XDP8", "Rw5d", "qfmZ", "2FVa"]
                            }
                          }
                        }
                      ],
                      "textures": [
                        { "name": "skin",
                          "source": "data:image/png;base64,iVBORw0KGgo=" }
                      ]
                    }
                    """;

    /*
     * P62：UUID 层级不错位（Root → Body）。
     */
    @Test
    void uuidHierarchyProducesCorrectBoneTree() {

        ModelDefinition definition =
                importModel(
                        MINIMAL_5X
                );

        ModelBone root =
                definition.getPrimaryGeometry()
                        .getRoot();

        assertEquals(
                "Root",
                root.getName()
        );

        assertEquals(
                1,
                root.getChildren()
                        .size()
        );

        assertEquals(
                "Body",
                root.getChildren()
                        .get(0)
                        .getName()
        );
    }

    /*
     * P0-3 + P63：父旋转下子骨骼局部位置 =
     * parentAbs⁻¹ × (origin − parentOrigin)。
     * Parent(0,0,0) rot 90°Y；Child origin (10,0,0)
     * → local translation = (0, 0, 10)。
     */
    @Test
    void parentRotationIsInvertedIntoChildLocalPosition() {

        String dedicated =
                """
                        {
                          "meta": { "format_version": "5.0" },
                          "model_format": "free",
                          "groups": [
                            { "name": "Root", "uuid": "r",
                              "origin": [0, 0, 0], "rotation": [0, 0, 0],
                              "export": true },
                            { "name": "Body", "uuid": "b",
                              "origin": [0, 0, 0], "rotation": [0, 90, 0],
                              "export": true },
                            { "name": "Head", "uuid": "h",
                              "origin": [10, 0, 0], "rotation": [0, 0, 0],
                              "export": true }
                          ],
                          "outliner": [
                            { "uuid": "r", "children": [
                              { "uuid": "b", "children": [
                                { "uuid": "h", "children": [] }
                              ]}
                            ]}
                          ],
                          "elements": [],
                          "textures": []
                        }
                        """;

        ModelDefinition definition =
                importModel(
                        dedicated
                );

        ModelBone head =
                definition.findBone(
                        "Head"
                );

        assertNotNull(
                head
        );

        Vec3 local =
                head.getLocalTransform()
                        .getTranslation();

        /*
         * R(-90°) × (10, 0, 0) = (0, 0, 10)。
         */
        assertEquals(
                0.0,
                local.getX(),
                1e-9
        );

        assertEquals(
                0.0,
                local.getY(),
                1e-9
        );

        assertEquals(
                10.0,
                local.getZ(),
                1e-9
        );
    }

    /*
     * P0-5 + 动画：catmullrom + animator UUID + loop 语义。
     */
    @Test
    void animationWithUuidAnimatorsAndCatmullromIsPreserved() {

        ModelDefinition definition =
                importModel(
                        withAnimations(
                                WALK_ANIMATION
                        )
                );

        var walk =
                definition.getAnimation(
                        "walk"
                );

        assertNotNull(
                walk
        );

        assertEquals(
                mizukichou.nekonyume.model.LoopMode.LOOP,
                walk.getLoopMode()
        );

        var bodyTrack =
                walk.getBoneAnimation(
                        "Body"
                );

        assertNotNull(
                bodyTrack
        );

        assertTrue(
                bodyTrack.getRotationChannel()
                        .getKeyframes()
                        .stream()
                        .anyMatch(
                                frame ->
                                        frame.getInterpolation() ==
                                                mizukichou.nekonyume.model.KeyframeInterpolation.CATMULLROM
                        )
        );
    }

    /*
     * P1-23：负 keyframe time 拒绝。
     */
    @Test
    void negativeKeyframeTimeIsRejected() {

        assertThrows(
                InvalidBBModelException.class,
                () -> BBModelImporter.importJson(
                        withAnimations(
                                WALK_ANIMATION.replace(
                                        "\"time\": 0",
                                        "\"time\": -1"
                                )
                        ),
                        ResourceId.parse(
                                "cats:test_cat"
                        ),
                        "synthetic-5x.bbmodel",
                        LOGGER
                )
        );
    }

    /*
     * P1-17：未知 outliner UUID 硬错误（不再 warning 跳过）。
     * 手法：只改 elements 声明处的 uuid，使 outliner 引用悬空。
     */
    @Test
    void unknownOutlinerUuidIsHardError() {

        assertThrows(
                InvalidBBModelException.class,
                () -> BBModelImporter.importJson(
                        MINIMAL_5X.replace(
                                "\"uuid\": \"cube-uuid\"",
                                "\"uuid\": \"other-uuid\""
                        ),
                        ResourceId.parse(
                                "cats:test_cat"
                        ),
                        "synthetic-5x.bbmodel",
                        LOGGER
                )
        );
    }

    /*
     * P1-19：重复 group uuid 拒绝。
     */
    @Test
    void duplicateGroupUuidIsRejected() {

        assertThrows(
                InvalidBBModelException.class,
                () -> BBModelImporter.importJson(
                        MINIMAL_5X.replace(
                                "\"name\": \"Body\", \"uuid\": \"body-uuid\",",
                                "\"name\": \"Body\", \"uuid\": \"root-uuid\","
                        ),
                        ResourceId.parse(
                                "cats:test_cat"
                        ),
                        "synthetic-5x.bbmodel",
                        LOGGER
                )
        );
    }

    /*
     * P1-10：纹理资源路径含 modelId.path（跨模型隔离）。
     */
    @Test
    void textureResourcePathIncludesModelPath() {

        ModelDefinition definition =
                importModel(
                        MINIMAL_5X
                );

        assertEquals(
                "cats:textures/model/test_cat/skin.png",
                definition.getTextures()
                        .get(0)
                        .getResource()
                        .toString()
        );
    }

    /*
     * P1-9：sanitize 后碰撞拒绝（"skin" 与 "Skin"）。
     * 手法：在 skin 条目后追加 Skin 条目（无缩进依赖锚点，
     * 单次 replace）。
     */
    @Test
    void sanitizationCollisionIsRejected() {

        String json =
                MINIMAL_5X.replace(
                        "data:image/png;base64,iVBORw0KGgo=\" }",
                        "data:image/png;base64,iVBORw0KGgo=\" },\n"
                                + "                        { \"name\": \"Skin\",\n"
                                + "                          \"source\": \"data:image/png;base64,iVBORw0KGgo=\" }"
                );

        assertThrows(
                InvalidBBModelException.class,
                () -> BBModelImporter.importJson(
                        json,
                        ResourceId.parse(
                                "cats:test_cat"
                        ),
                        "synthetic-5x.bbmodel",
                        LOGGER
                )
        );
    }

    /*
     * 0.9.0更新：outliner 重复引用同一
     * Element UUID → 显式拒绝（同一 Cube 渲染多次会
     * z-fighting / 复杂度翻倍）。
     */
    @Test
    void duplicateElementReferenceIsRejected() {

        String json =
                MINIMAL_5X.replace(
                        "\"children\": [\n" +
                                "        \"cube-uuid\"",
                        "\"children\": [\n" +
                                "        \"cube-uuid\",\n" +
                                "        \"cube-uuid\""
                );

        assertThrows(
                InvalidBBModelException.class,
                () -> BBModelImporter.importJson(
                        json,
                        ResourceId.parse(
                                "cats:test_cat"
                        ),
                        "synthetic-5x.bbmodel",
                        LOGGER
                )
        );
    }

    /*
     * 0.9.0更新：Cube 非整数 texture
     * 索引显式拒绝（与 mesh 路径 faceTextureIndex
     * 同口径——不再静默截断）。
     */
    @Test
    void nonIntegerCubeTextureIsRejected() {

        String json =
                MINIMAL_5X.replace(
                        "\"texture\": 0",
                        "\"texture\": 1.5"
                );

        assertThrows(
                InvalidBBModelException.class,
                () -> BBModelImporter.importJson(
                        json,
                        ResourceId.parse(
                                "cats:test_cat"
                        ),
                        "synthetic-5x.bbmodel",
                        LOGGER
                )
        );
    }

    /*
     * 0.9.0更新：5.x Cube 的 mirror 保留
     * 到 Canonical（此前读取后丢弃，与 Legacy 语义不同）。
     */
    @Test
    void cubeMirrorIsPreserved() {

        String json =
                MINIMAL_5X.replace(
                        "\"type\": \"cube\",",
                        "\"type\": \"cube\",\n" +
                                "                            \"mirror\": true,"
                );

        ModelDefinition definition =
                importModel(
                        json
                );

        ModelBone body =
                definition.findBone(
                        "Body"
                );

        assertTrue(
                body.getCuboids()
                        .get(0)
                        .isMirror()
        );
    }

}
