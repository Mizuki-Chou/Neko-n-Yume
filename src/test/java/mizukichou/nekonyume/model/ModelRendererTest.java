package mizukichou.nekonyume.model;

import mizukichou.nekonyume.model.bbmodel.BBModelException;
import mizukichou.nekonyume.model.bbmodel.BBModelImporter;
import mizukichou.nekonyume.model.bbmodel.ImportResult;
import mizukichou.nekonyume.testutil.FakeBukkit;
import mizukichou.nekonyume.testutil.FakeRenderBackend;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 静态渲染器：对象创建 / 世界变换 / 变更检测 / 漂移重锚定。
 */
class ModelRendererTest {

    private static final Logger LOGGER =
            Logger.getAnonymousLogger();

    private World world;

    private ModelDefinition definition;

    private FakeRenderBackend backend;

    private ModelRenderer renderer;

    private Location entityLocation;

    @BeforeEach
    void setUp() throws BBModelException {

        world =
                FakeBukkit.proxy(
                        World.class,
                        Map.of(
                                "getName", "test",
                                "getUID", UUID.randomUUID()
                        ),
                        null
                );

        ImportResult result =
                BBModelImporter.importJson(
                        minimalCatModel(),
                        ResourceId.parse("cats:test_cat"),
                        "test.bbmodel",
                        LOGGER
                );

        definition =
                result.getDefinition();

        entityLocation =
                new Location(
                        world,
                        100.0,
                        64.0,
                        100.0
                );

        backend =
                new FakeRenderBackend();

        renderer =
                ModelRenderer.create(
                        new ModelInstance(
                                definition,
                                UUID.randomUUID()
                        ),
                        backend,
                        entityLocation,
                        UUID.randomUUID()
                );
    }

    @Test
    void createsOneObjectPerBone() {

        assertEquals(
                3,
                renderer.objectCount()
        );
        assertEquals(
                3,
                backend.getCreated().size()
        );
    }

    @Test
    void objectsCarryItemModelIds() {

        FakeRenderBackend.FakeRenderObject body =
                backend.objectFor("Body");

        assertNotNull(body);

        assertEquals(
                "nekonyume:cats/test_cat/body",
                body.getItemModel().toString()
        );

        FakeRenderBackend.FakeRenderObject head =
                backend.objectFor("Head");

        assertEquals(
                "nekonyume:cats/test_cat/head",
                head.getItemModel().toString()
        );
    }

    @Test
    void tickWritesYawRotatedWorldTransforms() {

        renderer.tick(
                entityLocation,
                0.0
        );

        /*
         * yaw 0：Body pivot (0,8,0) → 骨骼位移 (0, 0.5, 0)
         * + 客户端居中补偿 R·(0.5,0.5,0.5) = (0.5, 1.0, 0.5)。
         */
        FakeRenderBackend.FakeRenderObject body =
                backend.objectFor("Body");

        assertEquals(
                0.5,
                body.getLastTranslation().getX(),
                1e-9
        );
        assertEquals(
                1.0,
                body.getLastTranslation().getY(),
                1e-9
        );

        /*
         * Head pivot (0,16,-6) → (0.5, 1.5, 0.125)。
         */
        FakeRenderBackend.FakeRenderObject head =
                backend.objectFor("Head");

        assertEquals(
                0.125,
                head.getLastTranslation().getZ(),
                1e-9
        );

        /*
         * yaw 90：Head 绕 Y 旋转，(x,z) → (z,-x)：
         * (0.5,1.5,0.125) → (0.125,1.5,-0.5)。
         */
        renderer.tick(
                entityLocation,
                90.0
        );

        assertEquals(
                0.125,
                head.getLastTranslation().getX(),
                1e-9
        );
        assertEquals(
                -0.5,
                head.getLastTranslation().getZ(),
                1e-9
        );

        /*
         * 旋转复合：leftRotation 非单位四元数
         * （yaw × 骨骼旋转 × π 抵消）。
         */
        assertFalse(
                head.getLastRotation()
                        .isIdentity()
        );
    }

    @Test
    void unchangedStateSkipsWrites() {

        renderer.tick(
                entityLocation,
                0.0
        );

        int writes =
                backend.objectFor("Body")
                        .getTransformWrites();

        renderer.tick(
                entityLocation,
                0.0
        );

        assertEquals(
                writes,
                backend.objectFor("Body")
                        .getTransformWrites()
        );
    }

    @Test
    void positionChangeIsTracked() {

        renderer.tick(
                entityLocation,
                0.0
        );

        int writes =
                backend.objectFor("Body")
                        .getTransformWrites();

        Location moved =
                entityLocation.clone()
                        .add(1.0, 0.0, 0.0);

        renderer.tick(
                moved,
                0.0
        );

        assertEquals(
                writes + 1,
                backend.objectFor("Body")
                        .getTransformWrites()
        );

        /*
         * 位移进入 translation（drift），叠加在
         * 0.5 格居中补偿之上：0.5 + 1.0 = 1.5。
         */
        assertEquals(
                1.5,
                backend.objectFor("Body")
                        .getLastTranslation()
                        .getX(),
                1e-9
        );
    }

