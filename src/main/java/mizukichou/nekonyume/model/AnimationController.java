package mizukichou.nekonyume.model;

import java.util.Map;

/**
 * 动画控制器（运行时，每实例一份，主线程专用）。
 *
 * <p>
 * 职责（§19）：动画不硬编码进实体逻辑；本控制器独立采样
 * 并写入 {@link ModelBonePose}。切动画时交叉淡化（§22）
 * 0.15 秒；循环动画按时长取模，非循环停最后一帧——
 * 回退到 idle 由适配器重新下发信号驱动。
 * </p>
 *
 * <p>
 * 时间源为单调时钟（nanoTime），不受系统时钟回拨影响。
 * </p>
 */
public final class AnimationController {

    /**
     * 交叉淡化时长（秒），§22：0.1~0.2。
     */
    private static final double CROSSFADE_SECONDS = 0.15;

    private final ModelInstance instance;

    private String currentName;

    private double currentTimeSeconds;

    private String targetName;

    private double targetTimeSeconds;

    private boolean blending;

    private double blendStartSeconds;

    private double lastTickSeconds;

    private boolean firstTick = true;

    /**
     * 非循环动画已播完：姿态稳定，后续 tick 零操作
     * （直到下次 play / stop）。
     */
    private boolean settled;

    /**
     * P1-22：blend 中切第三个动画时的 from 快照——
     * 冻结当前屏幕混合结果（骨骼名 → 动画增量），
     * 新 blend 从它开始而非上一动画身份。
     */
    private Map<String, ModelTransform> frozenPose;

