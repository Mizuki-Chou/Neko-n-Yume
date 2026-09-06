package mizukichou.nekonyume.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 动画单通道（定义态，不可变）。
 *
 * <p>
 * 关键帧按时间升序（构造时排序）。采样按帧间插值类型：
 * {@link KeyframeInterpolation#LINEAR} 分量线性、
 * {@link KeyframeInterpolation#CATMULLROM} 分量 Catmull-Rom
 * （均匀参数化、端点钳制，与 Blockbench 一致）。
 * 越界取边界帧值。
 * </p>
 */
public final class AnimationChannel {

    private final List<ChannelKeyframe> keyframes;

    public AnimationChannel(
            List<ChannelKeyframe> keyframes
    ) {

        if (keyframes == null ||
                keyframes.isEmpty()) {

            throw new IllegalArgumentException(
                    "keyframes must not be empty."
            );
        }

        List<ChannelKeyframe> copy =
                new ArrayList<>(keyframes);

        for (ChannelKeyframe keyframe :
                copy) {

            if (keyframe == null) {

                throw new IllegalArgumentException(
                        "keyframes must not contain null."
                );
            }
        }

        copy.sort(
                Comparator.comparingDouble(
                        ChannelKeyframe::getTimeSeconds
                )
        );

        /*
         * 相同时间帧：保留先出现者，其余忽略
         * （Blockbench 允许同一时间多帧，后者为冗余）。
         */
        List<ChannelKeyframe> deduped =
                new ArrayList<>(copy.size());

        double previousTime =
                Double.NEGATIVE_INFINITY;

        for (ChannelKeyframe keyframe :
                copy) {

            double time =
                    keyframe.getTimeSeconds();

            if (time > previousTime + 1e-9) {

                deduped.add(keyframe);
                previousTime = time;
            }
        }

        this.keyframes =
                Collections.unmodifiableList(
                        deduped
                );
    }

    /**
     * 关键帧（不可修改视图，时间升序、去重）。
     */
    public List<ChannelKeyframe> getKeyframes() {

        return keyframes;
    }

    /**
     * 采样时间 t（秒）处的通道值（三分量）。
     */
    public Vec3 sample(
            double timeSeconds
    ) {

        List<ChannelKeyframe> frames =
                keyframes;

        if (frames.size() == 1) {

            return frames.get(0)
                    .getValue();
        }

        ChannelKeyframe first =
                frames.get(0);

        if (timeSeconds <= first.getTimeSeconds()) {

            return first.getValue();
        }

        ChannelKeyframe last =
                frames.get(frames.size() - 1);

        if (timeSeconds >= last.getTimeSeconds()) {

            return last.getValue();
        }

        /*
         * 0.9.0更新：二分定位区间
         * （关键帧按时间升序）。
         */
        int low =
                1;

        int high =
                frames.size() - 1;

        while (low <= high) {

            int mid =
                    (low + high) >>> 1;

            if (timeSeconds >
                    frames.get(mid)
                            .getTimeSeconds()) {

                low = mid + 1;

            } else {

                high = mid - 1;
            }
        }

        int i =
                Math.min(
                        low,
                        frames.size() - 1
                );

        {

            ChannelKeyframe right =
                    frames.get(i);

            ChannelKeyframe left =
                    frames.get(i - 1);

            double span =
                    right.getTimeSeconds() -
                            left.getTimeSeconds();

            double t =
                    span <= 1e-12
                            ? 0.0
                            : (timeSeconds - left.getTimeSeconds())
                            / span;

            if (left.getInterpolation() ==
                    KeyframeInterpolation.CATMULLROM) {

                ChannelKeyframe before =
                        i - 2 >= 0
                                ? frames.get(i - 2)
                                : left;

                ChannelKeyframe after =
                        i + 1 < frames.size()
                                ? frames.get(i + 1)
                                : right;

                return catmullRom(
                        before.getValue(),
                        left.getValue(),
                        right.getValue(),
                        after.getValue(),
                        t
                );
            }

            return Vec3.lerp(
                    left.getValue(),
                    right.getValue(),
                    t
            );
        }
    }

    /**
     * 分量 Catmull-Rom（均匀参数化，端点钳制）。
     */
    private static Vec3 catmullRom(
            Vec3 p0,
            Vec3 p1,
            Vec3 p2,
            Vec3 p3,
            double t
    ) {

        return new Vec3(
                catmullRomComponent(
                        p0.getX(),
                        p1.getX(),
                        p2.getX(),
                        p3.getX(),
                        t
                ),
                catmullRomComponent(
                        p0.getY(),
                        p1.getY(),
                        p2.getY(),
                        p3.getY(),
                        t
                ),
                catmullRomComponent(
                        p0.getZ(),
                        p1.getZ(),
                        p2.getZ(),
                        p3.getZ(),
                        t
                )
        );
    }

    private static double catmullRomComponent(
            double p0,
            double p1,
            double p2,
            double p3,
            double t
    ) {

        double t2 =
                t * t;

        double t3 =
                t2 * t;

        return 0.5 *
                ((2.0 * p1) +
                        (-p0 + p2) * t +
                        (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 +
                        (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }
}