    @Test
    void farDriftTriggersReanchor() {

        renderer.tick(
                entityLocation,
                0.0
        );

        int teleports =
                backend.objectFor("Body")
                        .getTeleportCount();

        Location far =
                entityLocation.clone()
                        .add(40.0, 0.0, 0.0);

        renderer.tick(
                far,
                0.0
        );

        assertEquals(
                teleports + 1,
                backend.objectFor("Body")
                        .getTeleportCount()
        );

        assertEquals(
                far.getX(),
                backend.objectFor("Body")
                        .getLastTeleportTarget()
                        .getX(),
                1e-9
        );
    }

    @Test
    void destroyRemovesAllAndIsIdempotent() {

        renderer.destroy();

        assertTrue(
                renderer.isDestroyed()
        );

        for (FakeRenderBackend.FakeRenderObject object :
                backend.getCreated()) {

            assertTrue(
                    object.isRemoved()
            );
        }

        renderer.destroy();

        assertEquals(
                0,
                renderer.objectCount()
        );
    }

    @Test
    void tickAfterDestroyIsNoOp() {

        renderer.destroy();

        renderer.tick(
                entityLocation.clone().add(100, 0, 0),
                45.0
        );

        for (FakeRenderBackend.FakeRenderObject object :
                backend.getCreated()) {

            assertEquals(
                    0,
                    object.getTransformWrites()
            );
        }
    }

    @Test
    void createRejectsNulls() {

        assertThrows(
                IllegalArgumentException.class,
                () -> ModelRenderer.create(
                        null,
                        backend,
                        entityLocation,
                        UUID.randomUUID()
                )
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
                          "children": [
                            {
                              "name": "head_cube",
                              "type": "cube",
                              "from": [-2, 14, -8],
                              "to": [2, 18, -4],
                              "faces": {
                                "north": {"uv": [0, 0],  "uv_size": [4, 4],   "texture": 0},
                                "east":  {"uv": [4, 0],  "uv_size": [4, 4],   "texture": 0},
                                "south": {"uv": [8, 0],  "uv_size": [4, 4],   "texture": 0},
                                "west":  {"uv": [12, 0], "uv_size": [4, 4],   "texture": 0},
                                "up":    {"uv": [0, 4],  "uv_size": [4, 4],   "texture": 0},
                                "down":  {"uv": [4, 4],  "uv_size": [4, 4],   "texture": 0}
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
                      "name": "skin",
                      "source": "data:image/png;base64,iVBORw0KGgo="
                    }
                  ]
                }
                """;
    }
    /*
     * API 构造可绕开 BBModelValidator 产生"合法骨骼名但非法资源
     * 路径"的模型（如含 ".." 的名字）：渲染器必须显式拒绝而非
     * 把 null itemModel 传给后端。
     */
    @Test
    void createRejectsBoneNamesProducingInvalidResourcePaths() {

        ModelBone bad = new ModelBone(
                "A..B",
                ModelTransform.IDENTITY,
                java.util.List.of(),
                java.util.List.of(
                        new ModelCuboid(
                                "cube",
                                new Vec3(
                                        0.0,
                                        0.0,
                                        0.0
                                ),
                                new Vec3(
                                        1.0,
                                        1.0,
                                        1.0
                                ),
                                ModelCuboid.NO_TEXTURE,
                                false,
                                null
                        )
                )
        );

        ModelBone root = new ModelBone(
                "Root",
                ModelTransform.IDENTITY,
                java.util.List.of(bad),
                java.util.List.of()
        );

        ModelGeometry geometry =
                new ModelGeometry.Builder(
                        "main",
                        root
                )
                        .build();

        ModelDefinition definition =
                new ModelDefinition.Builder()
                        .id("cats:test_cat")
                        .geometry(geometry)
                        .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> ModelRenderer.create(
                        new ModelInstance(
                                definition,
                                UUID.randomUUID()
                        ),
                        backend,
                        entityLocation,
                        UUID.randomUUID()
                )
        );
    }


    /*
     * 0.9.0更新：非有限位置/偏航防御——
     * NaN/Infinity 不得进入 Display 变换。
     */
    @Test
    void tickIgnoresNonFiniteInputs() {

        renderer.tick(
                null,
                0.0
        );

        renderer.tick(
                new Location(
                        world,
                        Double.NaN,
                        64.0,
                        0.0
                ),
                0.0
        );

        renderer.tick(
                new Location(
                        world,
                        0.0,
                        64.0,
                        0.0
                ),
                Double.NaN
        );

        renderer.tick(
                new Location(
                        world,
                        0.0,
                        64.0,
                        0.0
                ),
                Double.POSITIVE_INFINITY
        );

        /*
         * 防御只跳过本次写入，渲染器仍可用。
         */
        renderer.tick(
                new Location(
                        world,
                        0.0,
                        64.0,
                        0.0
                ),
                0.0
        );

        assertFalse(
                renderer.isDestroyed()
        );
    }

    @Test
    void createRejectsNonFiniteSpawn() {

        assertThrows(
                IllegalArgumentException.class,
                () -> ModelRenderer.create(
                        new ModelInstance(
                                definition,
                                UUID.randomUUID()
                        ),
                        backend,
                        new Location(
                                world,
                                Double.NaN,
                                64.0,
                                0.0
                        ),
                        UUID.randomUUID()
                )
        );
    }

}
