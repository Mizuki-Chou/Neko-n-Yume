package mizukichou.nekonyume.model;

/**
 * 运行时骨骼姿态（每实例每骨骼一份，可变）。
 *
 * <p>
 * 主线程专用（§42）。变换组合（§17）：
 * {@code final = baseTransform × animationTransform}。
 * </p>
 *
 * <p>
 * baseTransform 默认取骨骼定义的局部变换，
 * 可被姿态层覆盖（坐下收尾等程序化姿态，Phase 5）；
 * animationTransform 由动画控制器写入（默认单位变换）。
 * </p>
 */
public final class ModelBonePose {

    private final ModelBone bone;

    private ModelTransform baseTransform;

    private ModelTransform animationTransform;

    ModelBonePose(
            ModelBone bone
    ) {

        this.bone = bone;
        this.baseTransform =
                bone.getLocalTransform();
        this.animationTransform =
                ModelTransform.IDENTITY;
    }

    public ModelBone getBone() {

        return bone;
    }

    public String getBoneName() {

        return bone.getName();
    }

    /**
     * 覆盖基础姿态（null 拒绝；重置请传定义变换）。
     */
    public void setBaseTransform(
            ModelTransform transform
    ) {

        if (transform == null) {
            throw new IllegalArgumentException(
                    "baseTransform must not be null."
            );
        }

        this.baseTransform = transform;
    }

    public ModelTransform getBaseTransform() {

        return baseTransform;
    }

    /**
     * 写入动画增量变换（null 拒绝）。
     */
    public void setAnimationTransform(
            ModelTransform transform
    ) {

        if (transform == null) {
            throw new IllegalArgumentException(
                    "animationTransform must not be null."
            );
        }

        this.animationTransform = transform;
    }

    public ModelTransform getAnimationTransform() {

        return animationTransform;
    }

    /**
     * 最终变换 = base × animation（每次现算，无缓存；
     * 渲染层按需做变更检测，见架构 §35）。
     */
    public ModelTransform computeFinalTransform() {

        return baseTransform.compose(
                animationTransform
        );
    }

    @Override
    public String toString() {

        return "ModelBonePose{" +
                bone.getName() + "}";
    }
}
