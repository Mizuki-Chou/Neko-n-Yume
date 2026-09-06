package mizukichou.nekonyume.model;

/**
 * 动画通道关键帧（定义态，不可变）。
 *
 * <p>
 * 值语义按通道区分：
 * rotation 通道为 Euler 度分量、position 通道为 BB units、
 * scale 通道为各轴缩放分量。
 * {@code interpolation} 描述从本帧到下一帧的过渡曲线
 * （Blockbench 4.x 的插值在 data_point 级、5.x 在 keyframe 级，
 * 适配器统一归一为本结构）。
 * </p>
 */
public final class ChannelKeyframe {

    private final double timeSeconds;

    private final Vec3 value;

    private final KeyframeInterpolation interpolation;

    public ChannelKeyframe(
            double timeSeconds,
            Vec3 value,
            KeyframeInterpolation interpolation
    ) {

        if (!Double.isFinite(timeSeconds) ||
                timeSeconds < 0.0) {

            throw new IllegalArgumentException(
                    "timeSeconds must be finite and non-negative: "
                            + timeSeconds
            );
        }

        if (value == null) {

            throw new IllegalArgumentException(
                    "value must not be null."
            );
        }

        this.timeSeconds = timeSeconds;
        this.value = value;
        this.interpolation =
                interpolation == null
                        ? KeyframeInterpolation.LINEAR
                        : interpolation;
    }

    public double getTimeSeconds() {

        return timeSeconds;
    }

    public Vec3 getValue() {

        return value;
    }

    public KeyframeInterpolation getInterpolation() {

        return interpolation;
    }

    @Override
    public String toString() {

        return "ChannelKeyframe{" +
                timeSeconds + "s, " +
                interpolation + "}";
    }
}
