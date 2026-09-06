package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.CubeFace;
import mizukichou.nekonyume.model.KeyframeInterpolation;
import mizukichou.nekonyume.model.ModelAnimation;
import mizukichou.nekonyume.model.ModelBone;
import mizukichou.nekonyume.model.ModelBoneAnimation;
import mizukichou.nekonyume.model.ModelCuboid;
import mizukichou.nekonyume.model.ModelDefinition;
import mizukichou.nekonyume.model.ModelInstance;
import mizukichou.nekonyume.model.ModelTransform;
import mizukichou.nekonyume.model.Quaternion;
import mizukichou.nekonyume.model.UvRect;
import mizukichou.nekonyume.model.ResourceId;
import mizukichou.nekonyume.model.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * .bbmodel 导入全流程（手写最小样例）。
 */
class BBModelImporterTest {

    private static final Logger LOGGER =
            Logger.getAnonymousLogger();

    private static final ResourceId MODEL_ID =
            ResourceId.parse("cats:test_cat");

    @Test
    void importsMinimalCatModel() throws Exception {

        ImportResult result =
                importJson(
                        minimalCatModel()
                );

        ModelDefinition definition =
                result.getDefinition();

        assertEquals(
                "cats:test_cat",
                definition.getId().toString()
        );

        assertEquals(
                3,
                definition.getPrimaryGeometry()
                        .boneCount()
        );

        /*
         * 骨骼局部变换：pivot - 父 pivot。
         */
        ModelBone root =
                definition.findBone("Root");

        assertEquals(
                0.0,
                root.getLocalTransform()
                        .getTranslation()
                        .getX()
        );

        ModelBone body =
                definition.findBone("Body");

        assertEquals(
                8.0,
                body.getLocalTransform()
                        .getTranslation()
                        .getY()
        );

        ModelBone head =
                definition.findBone("Head");

        assertEquals(
                16.0,
                head.getLocalTransform()
                        .getTranslation()
                        .getY()
        );
        assertEquals(
                -6.0,
                head.getLocalTransform()
                        .getTranslation()
                        .getZ()
        );

        /*
         * 立方体：offset = from - 骨骼 pivot。
         */
        assertEquals(
                1,
                body.getCuboids().size()
        );

        ModelCuboid cube =
                body.getCuboids().get(0);

        assertEquals(
                -4.0,
                cube.getOffset().getX()
        );
        assertEquals(
                -8.0,
                cube.getOffset().getY()
        );
        assertEquals(
                8.0,
                cube.getSize().getX()
        );
        assertEquals(
                8.0,
                cube.getSize().getY()
        );
        assertEquals(
                16.0,
                cube.getSize().getZ()
        );

        /*
         * 面级 UV 保真。
         */
        assertEquals(
                6,
                cube.getFaces().size()
        );

        /*
         * 面级 UV 为 4 值 UvRect：north = [0, 0] + size [8, 16]
         * → (0, 0, 8, 16)。
         */
        assertEquals(
                new UvRect(0.0, 0.0, 8.0, 16.0),
                cube.getFaces()
                        .get(CubeFace.NORTH)
        );

        assertEquals(
                0,
                cube.getTextureIndex()
        );

        assertEquals(
                1,
                definition.getTextures().size()
        );

        /*
         * 内嵌 base64 纹理解码。
         */
        assertEquals(
                1,
                result.getEmbeddedTextures().size()
        );

        byte[] png =
                result.getEmbeddedTextures()
                        .get(
                                ResourceId.parse(
                                        "cats:textures/model/test_cat/skin.png"
                                )
                        );

        assertNotNull(png);
        assertEquals(8, png.length);
        assertTrue(
                (png[0] & 0xFF) == 0x89
        );
    }

    @Test
    void importedDefinitionSupportsInstances() throws Exception {

        ImportResult result =
                importJson(
                        minimalCatModel()
                );

        ModelInstance instance =
                new ModelInstance(
                        result.getDefinition(),
                        UUID.randomUUID()
                );

        assertNotNull(
                instance.getBone("Head")
        );
    }

