package mizukichou.nekonyume.model;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 模型实例渲染器（静态渲染阶段，Phase 3）。
 *
 * <p>
 * 连接 {@link ModelInstance} 与渲染后端：
 * </p>
 * <ul>
 *   <li>创建时为每个骨骼生成一个渲染对象</li>
 *   <li>骨骼世界变换沿树累积（worldT = 父 worldT × 局部）</li>
 *   <li>{@link #tick(Location, double)} 做变更检测
 *       （架构 §35：位置/yaw 未变则不写）</li>
 *   <li>漂移重锚定：渲染对象实体偏离目标超过
 *       32 格才传送（避免每 tick 无条件传送）</li>
 * </ul>
 *
 * <p>
 * 主线程专用。动画阶段（Phase 5）：
 * {@link #refreshPoses()} 从实例姿态重算世界变换，
 * {@link #tick(Location, double)} 在姿态脏或位置变化时写入。
 * </p>
 */
public final class ModelRenderer {

    /**
     * 漂移重锚定阈值（格）。
     */
    private static final double REANCHOR_DISTANCE = 32.0;

    private static final double REANCHOR_DISTANCE_SQ =
            REANCHOR_DISTANCE * REANCHOR_DISTANCE;

    /**
     * 0.9.0更新：浮点微小抖动不应触发
     * 整棵模型重算。
     */
    private static final double POSITION_EPSILON_SQUARED =
            1e-6;

    private static final double YAW_EPSILON_DEGREES =
            0.5;

    private static final double ONE_OVER_16 =
            1.0 / 16.0;

    /**
     * 客户端渲染补偿（ItemDisplay 语义，1.21.4+ 源码验证）：
     *
     * <p>
     * 1. {@code ItemRenderer.renderItem} 在应用显示变换后固定
     *    {@code translate(-0.5, -0.5, -0.5)}——显示变换的 pivot
     *    在模型中心 (8,8,8)，模型空间原点相对实体位置偏移
     *    -0.5 格；补偿 = 骨骼空间 +0.5 格（经骨骼旋转）。
     * </p>
     *
     * <p>
     * 2. {@code ItemDisplayEntityRenderer} 渲染前固定乘
     *    {@code RotY(π)}——左旋转必须再乘 180° Y 旋转抵消，
     *    否则模型相对 Blockbench 编辑器视图前后颠倒、
     *    左右镜像。
     * </p>
     */
    private static final Vec3 HALF_BLOCK_LOCAL =
            new Vec3(0.5, 0.5, 0.5);

    private static final Quaternion YAW_180 =
            Quaternion.fromYAxisDeg(
                    180.0
            );

    private final ModelInstance instance;

    private final Map<String, ModelRenderObject> objects;

    private final Map<String, ModelTransform> boneWorldTransforms;

    private Vec3 anchorPosition;

    private Vec3 lastEntityPosition;

    private double lastYawDeg = Double.NaN;

    private boolean visible = true;

    private boolean posesDirty = true;

    private boolean destroyed;

    private ModelRenderer(
            ModelInstance instance,
            Map<String, ModelRenderObject> objects,
            Map<String, ModelTransform> boneWorldTransforms,
            Vec3 anchorPosition
    ) {

        this.instance = instance;
        this.objects = objects;
        this.boneWorldTransforms = boneWorldTransforms;
        this.anchorPosition = anchorPosition;
    }

    /**
     * 创建渲染器：为每个骨骼生成渲染对象并应用初始变换。
     *
     * @param spawnLocation 初始位置（实体位置）
     */
    public static ModelRenderer create(
            ModelInstance instance,
            ModelRenderBackend backend,
            Location spawnLocation,
            UUID catUuid
    ) {

        if (instance == null || backend == null ||
                spawnLocation == null ||
                !Double.isFinite(spawnLocation.getX()) ||
                !Double.isFinite(spawnLocation.getY()) ||
                !Double.isFinite(spawnLocation.getZ())) {

            throw new IllegalArgumentException(
                    "Arguments must be non-null and finite."
            );
        }

        Vec3 anchor =
                new Vec3(
                        spawnLocation.getX(),
                        spawnLocation.getY(),
                        spawnLocation.getZ()
                );

        Map<String, ModelRenderObject> objects =
                new LinkedHashMap<>();

        java.util.List<String> boneNames =
                new java.util.ArrayList<>();

        collectRenderableBoneNames(
                instance.getDefinition()
                        .getPrimaryGeometry()
                        .getRoot(),
                boneNames
        );

        ResourceId definitionId =
                instance.getDefinition()
                        .getId();

        for (String boneName :
                boneNames) {

            ResourceId itemModel =
                    ModelResourceNaming.boneItemModel(
                            definitionId,
                            boneName
                    );

            if (itemModel == null) {

                throw new IllegalArgumentException(
                        "Bone name produces invalid item model " +
                                "resource path: '" + boneName +
                                "' in model " +
                                definitionId + "."
                );
            }

            try {

                ModelRenderObject created =
                        backend.createBoneObject(
                                spawnLocation,
                                itemModel,
                                boneName,
                                catUuid
                        );

                /*
                 * 0.9.0更新：只有 Root 对象
                 * 开启交互盒——右键模型即命中 Root Display，
                 * 由监听器转发为对原版猫的交互。
                 */
                if ("Root".equals(
                        boneName
                )) {

                    created.setInteractive(
                            true
                    );
                }

                objects.put(
                        boneName,
                        created
                );

            } catch (RuntimeException exception) {

                /*
                 * 创建中途失败：清理已生成的渲染对象，
                 * 绝不留孤儿 Display（资源泄漏防御）。
                 */
                for (ModelRenderObject created :
                        objects.values()) {

                    try {

                        created.remove();

                    } catch (RuntimeException ignored) {
                        // 清理尽力而为。
                    }
                }

                objects.clear();

                throw exception;
            }
        }

        ModelRenderer renderer =
                new ModelRenderer(
                        instance,
                        objects,
                        new LinkedHashMap<>(),
                        anchor
                );

        renderer.refreshPoses();

        return renderer;
    }

    /**
     * 每 tick 同步（由 Phase 4 视觉控制器调用）。
     *
     * <p>
     * 变更检测：实体位置与 yaw 均未变时零操作。
     * </p>
     *
     * @param entityLocation 实体当前位置
     * @param yawDeg         实体水平朝向（度）
     */
    public void tick(
            Location entityLocation,
            double yawDeg
    ) {

        if (destroyed || entityLocation == null ||
                !Double.isFinite(entityLocation.getX()) ||
                !Double.isFinite(entityLocation.getY()) ||
                !Double.isFinite(entityLocation.getZ()) ||
                !Double.isFinite(yawDeg)) {
            return;
        }

        Vec3 position =
                new Vec3(
                        entityLocation.getX(),
                        entityLocation.getY(),
                        entityLocation.getZ()
                );

        boolean moved =
                lastEntityPosition == null ||
                position.subtract(
                        lastEntityPosition
                )
                        .lengthSquared() >
                        POSITION_EPSILON_SQUARED ||
                Math.abs(
                        yawDeg - lastYawDeg
                ) > YAW_EPSILON_DEGREES;

        if (!moved &&
                !posesDirty) {

            return;
        }

        lastEntityPosition = position;
        lastYawDeg = yawDeg;

        Quaternion yaw =
                Quaternion.fromYAxisDeg(
                        yawDeg
                );

        Vec3 drift =
                new Vec3(
                        position.getX() - anchorPosition.getX(),
                        position.getY() - anchorPosition.getY(),
                        position.getZ() - anchorPosition.getZ()
                );

        /*
         * 漂移重锚必须在写入变换之前：Display 实体 teleport
         * 到新位置后，本 tick 用零漂移重算全部变换。
         * 若写在 teleport 之后才重锚，下一次位置未变时
         * 变更检测会跳过写入，残留的旧漂移补偿会造成
         * 一次性的大幅视觉偏移。
         */
        if (drift.lengthSquared() >
                REANCHOR_DISTANCE_SQ) {

            reanchor(
                    entityLocation
            );

            drift =
                    new Vec3(
                            0.0,
                            0.0,
                            0.0
                    );
        }

        for (Map.Entry<String, ModelRenderObject> entry :
                objects.entrySet()) {

            ModelTransform world =
                    boneWorldTransforms.get(
                            entry.getKey()
                    );

            Vec3 localBlocks =
                    new Vec3(
                            world.getTranslation().getX() *
                                    ONE_OVER_16,
                            world.getTranslation().getY() *
                                    ONE_OVER_16,
                            world.getTranslation().getZ() *
                                    ONE_OVER_16
                    );

            /*
             * 0.5 格补偿在骨骼空间内先经骨骼旋转，
             * 再随整体朝向转 yaw；π 抵消并入左旋转。
             */
            Vec3 pivotCompensation =
                    world.getRotation()
                            .rotate(
                                    HALF_BLOCK_LOCAL
                            );

            Vec3 rotated =
                    yaw.rotate(
                            localBlocks.add(
                                    pivotCompensation
                            )
                    );

            Vec3 translation =
                    new Vec3(
                            rotated.getX() + drift.getX(),
                            rotated.getY() + drift.getY(),
                            rotated.getZ() + drift.getZ()
                    );

            entry.getValue()
                    .setWorldTransform(
                            translation,
                            yaw.multiply(
                                            world.getRotation()
                                    )
                                    .multiply(
                                            YAW_180
                                    ),
                            world.getScale()
                    );
        }

        posesDirty = false;
    }

    /**
     * 从实例姿态（含动画增量）重算全部骨骼世界变换。
     *
     * <p>
     * 动画推进时调用（Phase 5）；静态模型无需调用。
     * 重算后下一个 {@link #tick(Location, double)} 会写入。
     * </p>
     */
    public void refreshPoses() {

        if (destroyed) {
            return;
        }

        boneWorldTransforms.clear();

        ModelGeometry geometry =
                instance.getDefinition()
                        .getPrimaryGeometry();

        computeWorldTransforms(
                geometry.getRoot(),
                ModelTransform.IDENTITY,
                boneWorldTransforms
        );

        posesDirty = true;
    }

    /**
     * 销毁全部渲染对象（幂等）。
     */
    public void destroy() {

        if (destroyed) {
            return;
        }

        destroyed = true;

        /*
         * 0.9.0更新：逐对象异常隔离——
         * 单个 remove 失败绝不阻断其余 Display 清理。
         */
        for (ModelRenderObject object :
                objects.values()) {

            try {

                object.remove();

            } catch (RuntimeException exception) {

                // 清理尽力而为：单个坏 Display 不阻断其余。
            }
        }

        objects.clear();
    }

    public boolean isDestroyed() {

        return destroyed;
    }

    public ModelInstance getInstance() {

        return instance;
    }

    public int objectCount() {

        return objects.size();
    }

    /**
     * Root 渲染对象的实体 UUID（交互转发反向映射用）。
     * 空骨骼跳过的模型 Root 恒存在（collectRenderableBoneNames
     * 保证），正常返回非 null。
     */
    public UUID rootObjectId() {

        ModelRenderObject root =
                objects.get(
                        "Root"
                );

        return root == null
                ? null
                : root.getId();
    }

    /**
     * 全部渲染对象实体 UUID（0.9.0更新：
     * 自修复反向映射必须覆盖所有骨骼 Display，
     * 只登记 Root 则 Head/Ear 被 /kill 无法发现）。
     */
    public java.util.List<UUID> allObjectIds() {

        java.util.List<UUID> ids =
                new java.util.ArrayList<>(
                        objects.size()
                );

        for (ModelRenderObject object :
                objects.values()) {

            ids.add(
                    object.getId()
            );
        }

        return ids;
    }

    /**
     * 整体可见性（隐藏时不渲染任何骨骼，用于恢复期
     * 半透明影子等视觉接管场景）。
     */
    public void setVisible(
            boolean visible
    ) {

        if (destroyed) {
            return;
        }

        if (this.visible == visible) {
            return;
        }

        this.visible = visible;

        for (ModelRenderObject object :
                objects.values()) {

            object.setVisible(
                    visible
            );
        }
    }

    public boolean isVisible() {

        return visible;
    }

    private void reanchor(
            Location entityLocation
    ) {

        for (ModelRenderObject object :
                objects.values()) {

            object.teleportTo(
                    entityLocation
            );
        }

        anchorPosition =
                new Vec3(
                        entityLocation.getX(),
                        entityLocation.getY(),
                        entityLocation.getZ()
                );
    }

    /**
     * 沿树累积骨骼世界变换（模型空间，不含实体朝向）；
     * 局部变换取实例姿态的最终变换（base × 动画增量）。
     */
    private void computeWorldTransforms(
            ModelBone bone,
            ModelTransform parentWorld,
            Map<String, ModelTransform> out
    ) {

        ModelBonePose pose =
                instance.getBone(
                        bone.getName()
                );

        ModelTransform local =
                pose == null
                        ? bone.getLocalTransform()
                        : pose.computeFinalTransform();

        ModelTransform world =
                parentWorld.compose(
                        local
                );

        out.put(
                bone.getName(),
                world
        );

        for (ModelBone child :
                bone.getChildren()) {

            computeWorldTransforms(
                    child,
                    world,
                    out
            );
        }
    }

    /**
     * 收集骨骼名（先序遍历）。
     */
    private static void collectRenderableBoneNames(
            ModelBone bone,
            java.util.List<String> out
    ) {

        /*
         * 空骨骼（无几何）不创建 Display——它只是层级
         * transform 节点；但 Root 恒保留（承载实体锚点
         * 与整体朝向）。
         */
        boolean root =
                "Root".equals(
                        bone.getName()
                );

        boolean hasGeometry =
                !bone.getCuboids()
                        .isEmpty();

        if (root || hasGeometry) {

            out.add(
                    bone.getName()
            );
        }

        for (ModelBone child :
                bone.getChildren()) {

            collectRenderableBoneNames(
                    child,
                    out
            );
        }
    }
}
