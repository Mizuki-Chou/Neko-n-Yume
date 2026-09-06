package mizukichou.nekonyume.model;

/**
 * 动画关键帧（定义态，不可变）。
 *
 * <p>
 * 变换为<b>增量</b>（相对骨骼 base 变换）——
 * 导入器在烘焙阶段已把 .bbmodel 的绝对关键帧
 * 转换为 base⁻¹ × absolute（§17 组合语义）。
 * </p>
 */
public final class ModelKeyframe {

    private final double timeSeconds;

    private final ModelTransform transform;

    public ModelKeyframe(
            double timeSeconds,
            ModelTransform transform
    ) {

        if (!Double.isFinite(timeSeconds) ||
                timeSeconds < 0.0) {

            throw new IllegalArgumentException(
                    "timeSeconds must be finite and non-negative: "
                            + timeSeconds
            );
        }

        if (transform == null) {
            throw new IllegalArgumentException(
                    "transform must not be null."
            );
        }

        this.timeSeconds = timeSeconds;
        this.transform = transform;
    }

    public double getTimeSeconds() {

        return timeSeconds;
    }

    public ModelTransform getTransform() {

        return transform;
    }

    @Override
    public String toString() {

        return "ModelKeyframe{" +
                timeSeconds + "s}";
    }
}
