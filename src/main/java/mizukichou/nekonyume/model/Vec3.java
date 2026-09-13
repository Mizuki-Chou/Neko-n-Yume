package mizukichou.nekonyume.model;

/**
 * 三维向量（模型坐标，BB units）。
 *
 * <p>
 * 不可变。构造时拒绝非有限值（NaN / Infinity）。
 * 不提供任何原地修改方法。
 * </p>
 */
public final class Vec3 {

    public static final Vec3 ZERO =
            new Vec3(0.0, 0.0, 0.0);

    public static final Vec3 ONE =
            new Vec3(1.0, 1.0, 1.0);

    private final double x;

    private final double y;

    private final double z;

    public Vec3(
            double x,
            double y,
            double z
    ) {

        if (!Double.isFinite(x) ||
                !Double.isFinite(y) ||
                !Double.isFinite(z)) {

            throw new IllegalArgumentException(
                    "Vec3 components must be finite."
            );
        }

        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {

        return x;
    }

    public double getY() {

        return y;
    }

    public double getZ() {

        return z;
    }

    /**
     * 长度平方（距离比较用，避免开方）。
     */
    public double lengthSquared() {

        return x * x + y * y + z * z;
    }

    /**
     * 分量乘法（缩放组合用）。
     */
    public Vec3 multiply(
            Vec3 other
    ) {

        return new Vec3(
                x * other.x,
                y * other.y,
                z * other.z
        );
    }

    /**
     * 向量加法。
     */
    public Vec3 add(
            Vec3 other
    ) {

        return new Vec3(
                x + other.x,
                y + other.y,
                z + other.z
        );
    }

    /**
     * 向量减法。
     */
    public Vec3 subtract(
            Vec3 other
    ) {

        return new Vec3(
                x - other.x,
                y - other.y,
                z - other.z
        );
    }

    /**
     * 按轴取分量（0=x, 1=y, 2=z）。
     */
    public double component(
            int axis
    ) {

        return switch (axis) {

            case 0 -> x;
            case 1 -> y;
            case 2 -> z;
            default ->
                    throw new IllegalArgumentException(
                            "axis must be 0..2, got "
                                    + axis
                    );
        };
    }

    /**
     * 分量线性插值。
     */
    public static Vec3 lerp(
            Vec3 from,
            Vec3 to,
            double t
    ) {

        return new Vec3(
                from.x + (to.x - from.x) * t,
                from.y + (to.y - from.y) * t,
                from.z + (to.z - from.z) * t
        );
    }

    @Override
    public boolean equals(
            Object other
    ) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof Vec3 that)) {
            return false;
        }

        return Double.compare(x, that.x) == 0 &&
                Double.compare(y, that.y) == 0 &&
                Double.compare(z, that.z) == 0;
    }

    @Override
    public int hashCode() {

        return Double.hashCode(x) * 31 * 31 +
                Double.hashCode(y) * 31 +
                Double.hashCode(z);
    }

    @Override
    public String toString() {

        return "Vec3{" + x + ", " + y + ", " + z + "}";
    }
}
