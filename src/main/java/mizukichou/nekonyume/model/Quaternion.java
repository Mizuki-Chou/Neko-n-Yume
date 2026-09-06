package mizukichou.nekonyume.model;

/**
 * 四元数（Hamilton 约定：x, y, z, w）。
 *
 * <p>
 * 不可变。运行时旋转的数学表示（架构 §18）：
 * 多个系统之间的旋转组合一律通过四元数乘法，
 * 禁止 Euler 角累加。
 * </p>
 *
 * <p>
 * 与 Bukkit / Blockbench 的 Euler 表示仅在边界转换
 * （导入器、渲染器）——本类不提供 Euler 转换。
 * </p>
 */
public final class Quaternion {

    /**
     * 单位四元数（无旋转）。
     */
    public static final Quaternion IDENTITY =
            new Quaternion(0.0, 0.0, 0.0, 1.0);

    private static final double EPSILON = 1e-9;

    private final double x;

    private final double y;

    private final double z;

    private final double w;

    private Quaternion(
            double x,
            double y,
            double z,
            double w
    ) {

        if (!Double.isFinite(x) ||
                !Double.isFinite(y) ||
                !Double.isFinite(z) ||
                !Double.isFinite(w)) {

            throw new IllegalArgumentException(
                    "Quaternion components must be finite."
            );
        }

        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    /**
     * 绕单位轴旋转 {@code angleDeg} 度。
     *
     * <p>
     * 轴将被归一化；零向量轴或非有限角度视为非法输入，
     * 抛出 {@link IllegalArgumentException}。
     * </p>
     */
    public static Quaternion fromAxisAngleDeg(
            Vec3 axis,
            double angleDeg
    ) {

        if (axis == null ||
                !Double.isFinite(angleDeg)) {

            throw new IllegalArgumentException(
                    "Axis and angle must be valid."
            );
        }

        double axisLength =
                Math.sqrt(
                        axis.getX() * axis.getX() +
                                axis.getY() * axis.getY() +
                                axis.getZ() * axis.getZ()
                );

        if (axisLength < EPSILON) {
            throw new IllegalArgumentException(
                    "Rotation axis must not be zero."
            );
        }

        double halfRad =
                Math.toRadians(angleDeg) / 2.0;

        double sinHalf =
                Math.sin(halfRad);

        return new Quaternion(
                axis.getX() / axisLength * sinHalf,
                axis.getY() / axisLength * sinHalf,
                axis.getZ() / axisLength * sinHalf,
                Math.cos(halfRad)
        );
    }

    /**
     * 绕 Y 轴旋转 {@code angleDeg} 度（模型水平朝向最常用）。
     */
    public static Quaternion fromYAxisDeg(
            double angleDeg
    ) {

        return fromAxisAngleDeg(
                new Vec3(0.0, 1.0, 0.0),
                angleDeg
        );
    }

    /**
     * 从 Euler 角构造（仅格式边界使用，架构 §18）。
     *
     * <p>
     * 旋转顺序 Z→Y→X（先绕 Z、再绕 Y、最后绕 X），
     * 与 Minecraft java model / Blockbench 的
     * {@code rotation: [x, y, z]} 语义一致。
     * 运行时系统内部仍以四元数组合，不得累加 Euler。
     * </p>
     */
    public static Quaternion fromEulerDegXYZ(
            double xDeg,
            double yDeg,
            double zDeg
    ) {

        Quaternion x =
                fromAxisAngleDeg(
                        new Vec3(1.0, 0.0, 0.0),
                        xDeg
                );

        Quaternion y =
                fromAxisAngleDeg(
                        new Vec3(0.0, 1.0, 0.0),
                        yDeg
                );

        Quaternion z =
                fromAxisAngleDeg(
                        new Vec3(0.0, 0.0, 1.0),
                        zDeg
                );

        return x.multiply(y)
                .multiply(z);
    }

    /**
     * 转 Euler 角（仅格式边界使用，架构 §18），
     * 与 {@link #fromEulerDegXYZ} 互为逆运算
     * （Z→Y→X 顺序约定）。单位四元数前提。
     */
    public Vec3 toEulerDegXYZ() {

        double r02 =
                2.0 * (this.x * this.z + this.w * this.y);

        double r12 =
                2.0 * (this.y * this.z - this.w * this.x);

        double r00 =
                1.0 - 2.0 * (this.y * this.y + this.z * this.z);

        double r01 =
                2.0 * (this.x * this.y - this.w * this.z);

        double r22 =
                1.0 - 2.0 * (this.x * this.x + this.y * this.y);

        double yRad =
                Math.asin(
                        clamp(
                                r02,
                                -1.0,
                                1.0
                        )
                );

        double xRad =
                Math.atan2(
                        -r12,
                        r22
                );

        double zRad =
                Math.atan2(
                        -r01,
                        r00
                );

        return new Vec3(
                Math.toDegrees(xRad),
                Math.toDegrees(yRad),
                Math.toDegrees(zRad)
        );
    }

    private static double clamp(
            double value,
            double min,
            double max
    ) {

        return Math.max(
                min,
                Math.min(max, value)
        );
    }

    /**
     * Hamilton 积：{@code this × other}。
     *
     * <p>
     * 语义：先应用 {@code other}，再应用 {@code this}
     * （对向量作用时与矩阵乘法顺序一致）。
     * </p>
     */
    public Quaternion multiply(
            Quaternion other
    ) {

        if (other == null) {
            throw new IllegalArgumentException(
                    "other must not be null."
            );
        }

        return new Quaternion(
                w * other.x + x * other.w +
                        y * other.z - z * other.y,
                w * other.y - x * other.z +
                        y * other.w + z * other.x,
                w * other.z + x * other.y -
                        y * other.x + z * other.w,
                w * other.w - x * other.x -
                        y * other.y - z * other.z
        );
    }

    /**
     * 归一化。零长度四元数返回 {@link #IDENTITY}
     * （防御：退化输入不抛出、可预测）。
     */
    public Quaternion normalize() {

        double length =
                length();

        if (length < EPSILON) {
            return IDENTITY;
        }

        return new Quaternion(
                x / length,
                y / length,
                z / length,
                w / length
        );
    }

    /**
     * 旋转一个向量：{@code v' = q · v · q⁻¹}。
     */
    public Vec3 rotate(
            Vec3 vector
    ) {

        if (vector == null) {
            throw new IllegalArgumentException(
                    "vector must not be null."
            );
        }

        Quaternion q =
                normalize();

        Quaternion p =
                new Quaternion(
                        vector.getX(),
                        vector.getY(),
                        vector.getZ(),
                        0.0
                );

        Quaternion result =
                q.multiply(p)
                        .multiply(
                                q.conjugate()
                        );

        return new Vec3(
                result.x,
                result.y,
                result.z
        );
    }

    /**
     * 共轭（对单位四元数等价于逆）。
     */
    public Quaternion conjugate() {

        return new Quaternion(
                -x,
                -y,
                -z,
                w
        );
    }

    /**
     * 模长。
     */
    public double length() {

        return Math.sqrt(
                x * x + y * y + z * z + w * w
        );
    }

    /**
     * 逆四元数：q⁻¹ = conj(q) / |q|²。
     * 退化（近零长）回退单位四元数（防御）。
     */
    public Quaternion inverse() {

        double normSquared =
                x * x + y * y + z * z + w * w;

        if (normSquared < 1e-15) {

            return IDENTITY;
        }

        double inv =
                1.0 / normSquared;

        return new Quaternion(
                -x * inv,
                -y * inv,
                -z * inv,
                w * inv
        );
    }

    /**
     * 归一化线性插值（nlerp），取短弧；
     * 动画层混合用（渲染输出前必再归一）。
     */
    public static Quaternion nlerp(
            Quaternion from,
            Quaternion to,
            double t
    ) {

        if (from == null || to == null) {
            throw new IllegalArgumentException(
                    "from and to must not be null."
            );
        }

        if (t <= 0.0) {
            return from;
        }

        if (t >= 1.0) {
            return to;
        }

        double dot =
                from.x * to.x +
                        from.y * to.y +
                        from.z * to.z +
                        from.w * to.w;

        double bx = to.x;
        double by = to.y;
        double bz = to.z;
        double bw = to.w;

        if (dot < 0.0) {

            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
        }

        double x = from.x + (bx - from.x) * t;
        double y = from.y + (by - from.y) * t;
        double z = from.z + (bz - from.z) * t;
        double w = from.w + (bw - from.w) * t;

        double length =
                Math.sqrt(
                        x * x + y * y + z * z + w * w
                );

        if (length < 1e-15) {

            return from;
        }

        return new Quaternion(
                x / length,
                y / length,
                z / length,
                w / length
        );
    }

    /**
     * 是否为无旋转（在容差内）。
     */
    public boolean isIdentity() {

        return Math.abs(x) < EPSILON &&
                Math.abs(y) < EPSILON &&
                Math.abs(z) < EPSILON &&
                Math.abs(w - 1.0) < EPSILON;
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

    public double getW() {

        return w;
    }

    @Override
    public boolean equals(
            Object other
    ) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof Quaternion that)) {
            return false;
        }

        /*
         * 0.9.0更新：equals/hashCode 契约——精确比较
         * （epsilon equality 与 Double.hashCode 组合违反
         * 契约；近似比较由调用方显式做）。
         */
        return Double.doubleToLongBits(x) ==
                Double.doubleToLongBits(that.x) &&
                Double.doubleToLongBits(y) ==
                        Double.doubleToLongBits(that.y) &&
                Double.doubleToLongBits(z) ==
                        Double.doubleToLongBits(that.z) &&
                Double.doubleToLongBits(w) ==
                        Double.doubleToLongBits(that.w);
    }

    @Override
    public int hashCode() {

        return Double.hashCode(x) * 31 * 31 * 31 +
                Double.hashCode(y) * 31 * 31 +
                Double.hashCode(z) * 31 +
                Double.hashCode(w);
    }

    @Override
    public String toString() {

        return "Quaternion{" + x + ", " + y +
                ", " + z + ", " + w + "}";
    }
}
