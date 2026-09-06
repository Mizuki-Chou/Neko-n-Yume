package mizukichou.nekonyume.model;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * ItemDisplay 渲染后端（架构 §29：ItemDisplay 是渲染后端载体）。
 *
 * <p>
 * 项目内唯一触碰 ItemDisplay / item_model 组件的位置：
 * </p>
 * <ul>
 *   <li>基础物品 PAPER + {@code minecraft:item_model} 组件
 *       （Paper 26.2 API：DataComponentType.Valued&lt;Key&gt;）</li>
 *   <li>显示变换 FIXED（模型原点 = 渲染对象实体位置）</li>
 *   <li>插值：transformation 0.15s（3 tick），传送 2 tick
 *       （架构 §36）</li>
 *   <li>常亮（Brightness 15/15），不受环境光照影响</li>
 *   <li>非持久实体：不写入世界存档，防孤儿 Display</li>
 * </ul>
 */
public final class ItemDisplayRenderBackend
        implements ModelRenderBackend {

    /**
     * 插值时长（tick），§36 要求 0.1~0.2s。
     */
    private static final int INTERPOLATION_DURATION_TICKS = 3;

    private static final int TELEPORT_DURATION_TICKS = 2;

    /**
     * 基础物品：PAPER（无任何原版渲染副作用）。
     */
    private static final Material CARRIER_ITEM =
            Material.PAPER;

    /**
     * 交互盒尺寸（格，0.9.0更新）：只有 Root
     * 对象开启交互——玩家右键模型时按此盒命中。
     */
    private static final float INTERACTION_BOX =
            0.8f;

    /**
     * Display 身份标记（PDC）：所属猫实体 UUID。
     * 用于启动清理孤儿 Display 与交互转发反向映射。
     */
    public static final String PDC_KEY_DISPLAY_OWNER =
            "nny-model-display";

    private final Plugin plugin;

    private final NamespacedKey ownerKey;

    public ItemDisplayRenderBackend(
            Plugin plugin
    ) {

        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin must not be null."
            );
        }

        this.plugin = plugin;
        this.ownerKey =
                new NamespacedKey(
                        plugin,
                        PDC_KEY_DISPLAY_OWNER
                );
    }

    public NamespacedKey ownerKey() {

        return ownerKey;
    }

    @Override
    public ModelRenderObject createBoneObject(
            Location location,
            ResourceId itemModel,
            String boneName,
            UUID catUuid
    ) {

        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException(
                    "location must have a world."
            );
        }

        /*
         * Display 实体自身的 yaw/pitch 必须保持 0：客户端
         * Billboard.FIXED 会按<b>实体自身</b>的 yaw/pitch 旋转
         * 整个渲染模型（DisplayEntityRenderer 反编译验证），
         * 而服务器侧的所有朝向变换已写入 transformation。
         * 若继承猫的 yaw/pitch，模型会被叠加一个不随 tick
         * 更新的固定旋转：猫走动时模型偏移、方向错乱
         * （实测 90° 分离）。
         */
        Location spawnPosition =
                location.clone();

        spawnPosition.setYaw(
                0.0f
        );

        spawnPosition.setPitch(
                0.0f
        );

        /*
         * Paper 26.2 公共 API：ItemMeta#setItemModel(NamespacedKey)
         * （item_model 组件），比手工 setData 组件更稳定。
         */
        ItemStack item =
                ItemStack.of(
                        CARRIER_ITEM
                );

        ItemMeta meta =
                item.getItemMeta();

        meta.setItemModel(
                new NamespacedKey(
                        itemModel.getNamespace(),
                        itemModel.getPath()
                )
        );

        item.setItemMeta(
                meta
        );

        ItemDisplay display =
                spawnPosition.getWorld()
                        .spawn(
                                spawnPosition,
                                ItemDisplay.class
                        );

        /*
         * 0.9.0更新：setter 序列中途异常会泄漏
         * 已 spawn 的实体（RenderObject 未返回、无人清理）。
         * 整个初始化包 try-catch，失败即移除并重抛。
         */
        try {

            /*
             * 0.9.0更新：spawn 后立即打标——
             * 启动清理 / 交互转发反向映射 / 移除监听
             * 都依赖此身份标记（纸片 Display 必须有标）。
             */
            display.getPersistentDataContainer()
                    .set(
                            ownerKey,
                            PersistentDataType.STRING,
                            catUuid.toString()
                    );

            display.setItemStack(
                    item
            );

            display.setItemDisplayTransform(
                    ItemDisplay.ItemDisplayTransform.FIXED
            );

            display.setBillboard(
                    Display.Billboard.FIXED
            );

            display.setInterpolationDelay(
                    -1
            );

            display.setInterpolationDuration(
                    INTERPOLATION_DURATION_TICKS
            );

            display.setTeleportDuration(
                    TELEPORT_DURATION_TICKS
            );

            display.setBrightness(
                    new Display.Brightness(
                            15,
                            15
                    )
            );

            display.setPersistent(
                    false
            );

        } catch (RuntimeException exception) {

            try {

                display.remove();

            } catch (RuntimeException removeFailure) {

                // 尽力清理：remove 失败不掩盖原异常。
            }

            throw exception;
        }

        return new ItemDisplayRenderObject(
                display
        );
    }

    private static final class ItemDisplayRenderObject
            implements ModelRenderObject {

        private final ItemDisplay display;

        private boolean removed;

        ItemDisplayRenderObject(
                ItemDisplay display
        ) {

            this.display = display;
        }

        @Override
        public UUID getId() {

            return display.getUniqueId();
        }

        @Override
        public void setWorldTransform(
                Vec3 translationBlocks,
                Quaternion rotation,
                Vec3 scale
        ) {

            if (removed) {
                return;
            }

            display.setTransformation(
                    new Transformation(
                            new Vector3f(
                                    (float) translationBlocks.getX(),
                                    (float) translationBlocks.getY(),
                                    (float) translationBlocks.getZ()
                            ),
                            new Quaternionf(
                                    (float) rotation.getX(),
                                    (float) rotation.getY(),
                                    (float) rotation.getZ(),
                                    (float) rotation.getW()
                            ),
                            new Vector3f(
                                    (float) scale.getX(),
                                    (float) scale.getY(),
                                    (float) scale.getZ()
                            ),
                            new Quaternionf(
                                    0.0f,
                                    0.0f,
                                    0.0f,
                                    1.0f
                            )
                    )
            );
        }

        @Override
        public void teleportTo(
                Location location
        ) {

            if (removed) {
                return;
            }

            /*
             * teleport 会把目标的 yaw/pitch 写入实体——必须
             * 保持 0（见 createBoneObject 注释），否则重锚后
             * 模型被 Billboard.FIXED 叠加一个固定旋转。
             */
            Location target =
                    location.clone();

            target.setYaw(
                    0.0f
            );

            target.setPitch(
                    0.0f
            );

            display.teleport(
                    target
            );
        }

        @Override
        public void remove() {

            if (removed) {
                return;
            }

            removed = true;
            display.remove();
        }

        @Override
        public void setVisible(
                boolean visible
        ) {

            if (removed) {
                return;
            }

            display.setViewRange(
                    visible
                            ? 1.0f
                            : 0.0f
            );
        }

        @Override
        public void setInteractive(
                boolean interactive
        ) {

            /*
             * 0.9.0更新：Display 没有交互盒
             * （interactionWidth/Height 只在 Interaction 实体上），
             * 交互转发由后端单独生成的 Interaction 实体承担，
             * 本方法仅记录意图（Root 对象调用方知道要配交互）。
             */
            this.interactive =
                    interactive;
        }

        private boolean interactive;

        public boolean isInteractive() {

            return interactive;
        }
    }

    /**
     * 生成猫的交互代理实体（0.9.0更新）：
     * hideEntity 后客户端不再对原版猫发送交互包，
     * 玩家右键实际上命中这个 Interaction——由监听器
     * 转发为对猫的喂食 / 装备 / 抚摸。
     *
     * <p>
     * Paper 的 spawn(Location, Class, Consumer) 在实体
     * 进入世界之前应用 setter——setPersistent(false) 与
     * PDC 打标无崩溃窗口（反馈 #6 一并解决）。
     * </p>
     */
    @Override
    public org.bukkit.entity.Interaction spawnInteraction(
            Location location,
            UUID catUuid
    ) {

        if (location == null ||
                location.getWorld() == null) {

            throw new IllegalArgumentException(
                    "location must have a world."
            );
        }

        return location.getWorld()
                .spawn(
                        location,
                        org.bukkit.entity.Interaction.class,
                        interaction -> {

                            interaction.setInteractionWidth(
                                    1.2f
                            );

                            interaction.setInteractionHeight(
                                    1.2f
                            );

                            interaction.setResponsive(
                                    true
                            );

                            interaction.setPersistent(
                                    false
                            );

                            interaction.getPersistentDataContainer()
                                    .set(
                                            ownerKey,
                                            PersistentDataType.STRING,
                                            catUuid.toString()
                                    );
                        }
                );
    }
}
