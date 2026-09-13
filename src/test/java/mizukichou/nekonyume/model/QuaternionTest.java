package mizukichou.nekonyume.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 四元数数学验证（手算基准值）。
 */
class QuaternionTest {

    private static final double EPS = 1e-9;

    @Test
    void yAxisRotationMapsXToMinusZ() {

        /*
         * 绕 +Y 旋转 90°（右手系，从上方看顺时针）：
         * +X → -Z（与 Bukkit yaw 0→+Z、90→-X 的约定一致）。
         */
        Quaternion q =
                Quaternion.fromYAxisDeg(90.0);

        Vec3 rotated =
                q.rotate(
                        new Vec3(1.0, 0.0, 0.0)
                );

        assertEquals(0.0, rotated.getX(), EPS);
        assertEquals(0.0, rotated.getY(), EPS);
        assertEquals(-1.0, rotated.getZ(), EPS);
    }

    @Test
    void yAxisRotationKeepsUpVector() {

        Quaternion q =
                Quaternion.fromYAxisDeg(137.0);

        Vec3 rotated =
                q.rotate(
                        new Vec3(0.0, 1.0, 0.0)
                );

        assertEquals(0.0, rotated.getX(), EPS);
        assertEquals(1.0, rotated.getY(), EPS);
        assertEquals(0.0, rotated.getZ(), EPS);
    }

    @Test
    void compositionOfSameAxisAddsAngles() {

        Quaternion combined =
                Quaternion.fromYAxisDeg(90.0)
                        .multiply(
                                Quaternion.fromYAxisDeg(90.0)
                        );

        Vec3 rotated =
                combined.rotate(
                        new Vec3(1.0, 0.0, 0.0)
                );

        assertEquals(-1.0, rotated.getX(), EPS);
        assertEquals(0.0, rotated.getZ(), EPS);
    }

    @Test
    void identityRotatesNothing() {

        Vec3 v =
                new Vec3(3.0, -2.0, 7.0);

        assertEquals(
                v,
                Quaternion.IDENTITY.rotate(v)
        );
    }

    @Test
    void multiplicationIsNotCommutative() {

        Quaternion x =
                Quaternion.fromAxisAngleDeg(
                        new Vec3(1.0, 0.0, 0.0),
                        90.0
                );

        Quaternion y =
                Quaternion.fromYAxisDeg(90.0);

        assertNotEquals(
                x.multiply(y),
                y.multiply(x)
        );
    }

    @Test
    void conjugateTimesSelfIsIdentity() {

        Quaternion q =
                Quaternion.fromAxisAngleDeg(
                        new Vec3(1.0, 2.0, -1.0),
                        73.0
                );

        Quaternion product =
                q.normalize()
                        .multiply(
                                q.normalize()
                                        .conjugate()
                        );

        assertTrue(product.isIdentity());
    }

    @Test
    void normalizePreservesRotation() {

        Quaternion q =
                Quaternion.fromAxisAngleDeg(
                        new Vec3(1.0, 1.0, 0.0),
                        30.0
                );

        Vec3 v =
                new Vec3(1.0, 0.0, 0.0);

        Vec3 a =
                q.rotate(v);

        Vec3 b =
                q.normalize().rotate(v);

        assertEquals(a.getX(), b.getX(), EPS);
        assertEquals(a.getY(), b.getY(), EPS);
        assertEquals(a.getZ(), b.getZ(), EPS);
    }

    @Test
    void rejectsDegenerateInputs() {

        assertThrows(
                IllegalArgumentException.class,
                () -> Quaternion.fromAxisAngleDeg(
                        Vec3.ZERO,
                        45.0
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Quaternion.fromAxisAngleDeg(
                        new Vec3(1.0, 0.0, 0.0),
                        Double.NaN
                )
        );
    }

    @Test
    void normalizingIdentityKeepsIdentity() {

        assertTrue(
                Quaternion.IDENTITY
                        .normalize()
                        .isIdentity()
        );

        assertFalse(
                Quaternion.fromYAxisDeg(45.0)
                        .isIdentity()
        );
    }
}
