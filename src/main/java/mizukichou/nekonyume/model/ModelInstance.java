package mizukichou.nekonyume.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 世界中的模型实例（架构 §6）。
 *
 * <p>
 * 多实例共享同一个 {@link ModelDefinition}。
 * 持有绑定实体 UUID、可见性与每骨骼运行时姿态。
 * </p>
 *
 * <p>
 * 线程契约（§42）：主线程专用，可变状态不做并发防护。
 * 生命周期跟随实体（Phase 4 由绑定层管理）。
 * </p>
 */
public final class ModelInstance {

    private final ModelDefinition definition;

    private final UUID entityUuid;

    private final Map<String, ModelBonePose> poses;

    private boolean visible;

    /**
     * @param definition 共享模型定义（非 null）
     * @param entityUuid 绑定实体 UUID（非 null；实例生命周期跟随它）
     */
    public ModelInstance(
            ModelDefinition definition,
            UUID entityUuid
    ) {

        if (definition == null) {
            throw new IllegalArgumentException(
                    "definition must not be null."
            );
        }

        if (entityUuid == null) {
            throw new IllegalArgumentException(
                    "entityUuid must not be null."
            );
        }

        this.definition = definition;
        this.entityUuid = entityUuid;
        this.visible = true;
        this.poses =
                buildPoses(definition);
    }

    private static Map<String, ModelBonePose> buildPoses(
            ModelDefinition definition
    ) {

        Map<String, ModelBonePose> map =
                new LinkedHashMap<>();

        for (ModelGeometry geometry :
                definition.getGeometries()) {

            collectPoses(
                    geometry.getRoot(),
                    map
            );
        }

        return Collections.unmodifiableMap(
                map
        );
    }

    private static void collectPoses(
            ModelBone bone,
            Map<String, ModelBonePose> target
    ) {

        java.util.ArrayDeque<ModelBone> stack =
                new java.util.ArrayDeque<>();

        stack.push(bone);

        while (!stack.isEmpty()) {

            ModelBone current =
                    stack.pop();

            /*
             * 0.9.0更新：重名骨骼静默覆盖 →
             * 拒绝（ModelGeometry.Builder 已校验，这里是
             * 公共 API 不变量兜底）。
             */
            if (target.put(
                    current.getName(),
                    new ModelBonePose(current)
            ) != null) {

                throw new IllegalArgumentException(
                        "Duplicate bone name in poses: "
                                + current.getName()
                );
            }

            for (ModelBone child :
                    current.getChildren()) {

                stack.push(child);
            }
        }
    }

    public ModelDefinition getDefinition() {

        return definition;
    }

    public UUID getEntityUuid() {

        return entityUuid;
    }

    /**
     * 按名取骨骼姿态；不存在返回 null（API §44）。
     */
    public ModelBonePose getBone(
            String boneName
    ) {

        return poses.get(
                boneName
        );
    }

    /**
     * 全部骨骼姿态（不可修改视图，树遍历序）。
     */
    public Collection<ModelBonePose> poses() {

        return poses.values();
    }

    /**
     * 骨骼总数。
     */
    public int boneCount() {

        return poses.size();
    }

    public boolean isVisible() {

        return visible;
    }

    public void setVisible(
            boolean visible
    ) {

        this.visible = visible;
    }

    @Override
    public String toString() {

        return "ModelInstance{" +
                definition.getId() +
                ", entity=" + entityUuid +
                ", visible=" + visible + "}";
    }
}
