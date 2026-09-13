package mizukichou.nekonyume.model;

/**
 * 二维向量（模型坐标，BB units）。
 *
 * <p>
 * 不可变。主要用于立方体 UV 数据。
 * 构造时拒绝非有限值（NaN / Infinity）。
 * </p>
 */
public final class Vec2 {

    private final double x;

    private final double y;

    public Vec2(
            double x,
            double y
    ) {

        if (!Double.isFinite(x) ||
                !Double.isFinite(y)) {

            throw new IllegalArgumentException(
                    "Vec2 components must be finite."
            );
        }

        this.x = x;
        this.y = y;
    }

    public double getX() {

        return x;
    }

    public double getY() {

        return y;
    }

    @Override
    public boolean equals(
            Object other
    ) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof Vec2 that)) {
            return false;
        }

        return Double.compare(x, that.x) == 0 &&
                Double.compare(y, that.y) == 0;
    }

    @Override
    public int hashCode() {

        return Double.hashCode(x) * 31 +
                Double.hashCode(y);
    }

    @Override
    public String toString() {

        return "Vec2{" + x + ", " + y + "}";
    }
}
