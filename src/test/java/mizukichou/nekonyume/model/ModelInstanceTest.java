package mizukichou.nekonyume.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ModelInstance / ModelBonePose 运行时姿态语义。
 */
class ModelInstanceTest {

    private static final double EPS = 1e-9;

    @Test
    void instancePosesMirrorDefinitionBones() {

        ModelInstance instance =
                newInstance();

        assertEquals(
                4,
                instance.boneCount()
        );

        assertNotNull(instance.getBone("Root"));
        assertNotNull(instance.getBone("Body"));
        assertNotNull(instance.getBone("Head"));
        assertNotNull(instance.getBone("Tail"));
        assertNull(instance.getBone("Nose"));
    }

    @Test
    void initialFinalTransformIsBoneLocal() {

        ModelInstance instance =
                newInstance();

        ModelBonePose head =
                instance.getBone("Head");

        assertEquals(
                head.getBone().getLocalTransform(),
                head.computeFinalTransform()
        );
    }

    @Test
    void animationTransformComposesOntoBase() {

        ModelInstance instance =
                newInstance();

        ModelBonePose head =
                instance.getBone("Head");

        /*
         * base = 平移 (0, 6, -4)；anim = 绕 Y 转 90°。
         * 组合后平移不变（旋转作用在平移上，
         * 但旋转为纯旋转时 base 平移在旋转之前）。
         *
         * 语义核对：final = base × anim，
         * 平移 = base.t（anim 无平移），
         * 旋转 = base.r × anim.r。
         */
        head.setAnimationTransform(
                ModelTransform.rotation(
                        Quaternion.fromYAxisDeg(90.0)
                )
        );

        ModelTransform finalTransform =
                head.computeFinalTransform();

        assertEquals(
                0.0,
                finalTransform.getTranslation().getX(),
                EPS
        );
        assertEquals(
                6.0,
                finalTransform.getTranslation().getY(),
                EPS
        );
        assertEquals(
                -4.0,
                finalTransform.getTranslation().getZ(),
                EPS
        );
        assertFalse(
                finalTransform.getRotation()
                        .isIdentity()
        );
    }

    @Test
    void baseTransformCanBeOverridden() {

        ModelInstance instance =
                newInstance();

        ModelBonePose tail =
                instance.getBone("Tail");

        ModelTransform crouched =
                ModelTransform.translation(
                        new Vec3(0.0, -2.0, 0.0)
                );

        tail.setBaseTransform(
                crouched
        );

        assertEquals(
                crouched,
                tail.computeFinalTransform()
        );
    }

    @Test
    void nullInputsRejected() {

        ModelInstance instance =
                newInstance();

        ModelBonePose pose =
                instance.getBone("Head");

        assertThrows(
                IllegalArgumentException.class,
                () -> pose.setAnimationTransform(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> pose.setBaseTransform(null)
        );
    }

    @Test
    void visibilityDefaultsTrue() {

        ModelInstance instance =
                newInstance();

        assertTrue(instance.isVisible());

        instance.setVisible(false);

        assertFalse(instance.isVisible());
    }

    @Test
    void sharedDefinitionAcrossInstances() {

        ModelDefinition definition =
                definition();

        ModelInstance first =
                new ModelInstance(
                        definition,
                        UUID.randomUUID()
                );

        ModelInstance second =
                new ModelInstance(
                        definition,
                        UUID.randomUUID()
                );

        assertEquals(
                definition,
                first.getDefinition()
        );
        assertEquals(
                definition,
                second.getDefinition()
        );

        /*
         * 实例姿态互不影响。
         */
        first.getBone("Head")
                .setAnimationTransform(
                        ModelTransform.rotation(
                                Quaternion.fromYAxisDeg(45.0)
                        )
                );

        assertTrue(
                second.getBone("Head")
                        .computeFinalTransform()
                        .getRotation()
                        .isIdentity()
        );
    }

    @Test
    void posesCollectionIsUnmodifiable() {

        ModelInstance instance =
                newInstance();

        assertThrows(
                UnsupportedOperationException.class,
                () -> instance.poses()
                        .clear()
        );
    }

    private static ModelInstance newInstance() {

        return new ModelInstance(
                definition(),
                UUID.randomUUID()
        );
    }

    private static ModelDefinition definition() {

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
                        List.of()
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
                .build();
    }
}