    @Test
    void rotationConvertsToQuaternion() throws Exception {

        ImportResult result =
                importJson(
                        minimalCatModel()
                                .replace(
                                        "\"rotation\": [0, 0, 0],",
                                        "\"rotation\": [0, 90, 0],"
                                )
                );

        ModelBone body =
                result.getDefinition()
                        .findBone("Body");

        ModelTransform transform =
                body.getLocalTransform();

        /*
         * 绕 Y 90°：+X 分量转向 -Z。
         */
        Vec3 rotated =
                transform.getRotation()
                        .rotate(
                                new Vec3(
                                        1.0,
                                        0.0,
                                        0.0
                                )
                        );

        assertEquals(
                0.0,
                rotated.getX(),
                1e-9
        );
        assertEquals(
                -1.0,
                rotated.getZ(),
                1e-9
        );
    }

    @Test
    void missingMetaRejected() {

        InvalidBBModelException exception =
                assertThrows(
                        InvalidBBModelException.class,
                        () -> importJson(
                                "{\"outliner\":[]}"
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains("meta")
        );
    }

    @Test
    void unsupportedVersionRejected() {

        assertThrows(
                UnsupportedBBModelVersionException.class,
                () -> importJson(
                        "{\"meta\":{\"format_version\":\"3.6\"," +
                                "\"model_format\":\"java_block\"}," +
                                "\"outliner\":[]}"
                )
        );
    }

    @Test
    void bedrockFormatRejected() {

        assertThrows(
                UnsupportedModelFormatException.class,
                () -> importJson(
                        "{\"meta\":{\"format_version\":\"4.10\"," +
                                "\"model_format\":\"bedrock_block\"}," +
                                "\"outliner\":[]}"
                )
        );
    }

    @Test
        /*
     * 0.9.0更新：4.x 顶层组必须名为 Root
     * （5.x 由 ModernAdapter 规范化——见 ModernAdapterTest）。
     */
    void topLevelGroupMustBeNamedRoot() {

        assertThrows(
                InvalidBoneException.class,
                () -> importJson(
                        minimalCatModel()
                                .replace(
                                        "\"name\": \"Root\",",
                                        "\"name\": \"group\","
                                )
                )
        );
    }


    @Test
    void multipleTopLevelGroupsRejected() {

        assertThrows(
                InvalidBBModelException.class,
                () -> importJson(
                        "{\"meta\":{\"format_version\":\"4.10\"," +
                                "\"model_format\":\"java_block\"}," +
                                "\"outliner\":[" +
                                "{\"name\":\"Root\",\"pivot\":[0,0,0]}," +
                                "{\"name\":\"Second\",\"pivot\":[0,0,0]}]}"
                )
        );
    }

    /*
     * 0.9.0更新：非零 origin 不再整体拒绝（5.x 的 origin
     * 即骨骼位置）。4.x 的边界是「origin 与 pivot 分离」。
     */
    @Test
    void originDifferentFromPivotRejected() {

        assertThrows(
                InvalidBBModelException.class,
                () -> importJson(
                        minimalCatModel()
                                .replace(
                                        "\"origin\": [0, 0, 0],",
                                        "\"origin\": [1, 0, 0],"
                                )
                )
        );
    }

    @Test
    void elementRotationRejected() {

        InvalidBBModelException exception =
                assertThrows(
                        InvalidBBModelException.class,
                        () -> importJson(
                                minimalCatModel()
                                        .replace(
                                                "\"mirror\": false",
                                                "\"mirror\": false," +
                                                        "\"rotation\": {" +
                                                        "\"angle\": 45," +
                                                        "\"axis\": \"y\"," +
                                                        "\"origin\": [0,8,0]}"
                                        )
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains("Element rotation")
        );
    }

    @Test
    void partialFacesRejected() {

        assertThrows(
                InvalidBBModelException.class,
                () -> importJson(
                        minimalCatModel()
                                .replace(
                                        "\"down\":  {\"uv\": [24, 8], " +
                                                "\"uv_size\": [8, 16],  " +
                                                "\"texture\": 0}",
                                        "\"down\":  {\"uv\": [24, 8], " +
                                                "\"uv_size\": [8, 16],  " +
                                                "\"texture\": 0},\n" +
                                                "\"extra\": {\"uv\": [0, 0], " +
                                                "\"uv_size\": [1, 1], " +
                                                "\"texture\": 0}"
                                )
                )
        );
    }

    @Test
    void textureIndexOutOfRangeRejected() {

        assertThrows(
                InvalidBBModelException.class,
                () -> importJson(
                        minimalCatModel()
                                .replace(
                                        "\"texture\": 0",
                                        "\"texture\": 7"
                                )
                )
        );
    }

    /*
     * 重名骨骼（Blockbench 允许重名；实机 jianzhou 触发）
     * 导入期消歧：后续重名改为 name_2，首次出现的保留
     * 原名，不再拒绝。
     */
    @Test
    void duplicateBoneNameIsDisambiguated() throws BBModelException {

        ModelDefinition definition =
                importJson(
                        minimalCatModel()
                                .replace(
                                        "\"name\": \"Head\",",
                                        "\"name\": \"Body\","
                                )
                ).getDefinition();

        assertNotNull(
                definition.findBone(
                        "Body"
                )
        );

        assertNotNull(
                definition.findBone(
                        "Body_2"
                )
        );
    }

    @Test
    void invalidBoneNameRejected() {

        /*
         * 骨骼显示名放宽（P2-5）：空格合法；
         * 控制字符被 JSON 层拒绝（InvalidBBModelException），
         * 超长名被语义层拒绝（InvalidBoneException）。
         */
        assertThrows(
                InvalidBBModelException.class,
                () -> importJson(
                        minimalCatModel()
                                .replace(
                                        "\"name\": \"Head\",",
                                        "\"name\": \"Bad\u0007Name\","
                                )
                )
        );
    }

    @Test
    void nonPngEmbeddedTextureRejected() {

        assertThrows(
                InvalidTextureException.class,
                () -> importJson(
                        minimalCatModel()
                                .replace(
                                        "data:image/png;base64,iVBORw0KGgo=",
                                        "data:image/png;base64,AAAAAA=="
                                )
                )
        );
    }

    @Test
    void externalTextureRecorded() throws Exception {

        ImportResult result =
                importJson(
                        minimalCatModel()
                                .replace(
                                        "data:image/png;base64,iVBORw0KGgo=",
                                        "textures/skin.png"
                                )
                );

        assertTrue(
                result.getEmbeddedTextures()
                        .isEmpty()
        );

        Map<ResourceId, String> external =
                result.getExternalTextureSources();

        assertEquals(
                1,
                external.size()
        );
        assertEquals(
                "textures/skin.png",
                external.get(
                        ResourceId.parse(
                                "cats:textures/model/test_cat/skin.png"
                        )
                )
        );
    }

    @Test
    void exportedFalseGroupsSkipped() throws Exception {

        ImportResult result =
                importJson(
                        minimalCatModel()
                                .replace(
                                        "\"name\": \"Head\",",
                                        "\"export\": false," +
                                                "\n\"name\": \"Head\","
                                )
                );

        assertEquals(
                2,
                result.getDefinition()
                        .getPrimaryGeometry()
                        .boneCount()
        );

        assertNull(
                result.getDefinition()
                        .findBone("Head")
        );
    }

    @Test
    void errorMessageCarriesModelIdAndPath() {

        InvalidBBModelException exception =
                assertThrows(
                        InvalidBBModelException.class,
                        () -> importJson(
                                "{oops"
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains("cats:test_cat")
        );
        assertTrue(
                exception.getMessage()
                        .contains("test.bbmodel")
        );
    }

    private static ImportResult importJson(
            String json
    ) throws BBModelException {

        return BBModelImporter.importJson(
                json,
                MODEL_ID,
                "test.bbmodel",
                LOGGER
        );
    }

    /*
     * ============================================================
     * 动画解析（Phase 5）
     * ============================================================
     */

    private static String modelWithAnimation() {

        return minimalCatModel().replace(
                "\"textures\": [",
                "\"animations\": [\n" +
                        "    {\n" +
                        "      \"name\": \"walk\",\n" +
                        "      \"loop\": true,\n" +
                        "      \"length\": 1.0,\n" +
                        "      \"animators\": {\n" +
                        "        \"Body\": {\n" +
                        "          \"name\": \"Body\",\n" +
                        "          \"type\": \"bone\",\n" +
                        "          \"keyframes\": [\n" +
                        "            {\n" +
                        "              \"channel\": \"rotation\",\n" +
                        "              \"data_points\": [\n" +
                        "                {\"x\": 0, \"y\": 0,   \"z\": 0, \"time\": 0},\n" +
                        "                {\"x\": 0, \"y\": 90,  \"z\": 0, \"time\": 1}\n" +
                        "              ]\n" +
                        "            }\n" +
                        "          ]\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"textures\": ["
        );
    }

    @Test
    void importsAnimations() throws Exception {

        ImportResult result =
                BBModelImporter.importJson(
                        modelWithAnimation(),
                        ResourceId.parse("cats:test_cat"),
                        "test.bbmodel",
                        Logger.getAnonymousLogger()
                );

        ModelDefinition definition =
                result.getDefinition();

        assertEquals(
                1,
                definition.getAnimations()
                        .size()
        );

        ModelAnimation walk =
                definition.getAnimation(
                        "walk"
                );

        assertNotNull(walk);
        assertEquals(1.0, walk.getDurationSeconds());
        assertEquals(true, walk.isLooping());

        ModelBoneAnimation bodyTrack =
                walk.getBoneAnimation(
                        "Body"
                );

        assertNotNull(bodyTrack);

        /*
         * 通道级：rotation 通道 2 帧（Euler 绝对空间），
         * 半程采样 = 增量 45°。
         */
        assertEquals(
                2,
                bodyTrack.getRotationChannel()
                        .getKeyframes()
                        .size()
        );

        ModelTransform sampled =
                bodyTrack.sample(
                        0.5
                );

        Quaternion sampledRotation =
                sampled.getRotation();

        Quaternion expectedRotation =
                Quaternion.fromYAxisDeg(
                        45.0
                );

        assertTrue(
                Math.abs(
                        sampledRotation.getY()
                                - expectedRotation.getY()
                ) < 1e-6 &&
                        Math.abs(
                                sampledRotation.getW()
                                        - expectedRotation.getW()
                        ) < 1e-6
        );

        assertTrue(
                bodyTrack.sample(
                                0.0
                        )
                        .isIdentity()
        );
    }

    @Test
    void animationKeyframesAreBakedRelativeToBase() throws Exception {

        /*
         * Body 自带 30° 基准旋转：
         * 绝对 90° 关键帧 → 增量 = base⁻¹ × 90° = 60°。
         */
        String json =
                modelWithAnimation().replace(
                        "\"rotation\": [0, 0, 0],\n          \"origin\": [0, 0, 0],\n          \"children\": [\n            {\n              \"name\": \"body_cube\"",
                        "\"rotation\": [0, 30, 0],\n          \"origin\": [0, 0, 0],\n          \"children\": [\n            {\n              \"name\": \"body_cube\""
                );

        ImportResult result =
                BBModelImporter.importJson(
                        json,
                        ResourceId.parse("cats:test_cat"),
                        "test.bbmodel",
                        Logger.getAnonymousLogger()
                );

        ModelAnimation walk =
                result.getDefinition()
                        .getAnimation(
                                "walk"
                        );

        /*
         * 30° 基准 + 绝对 90° 关键帧：
         * 采样增量 = base⁻¹ × 90° = 60°。
         */
        ModelTransform sampled =
                walk.getBoneAnimation(
                                "Body"
                        )
                        .sample(
                                1.0
                        );

        Quaternion rotation60 =
                sampled.getRotation();

        Quaternion expected60 =
                Quaternion.fromYAxisDeg(
                        60.0
                );

        assertTrue(
                Math.abs(
                        rotation60.getY()
                                - expected60.getY()
                ) < 1e-6 &&
                        Math.abs(
                                rotation60.getW()
                                        - expected60.getW()
                        ) < 1e-6
        );
    }

    @Test
    void illegalAnimationNameRejected() throws Exception {

        String json =
                modelWithAnimation().replace(
                        "\"name\": \"walk\",",
                        "\"name\": \"Bad\u0007Name\","
                );

        assertThrows(
                InvalidBBModelException.class,
                () -> BBModelImporter.importJson(
                        json,
                        ResourceId.parse("cats:test_cat"),
                        "test.bbmodel",
                        Logger.getAnonymousLogger()
                )
        );
    }

    @Test
    void unknownBoneInAnimationRejected() throws Exception {

        String json =
                modelWithAnimation().replace(
                        "\"Body\": {\n" +
                                "          \"name\": \"Body\",",
                        "\"NoSuchBone\": {\n" +
                                "          \"name\": \"NoSuchBone\","
                );

        assertThrows(
                InvalidBBModelException.class,
                () -> BBModelImporter.importJson(
                        json,
                        ResourceId.parse("cats:test_cat"),
                        "test.bbmodel",
                        Logger.getAnonymousLogger()
                )
        );
    }

    @Test
    void unsupportedInterpolationRejected() throws Exception {

        String json =
                modelWithAnimation().replace(
                        "{\"x\": 0, \"y\": 0,   \"z\": 0, \"time\": 0},",
                        "{\"x\": 0, \"y\": 0,   \"z\": 0, \"time\": 0, \"interpolation\": \"step\"},"
                );

        assertThrows(
                InvalidBBModelException.class,
                () -> BBModelImporter.importJson(
                        json,
                        ResourceId.parse("cats:test_cat"),
                        "test.bbmodel",
                        Logger.getAnonymousLogger()
                )
        );
    }

    @Test
    void catmullromInterpolationAccepted() throws Exception {

        /*
         * Blockbench 默认插值（5.x 真实文件主流）：必须支持。
         */
        String json =
                modelWithAnimation().replace(
                        "{\"x\": 0, \"y\": 0,   \"z\": 0, \"time\": 0},",
                        "{\"x\": 0, \"y\": 0,   \"z\": 0, \"time\": 0, \"interpolation\": \"catmullrom\"},"
                );

        ImportResult result =
                BBModelImporter.importJson(
                        json,
                        ResourceId.parse("cats:test_cat"),
                        "test.bbmodel",
                        Logger.getAnonymousLogger()
                );

        ModelBoneAnimation bodyTrack =
                result.getDefinition()
                        .getAnimation("walk")
                        .getBoneAnimation("Body");

        assertNotNull(bodyTrack);

        assertEquals(
                KeyframeInterpolation.CATMULLROM,
                bodyTrack.getRotationChannel()
                        .getKeyframes()
                        .get(0)
                        .getInterpolation()
        );
    }


    @Test
    void loopVariantsParsed() throws Exception {

        ImportResult result =
                BBModelImporter.importJson(
                        modelWithAnimation().replace(
                                "\"loop\": true",
                                "\"loop\": \"once\""
                        ),
                        ResourceId.parse("cats:test_cat"),
                        "test.bbmodel",
                        Logger.getAnonymousLogger()
                );

        assertEquals(
                false,
                result.getDefinition()
                        .getAnimation("walk")
                        .isLooping()
        );
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
    @Test
    void unexportedCubeIsSkipped() throws BBModelException {

        String json = minimalCatModel().replace(
                "\"name\": \"body_cube\",",
                "\"export\": false,\n            \"name\": \"body_cube\","
        );

        ModelDefinition definition =
                BBModelImporter.importJson(
                        json,
                        ResourceId.parse("cats:test_cat"),
                        "test.bbmodel",
                        LOGGER
                )
                        .getDefinition();

        assertTrue(
                definition.findBone("Body")
                        .getCuboids()
                        .isEmpty()
        );
    }

}
