package mizukichou.nekonyume.model;

import java.util.Objects;

/**
 * 模型变换（TRS：平移 / 旋转 / 缩放）。
 *
 * <p>
 * 不可变。平移以 BB units 计；缩放 1.0 表示原始大小。
 * 运行时变换组合（架构 §17）：
 * {@code finalTransform = baseTransform × animationTransform}，
 * 组合语义与矩阵乘法一致：先应用右侧，再应用左侧。
 * </p>
 *
 * <p>
 * 已知限制：非均匀缩放与旋转组合时按 TRS 近似
 * （严格数学上二者不可交换）。Blockbench 模型约定
 * 骨骼缩放保持 1（或均匀缩放），该场景实际不出现。
 * </p>
 */
public final class ModelTransform {

    /**
     * 单位变换（无平移、无旋转、原始缩放）。
     */
    public static final ModelTransform IDENTITY =
            new ModelTransform(
                    Vec3.ZERO,
                    Quaternion.IDENTITY,
                    new Vec3(1.0, 1.0, 1.0)
            );

    private final Vec3 translation;

    private final Quaternion rotation;

    private final Vec3 scale;

    public ModelTransform(
            Vec3 translation,
            Quaternion rotation,
            Vec3 scale
    ) {

        if (translation == null ||
                rotation == null ||
                scale == null) {

            throw new IllegalArgumentException(
                    "Transform components must not be null."
            );
        }

        this.translation = translation;
        this.rotation = rotation;
        this.scale = scale;
    }

    /**
     * 纯平移。
     */
    public static ModelTransform translation(
            Vec3 translation
    ) {

        return new ModelTransform(
                translation,
                Quaternion.IDENTITY,
                new Vec3(1.0, 1.0, 1.0)
        );
    }

    /**
     * 纯旋转。
     */
    public static ModelTransform rotation(
            Quaternion rotation
    ) {

        return new ModelTransform(
                Vec3.ZERO,
                rotation,
                new Vec3(1.0, 1.0, 1.0)
        );
    }

    /**
     * 组合：{@code this × other}（先 {@code other} 后 {@code this}）。
     */
    public ModelTransform compose(
            ModelTransform other
    ) {

        if (other == null) {
            throw new IllegalArgumentException(
                    "other must not be null."
            );
        }

        /*
         * v' = T1·R1·S1·(T2·R2·S2·v)
         *    = t1 + R1(s1 ⊙ t2) + R1·R2·(s1 ⊙ s2)v
         * （TRS 近似，见类注释。）
         */
        Vec3 combinedTranslation =
                translation.add(
                        rotation.rotate(
                                other.translation.multiply(
                                        scale
                                )
                        )
                );

        Quaternion combinedRotation =
                rotation.multiply(
                        other.rotation
                ).normalize();

        Vec3 combinedScale =
                scale.multiply(
                        other.scale
                );

        return new ModelTransform(
                combinedTranslation,
                combinedRotation,
                combinedScale
        );
    }

    /**
     * 逆变换（TRS 分解下）：
     * t' = -R⁻¹(t ⊘ s)，R' = R⁻¹，s' = 1 ⊘ s。
     *
     * <p>
     * 仅限可逆变换使用（scale 分量非零）；
     * 用于动画烘焙：增量 = base⁻¹ × 绝对关键帧。
     * </p>
     */
    public ModelTransform invert() {

        /*
         * P1-16：零缩放分量不可逆——提前给出明确错误
         * （否则 1/0 会产生 Infinity，被 Vec3 构造器
         * 以隐晦消息拒绝）。
         */
        if (scale.getX() == 0.0 ||
                scale.getY() == 0.0 ||
                scale.getZ() == 0.0) {

            throw new IllegalStateException(
                    "Cannot invert a transform with zero scale: "
                            + scale
            );
        }

        Quaternion inverseRotation =
                rotation.inverse();

        Vec3 inverseScale =
                new Vec3(
                        1.0 / scale.getX(),
                        1.0 / scale.getY(),
                        1.0 / scale.getZ()
                );

        Vec3 scaledTranslation =
                new Vec3(
                        translation.getX() / scale.getX(),
                        translation.getY() / scale.getY(),
                        translation.getZ() / scale.getZ()
                );

        Vec3 rotated =
                inverseRotation.rotate(
                        scaledTranslation
                );

        return new ModelTransform(
                new Vec3(
                        -rotated.getX(),
                        -rotated.getY(),
                        -rotated.getZ()
                ),
                inverseRotation,
                inverseScale
        );
    }

    /**
     * 插值：translation/scale 分量线性插值，
     * rotation 四元数 nlerp（动画采样/混合用）。
     */
    public static ModelTransform interpolate(
            ModelTransform from,
            ModelTransform to,
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

        return new ModelTransform(
                new Vec3(
                        from.translation.getX() +
                                (to.translation.getX() -
                                        from.translation.getX()) * t,
                        from.translation.getY() +
                                (to.translation.getY() -
                                        from.translation.getY()) * t,
                        from.translation.getZ() +
                                (to.translation.getZ() -
                                        from.translation.getZ()) * t
                ),
                Quaternion.nlerp(
                        from.rotation,
                        to.rotation,
                        t
                ),
                new Vec3(
                        from.scale.getX() +
                                (to.scale.getX() -
                                        from.scale.getX()) * t,
                        from.scale.getY() +
                                (to.scale.getY() -
                                        from.scale.getY()) * t,
                        from.scale.getZ() +
                                (to.scale.getZ() -
                                        from.scale.getZ()) * t
                )
        );
    }

    /**
     * 是否为无任何效果的变换。
     */
    public boolean isIdentity() {

        return rotation.isIdentity() &&
                translation.equals(Vec3.ZERO) &&
                scale.equals(
                        new Vec3(1.0, 1.0, 1.0)
                );
    }

    public Vec3 getTranslation() {

        return translation;
    }

    public Quaternion getRotation() {

        return rotation;
    }

    public Vec3 getScale() {

        return scale;
    }

    @Override
    public boolean equals(
            Object other
    ) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof ModelTransform that)) {
            return false;
        }

        return translation.equals(that.translation) &&
                rotation.equals(that.rotation) &&
                scale.equals(that.scale);
    }

    @Override
    public int hashCode() {

        return Objects.hash(
                translation,
                rotation,
                scale
        );
    }

    @Override
    public String toString() {

        return "ModelTransform{" +
                "t=" + translation +
                ", r=" + rotation +
                ", s=" + scale + "}";
    }
}
