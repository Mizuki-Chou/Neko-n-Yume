package mizukichou.nekonyume.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ModelDefinition / ModelGeometry 构建校验与不可变性。
 */
class ModelDefinitionTest {

    @Test
    void buildsValidDefinition() {

        ModelDefinition definition =
                buildCatDefinition();

        assertEquals(
                "cats:black_cat",
                definition.getId().toString()
        );

        assertEquals(
                1,
                definition.getGeometries().size()
        );

        ModelBone head =
                definition.findBone("Head");

        assertNotNull(head);
        assertNull(definition.findBone("Nose"));

        assertEquals(
                4,
                definition.getPrimaryGeometry()
                        .boneCount()
        );

        assertEquals(
                1,
                definition.getTextures().size()
        );
    }

    @Test
    void rootBoneNameIsNotForced() {

        /*
         * 根骨骼名不强制 Root（P0-4 现实折中）：
         * 任意名均可作为模型根。
         */
        ModelBone customRoot =
                new ModelBone(
                        "Body",
                        ModelTransform.IDENTITY,
                        List.of(),
                        List.of()
                );

        ModelGeometry geometry =
                new ModelGeometry.Builder(
                        "main",
                        customRoot
                ).build();

        assertEquals(
                "Body",
                geometry.getRoot()
                        .getName()
        );
    }

    @Test
    void duplicateBoneNamesRejected() {

        ModelBone leaf =
                new ModelBone(
                        "Head",
                        ModelTransform.IDENTITY,
                        List.of(),
                        List.of()
                );

        ModelBone root =
                new ModelBone(
                        "Root",
                        ModelTransform.IDENTITY,
                        List.of(
                                leaf,
                                leaf
                        ),
                        List.of()
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelGeometry.Builder(
                        "main",
                        root
                ).build()
        );
    }

    @Test
    void missingGeometryRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelDefinition.Builder()
                        .id("cats:black_cat")
                        .build()
        );
    }

    @Test
    void multipleGeometriesRejectedInV1() {

        ModelGeometry geometry =
                new ModelGeometry.Builder(
                        "main",
                        new ModelBone(
                                "Root",
                                ModelTransform.IDENTITY,
                                List.of(),
                                List.of()
                        )
                ).build();

        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelDefinition.Builder()
                        .id("cats:black_cat")
                        .geometry(geometry)
                        .geometry(geometry)
                        .build()
        );
    }

    @Test
    void invalidModelIdRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelDefinition.Builder()
                        .id("no-colon")
        );
    }

    @Test
    void cuboidTextureIndexMustBeInRange() {

        ModelBone root =
                new ModelBone(
                        "Root",
                        ModelTransform.IDENTITY,
                        List.of(),
                        List.of(
                                new ModelCuboid(
                                        "body",
                                        Vec3.ZERO,
                                        new Vec3(4.0, 4.0, 8.0),
                                        new Vec2(0.0, 0.0),
                                        new Vec2(16.0, 16.0),
                                        3,
                                        false
                                )
                        )
                );

        ModelGeometry geometry =
                new ModelGeometry.Builder(
                        "main",
                        root
                ).build();

        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelDefinition.Builder()
                        .id("cats:black_cat")
                        .geometry(geometry)
                        .texture(
                                new ModelTexture(
                                        "skin",
                                        ResourceId.parse(
                                                "cats:textures/black_cat.png"
                                        )
                                )
                        )
                        .build()
        );
    }

    @Test
    void listsAreUnmodifiable() {

        ModelDefinition definition =
                buildCatDefinition();

        assertThrows(
                UnsupportedOperationException.class,
                () -> definition.getGeometries()
                        .add(
                                definition.getPrimaryGeometry()
                        )
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> definition.getTextures()
                        .clear()
        );

        ModelBone head =
                definition.findBone("Head");

        assertThrows(
                UnsupportedOperationException.class,
                () -> head.getChildren()
                        .add(null)
        );
    }

    @Test
    void invalidBoneNamesRejected() {

        for (String bad :
                java.util.Arrays.asList(
                        null,
                        "",
                        "Bad\u0007Name",
                        "x".repeat(65)
                )) {

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ModelBone(
                            bad,
                            ModelTransform.IDENTITY,
                            List.of(),
                            List.of()
                    )
            );
        }
    }

    private static ModelDefinition buildCatDefinition() {

        ModelBone head =
                new ModelBone(
                        "Head",
                        ModelTransform.translation(
                                new Vec3(0.0, 6.0, -4.0)
                        ),
                        List.of(),
                        List.of()
                );

        ModelBone tail =
                new ModelBone(
                        "Tail",
                        ModelTransform.translation(
                                new Vec3(0.0, 2.0, 7.0)
                        ),
                        List.of(),
                        List.of()
                );

        ModelBone body =
                new ModelBone(
                        "Body",
                        ModelTransform.IDENTITY,
                        List.of(
                                head,
                                tail
                        ),
                        List.of(
                                new ModelCuboid(
                                        "body",
                                        new Vec3(-2.0, 0.0, -4.0),
                                        new Vec3(4.0, 4.0, 8.0),
                                        new Vec2(0.0, 0.0),
                                        new Vec2(16.0, 16.0),
                                        0,
                                        false
                                )
                        )
                );

        ModelBone root =
                new ModelBone(
                        "Root",
                        ModelTransform.IDENTITY,
                        List.of(
                                body
                        ),
                        List.of()
                );

        ModelGeometry geometry =
                new ModelGeometry.Builder(
                        "main",
                        root
                ).build();

        return new ModelDefinition.Builder()
                .id("cats:black_cat")
                .geometry(geometry)
                .texture(
                        new ModelTexture(
                                "skin",
                                ResourceId.parse(
                                        "cats:textures/black_cat.png"
                                )
                        )
                )
                .build();
    }
}
