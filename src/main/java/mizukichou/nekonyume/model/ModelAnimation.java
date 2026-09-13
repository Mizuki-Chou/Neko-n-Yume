package mizukichou.nekonyume.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型动画（定义态，不可变，架构 §19/§20）。
 *
 * <p>
 * 名称约定（§20）：动画名与适配器标准名对应
 * （{@code idle / walk / sit / attack / hurt / eat}），
 * 模型作者按此命名；未提供的动画按无操作处理。
 * </p>
 *
 * <p>
 * 循环语义（0.9.0更新）：{@link LoopMode}
 * LOOP 循环；HOLD 播完保持最后一帧；
 * ONCE 播完回到基础姿态。
 * </p>
 */
public final class ModelAnimation {

    private final String name;

    private final double durationSeconds;

    private final LoopMode loopMode;

    private final Map<String, ModelBoneAnimation> boneAnimations;

    public ModelAnimation(
            String name,
            double durationSeconds,
            LoopMode loopMode,
            Map<String, ModelBoneAnimation> boneAnimations
    ) {

        if (name == null ||
                name.isBlank()) {

            throw new IllegalArgumentException(
                    "name must not be blank."
            );
        }

        if (!Double.isFinite(durationSeconds) ||
                durationSeconds <= 0.0) {

            throw new IllegalArgumentException(
                    "durationSeconds must be finite and positive: "
                            + durationSeconds
            );
        }

        if (loopMode == null) {

            throw new IllegalArgumentException(
                    "loopMode must not be null."
            );
        }

        if (boneAnimations == null ||
                boneAnimations.isEmpty()) {

            throw new IllegalArgumentException(
                    "boneAnimations must not be empty."
            );
        }

        Map<String, ModelBoneAnimation> copy =
                new LinkedHashMap<>();

        for (Map.Entry<String, ModelBoneAnimation> entry :
                boneAnimations.entrySet()) {

            if (entry.getKey() == null ||
                    entry.getValue() == null) {

                throw new IllegalArgumentException(
                        "boneAnimations must not contain null."
                );
            }

            if (!entry.getKey()
                    .equals(entry.getValue().getBoneName())) {

                throw new IllegalArgumentException(
                        "Bone animation key '"
                                + entry.getKey()
                                + "' does not match bone name '"
                                + entry.getValue().getBoneName()
                                + "'."
                );
            }

            copy.put(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        this.name = name;
        this.durationSeconds = durationSeconds;
        this.loopMode = loopMode;
        this.boneAnimations =
                Collections.unmodifiableMap(copy);
    }

    public String getName() {

        return name;
    }

    public double getDurationSeconds() {

        return durationSeconds;
    }

    public LoopMode getLoopMode() {

        return loopMode;
    }

    /**
     * 是否循环（LOOP）。
     */
    public boolean isLooping() {

        return loopMode == LoopMode.LOOP;
    }

    /**
     * 骨骼名 → 动画轨（不可修改视图）。
     */
    public Map<String, ModelBoneAnimation> getBoneAnimations() {

        return boneAnimations;
    }

    /**
     * 指定骨骼的动画轨；无则返回 null。
     */
    public ModelBoneAnimation getBoneAnimation(
            String boneName
    ) {

        if (boneName == null) {
            return null;
        }

        return boneAnimations.get(boneName);
    }

    /**
     * 时间归一化：循环动画取模，非循环钳制到时长。
     */
    public double normalizeTime(
            double timeSeconds
    ) {

        if (timeSeconds <= 0.0) {
            return 0.0;
        }

        if (isLooping()) {

            double mod =
                    timeSeconds % durationSeconds;

            return mod < 0.0
                    ? mod + durationSeconds
                    : mod;
        }

        return Math.min(
                timeSeconds,
                durationSeconds
        );
    }

    /**
     * 按时间采样全部骨骼的动画增量变换。
     */
    public Map<String, ModelTransform> sample(
            double timeSeconds
    ) {

        double normalized =
                normalizeTime(timeSeconds);

        Map<String, ModelTransform> result =
                new LinkedHashMap<>();

        for (Map.Entry<String, ModelBoneAnimation> entry :
                boneAnimations.entrySet()) {

            result.put(
                    entry.getKey(),
                    entry.getValue()
                            .sample(normalized)
            );
        }

        return result;
    }

    @Override
    public String toString() {

        return "ModelAnimation{" + name +
                ", " + durationSeconds + "s" +
                ", " + loopMode + "}";
    }
}
