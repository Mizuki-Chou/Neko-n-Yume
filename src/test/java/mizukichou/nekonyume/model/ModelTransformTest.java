package mizukichou.nekonyume.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 变换组合语义验证（手算基准值）。
 */
class ModelTransformTest {

    private static final double EPS = 1e-9;

    @Test
    void identityComposeReturnsOther() {

        assertEquals(
                ModelTransform.translation(
                        new Vec3(1.0, 2.0, 3.0)
                ),
                ModelTransform.IDENTITY.compose(
                        ModelTransform.translation(
                                new Vec3(1.0, 2.0, 3.0)
                        )
                )
        );

        assertEquals(
                ModelTransform.translation(
                        new Vec3(1.0, 2.0, 3.0)
                ),
                ModelTransform.translation(
                        new Vec3(1.0, 2.0, 3.0)
                ).compose(
                        ModelTransform.IDENTITY
                )
        );
    }

    @Test
    void translationCompositionAdds() {

        ModelTransform combined =
                ModelTransform.translation(
                        new Vec3(1.0, 2.0, 3.0)
                ).compose(
                        ModelTransform.translation(
                                new Vec3(10.0, 20.0, 30.0)
                        )
                );

        assertEquals(
                new Vec3(11.0, 22.0, 33.0),
                combined.getTranslation()
        );
    }

    @Test
    void rotationThenTranslationOrder() {

        /*
         * base = 绕 Y 转 90°；anim = 平移 (1, 0, 0)。
         * 组合（先 anim 后 base）：平移向量先被旋转，
         * (1,0,0) 绕 Y 90° → (0,0,-1)。
         */
        ModelTransform base =
                ModelTransform.rotation(
                        Quaternion.fromYAxisDeg(90.0)
                );

        ModelTransform anim =
                ModelTransform.translation(
                        new Vec3(1.0, 0.0, 0.0)
                );

        ModelTransform combined =
                base.compose(anim);

        assertEquals(
                0.0,
                combined.getTranslation().getX(),
                EPS
        );
        assertEquals(
                -1.0,
                combined.getTranslation().getZ(),
                EPS
        );
    }

    @Test
    void scaleCompositionMultipliesPerComponent() {

        ModelTransform combined =
                new ModelTransform(
                        Vec3.ZERO,
                        Quaternion.IDENTITY,
                        new Vec3(2.0, 3.0, 4.0)
                ).compose(
                        new ModelTransform(
                                Vec3.ZERO,
                                Quaternion.IDENTITY,
                                new Vec3(5.0, 6.0, 7.0)
                        )
                );

        assertEquals(
                new Vec3(10.0, 18.0, 28.0),
                combined.getScale()
        );
    }

    @Test
    void compositionIsNotCommutative() {

        ModelTransform rotate =
                ModelTransform.rotation(
                        Quaternion.fromYAxisDeg(90.0)
                );

        ModelTransform translate =
                ModelTransform.translation(
                        new Vec3(1.0, 0.0, 0.0)
                );

        assertNotEquals(
                rotate.compose(translate),
                translate.compose(rotate)
        );
    }

    @Test
    void identityDetection() {

        assertTrue(ModelTransform.IDENTITY.isIdentity());

        assertFalse(
                ModelTransform.translation(
                        new Vec3(1.0, 0.0, 0.0)
                ).isIdentity()
        );

        assertFalse(
                ModelTransform.rotation(
                        Quaternion.fromYAxisDeg(30.0)
                ).isIdentity()
        );
    }
}