    public AnimationController(
            ModelInstance instance
    ) {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "instance must not be null."
            );
        }

        this.instance = instance;
    }

    /**
     * 请求播放动画（真实时钟入口）。
     *
     * <p>
     * 动画不存在 / 与当前相同 → 忽略；
     * 已有动画在播 → 开始交叉淡化。
     * </p>
     */
    public boolean play(
            String name
    ) {

        return playAt(
                name,
                nowSeconds()
        );
    }

    /**
     * 请求播放动画（注入时钟；测试 seam）。
     */
    public boolean playAt(
            String name,
            double nowSeconds
    ) {

        if (name == null ||
                instance.getDefinition()
                        .getAnimation(name) == null) {

            return false;
        }

        if (name.equals(currentName) &&
                !blending) {

            ModelAnimation current =
                    instance.getDefinition()
                            .getAnimation(
                                    currentName
                            );

            /*
             * 非循环动画播完后允许重播（重置到开头）。
             * 同时重置时间基准——否则重播首 tick 的 dt
             * 会从上次 tick 起算（动画再次瞬跳）。
             */
            if (current != null &&
                    !current.isLooping() &&
                    currentTimeSeconds >=
                            current.getDurationSeconds()) {

                currentTimeSeconds = 0.0;
                settled = false;
                firstTick = true;
            }

            return true;
        }

        if (name.equals(targetName) &&
                blending) {

            return true;
        }

        /*
         * 0.9.0更新：A→B 淡化途中切回 A——
         * 绝不能产生 A→A self-blend（同一动画叠加两次
         * 导致幅度加倍）。取消淡化、保持当前 A 继续。
         */
        if (name.equals(currentName) &&
                blending) {

            blending = false;
            targetName = null;
            frozenPose = null;
            settled = false;

            return true;
        }

        if (currentName == null) {

            currentName = name;
            currentTimeSeconds = 0.0;
            blending = false;
            settled = false;

            return true;
        }

        if (blending) {

            /*
             * P1-22：A→B 途中切 C——from 不再推进 A，
             * 而是冻结此刻屏幕上的 A×B 混合结果。
             */
            frozenPose =
                    snapshotCurrentPose();
        }

        targetName = name;
        targetTimeSeconds = 0.0;
        blending = true;
        blendStartSeconds = nowSeconds;
        settled = false;

        return true;
    }

    /**
     * 冻结当前全部骨骼的动画增量（屏幕混合结果快照）。
     */
    private Map<String, ModelTransform> snapshotCurrentPose() {

        Map<String, ModelTransform> snapshot =
                new java.util.LinkedHashMap<>();

        for (ModelBonePose pose :
                instance.poses()) {

            snapshot.put(
                    pose.getBoneName(),
                    pose.getAnimationTransform()
            );
        }

        return snapshot;
    }

    /**
     * 把冻结快照直接写入姿态（不采样）。
     */
    private void applyFrozenPose(
            Map<String, ModelTransform> pose
    ) {

        for (Map.Entry<String, ModelTransform> entry :
                pose.entrySet()) {

            ModelBonePose bonePose =
                    instance.getBone(
                            entry.getKey()
                    );

            if (bonePose != null) {

                bonePose.setAnimationTransform(
                        entry.getValue()
                );
            }
        }
    }

    /**
     * 停止全部动画：骨骼回到 base 姿态。
     */
    public void stop() {

        currentName = null;
        targetName = null;
        blending = false;
        currentTimeSeconds = 0.0;
        targetTimeSeconds = 0.0;
        frozenPose = null;

        /*
         * 重置时间基准：下次 tick 重建 lastTickSeconds，
         * 否则新动画首 tick 的 dt 会从上次 tick 起算
         * （可能推进一大步，首帧跳变）。
         */
        firstTick = true;
        settled = false;

        for (ModelBonePose pose :
                instance.poses()) {

            pose.setAnimationTransform(
                    ModelTransform.IDENTITY
            );
        }
    }

    /**
     * 推进、采样并写入骨骼姿态（真实时钟入口）。
     *
     * @return 是否有动画在推进（供渲染层决定刷新）
     */
    public boolean tick() {

        return tickAt(
                nowSeconds()
        );
    }

    /**
     * 推进、采样并写入骨骼姿态（注入时钟；测试 seam）。
     */
    public boolean tickAt(
            double nowSeconds
    ) {

        if (firstTick) {

            firstTick = false;
            lastTickSeconds = nowSeconds;
            return hasActiveAnimation();
        }

        if (settled) {

            return false;
        }

        double dt =
                nowSeconds - lastTickSeconds;

        lastTickSeconds = nowSeconds;

        if (dt < 0.0) {

            dt = 0.0;
        }

        if (dt > 1.0) {

            dt = 1.0;
        }

        if (blending) {

            currentTimeSeconds += dt;
            targetTimeSeconds += dt;

            double progress =
                    (nowSeconds - blendStartSeconds) /
                            CROSSFADE_SECONDS;

            resetAnimationLayer();

            if (progress >= 1.0) {

                blending = false;
                currentName = targetName;
                currentTimeSeconds = targetTimeSeconds;
                targetName = null;
                frozenPose = null;

                applyAnimation(
                        currentName,
                        currentTimeSeconds,
                        1.0
                );
            } else {

                if (frozenPose != null) {

                    applyFrozenPose(
                            frozenPose
                    );

                } else {

                    applyAnimation(
                            currentName,
                            currentTimeSeconds,
                            1.0
                    );
                }

                applyAnimation(
                        targetName,
                        targetTimeSeconds,
                        progress
                );
            }

            return true;
        }

        if (currentName == null) {

            return false;
        }

        currentTimeSeconds += dt;

        ModelAnimation current =
                instance.getDefinition()
                        .getAnimation(
                                currentName
                        );

        /*
         * 非循环动画播完（0.9.0更新）：
         * - HOLD：钳制到末尾、写入最后一帧后静止
         *   （settled），后续 tick 零操作；
         * - ONCE：回到基础姿态（动画层清零）后静止。
         * 回退到 idle 由适配器重新下发信号驱动（含重播，见 playAt）。
         */
        if (current != null &&
                !current.isLooping() &&
                currentTimeSeconds >=
                        current.getDurationSeconds()) {

            if (current.getLoopMode()
                    == mizukichou.nekonyume.model.LoopMode.ONCE) {

                resetAnimationLayer();

            } else {

                currentTimeSeconds =
                        current.getDurationSeconds();

                resetAnimationLayer();

                applyAnimation(
                        currentName,
                        currentTimeSeconds,
                        1.0
                );
            }

            settled = true;

            return true;
        }

        resetAnimationLayer();

        applyAnimation(
                currentName,
                currentTimeSeconds,
                1.0
        );

        return true;
    }

    public boolean hasActiveAnimation() {

        return currentName != null ||
                blending;
    }

    /**
     * 是否处于交叉淡化中（测试观察口，0.9.0更新）。
     */
    public boolean isBlending() {

        return blending;
    }

    /**
     * 清空动画层：全部骨骼增量归零。
     * 每次采样前调用——切到不含某骨骼的动画时，
     * 旧动画在该骨骼上的姿态绝不残留。
     */
    private void resetAnimationLayer() {

        for (ModelBonePose pose :
                instance.poses()) {

            pose.setAnimationTransform(
                    ModelTransform.IDENTITY
            );
        }
    }

    private void applyAnimation(
            String name,
            double timeSeconds,
            double weight
    ) {

        ModelAnimation animation =
                instance.getDefinition()
                        .getAnimation(name);

        if (animation == null) {

            return;
        }

        Map<String, ModelTransform> sampled =
                animation.sample(timeSeconds);

        for (Map.Entry<String, ModelTransform> entry :
                sampled.entrySet()) {

            ModelBonePose pose =
                    instance.getBone(
                            entry.getKey()
                    );

            if (pose == null) {

                continue;
            }

            ModelTransform delta =
                    entry.getValue();

            if (weight >= 1.0) {

                pose.setAnimationTransform(delta);
                continue;
            }

            pose.setAnimationTransform(
                    ModelTransform.interpolate(
                            pose.getAnimationTransform(),
                            delta,
                            weight
                    )
            );
        }
    }

    private static double nowSeconds() {

        return System.nanoTime() / 1_000_000_000.0;
    }
}
