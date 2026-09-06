package mizukichou.nekonyume.model;

/**
 * 单骨骼动画轨（定义态，不可变，通道级）。
 *
 * <p>
 * 三通道均为<b>绝对空间</b>值（Blockbench 语义）：
 * rotation 为 Euler 度、position 为 BB units、
 * scale 为各轴缩放分量。烘焙阶段已为空通道填充
 * 基准单帧（rotation = 骨骼绝对旋转、position = 骨骼
 * 绝对位置、scale = 1），因此三通道恒非空、采样自足。
 * </p>
 *
 * <p>
 * 采样结果 = {@code base⁻¹ × absolute(t)}（§17 增量语义）。
 * 插值按通道帧间类型（linear / catmullrom，知识包 P1-19）。
 * </p>
 */
public final class ModelBoneAnimation {

    private final String boneName;

    private final AnimationChannel rotationChannel;

    private final AnimationChannel positionChannel;

    private final AnimationChannel scaleChannel;

    private final ModelTransform baseInverse;

    public ModelBoneAnimation(
            String boneName,
            AnimationChannel rotationChannel,
            AnimationChannel positionChannel,
            AnimationChannel scaleChannel,
            ModelTransform baseInverse
    ) {

        if (boneName == null ||
                boneName.isBlank()) {

            throw new IllegalArgumentException(
                    "boneName must not be blank."
            );
        }

        if (rotationChannel == null ||
                positionChannel == null ||
                scaleChannel == null ||
                baseInverse == null) {

            throw new IllegalArgumentException(
                    "channels and baseInverse must not be null."
            );
        }

        this.boneName = boneName;
        this.rotationChannel = rotationChannel;
        this.positionChannel = positionChannel;
        this.scaleChannel = scaleChannel;
        this.baseInverse = baseInverse;
    }

    public String getBoneName() {

        return boneName;
    }

    public AnimationChannel getRotationChannel() {

        return rotationChannel;
    }

    public AnimationChannel getPositionChannel() {

        return positionChannel;
    }

    public AnimationChannel getScaleChannel() {

        return scaleChannel;
    }

    /**
     * 采样该骨骼在时间 t（秒）处的动画增量变换。
     *
     * <p>
     * 各通道独立插值（linear / catmullrom）得绝对变换，
     * 再左乘 base⁻¹ 转增量。
     * </p>
     */
    public ModelTransform sample(
            double timeSeconds
    ) {

        Vec3 euler =
                rotationChannel.sample(
                        timeSeconds
                );

        Vec3 position =
                positionChannel.sample(
                        timeSeconds
                );

        Vec3 scale =
                scaleChannel.sample(
                        timeSeconds
                );

        ModelTransform absolute =
                new ModelTransform(
                        position,
                        Quaternion.fromEulerDegXYZ(
                                euler.getX(),
                                euler.getY(),
                                euler.getZ()
                        ),
                        scale
                );

        return baseInverse.compose(
                absolute
        );
    }

    @Override
    public String toString() {

        return "ModelBoneAnimation{" +
                boneName + "}";
    }
}
