package mizukichou.nekonyume.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import mizukichou.nekonyume.NekoNYume;
import org.bukkit.Bukkit;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class CatEntityListener implements Listener {

    private final NekoNYume plugin;

    public CatEntityListener(NekoNYume plugin) {
        this.plugin = plugin;
    }

    /*
     * ============================================================
     * 实体加入世界
     * ============================================================
     */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAddToWorld(
            EntityAddToWorldEvent event
    ) {

        Entity entity =
                event.getEntity();

        if (!(entity instanceof Cat cat)) {
            return;
        }

        /*
         * 注意：
         * 新生成的猫在 spawnEntity 时触发本事件，
         * 此时 updateCat() 还没有写入 PDC。
         *
         * 所以 PDC 检查必须放在延迟任务里，
         * 而不是事件触发的那一刻。
         * 否则新生成的正式实体永远无法通过检查。
         */
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {

                            if (cat.isDead() ||
                                    !cat.isValid()) {
                                return;
                            }

                            /*
                             * 这里只处理我们的猫
                             */
                            if (!cat.getPersistentDataContainer()
                                    .has(
                                            plugin.getCatManager()
                                                    .getCatKey(),
                                            PersistentDataType.BYTE
                                    )) {

                                return;
                            }

                            String ownerUUID =
                                    cat.getPersistentDataContainer()
                                            .get(
                                                    plugin.getCatManager()
                                                            .getOwnerKey(),
                                                    PersistentDataType.STRING
                                            );

                            if (ownerUUID == null) {
                                return;
                            }

                            UUID playerUUID;

                            try {

                                playerUUID =
                                        UUID.fromString(
                                                ownerUUID
                                        );

                            } catch (IllegalArgumentException e) {

                                return;
                            }

                            /*
                             * 玩家已经没有宠物数据
                             */
                            if (!plugin.getDataManager()
                                    .hasCat(playerUUID)) {

                                return;
                            }

                            UUID currentUUID =
                                    plugin.getDataManager()
                                            .getCatEntityUUID(
                                                    playerUUID
                                            );

                            /*
                             * 当前没有正式实体。
                             * 让这个实体成为正式实体。
                             */
                            if (currentUUID == null) {

                                plugin.getDataManager()
                                        .setCatEntityUUID(
                                                playerUUID,
                                                cat.getUniqueId()
                                        );

                                /*
                                 * 同步运行时缓存。
                                 */
                                mizukichou.nekonyume.cat.Cat logicalCat =
                                        plugin.getCatManager()
                                                .getCat(
                                                        playerUUID
                                                );

                                if (logicalCat != null) {

                                    logicalCat.setEntityUuid(
                                            cat.getUniqueId()
                                    );
                                }

                                return;
                            }

                            /*
                             * UUID 相同：
                             * 正常实体。
                             */
                            if (currentUUID.equals(
                                    cat.getUniqueId()
                            )) {

                                return;
                            }

                            /*
                             * UUID 不同：
                             * 这是旧实体 / 重复实体。
                             */
                            cat.remove();
                        }
                );
    }

    /*
     * ============================================================
     * 猫死亡
     * ============================================================
     *
     * 猫当前是暂时无敌的，
     * 但 /kill、部分插件仍然可能致死。
     *
     * 死亡只清绑定，
     * 逻辑猫与全部存档数据保留。
     * 玩家执行 /nekoyume summon 即可恢复。
     */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(
            EntityDeathEvent event
    ) {

        if (!(event.getEntity() instanceof Cat cat)) {
            return;
        }

        handleEntityLoss(
                cat
        );
    }

    /*
     * ============================================================
     * 世界加载
     * ============================================================
     *
     * 玩家登录时，猫咪所在世界可能尚未加载。
     * 世界加载完成后，
     * 重试等待中的实体恢复。
     */

    @EventHandler
    public void onWorldLoad(
            WorldLoadEvent event
    ) {

        plugin.getCatManager()
                .retryPendingWorldRestores(
                        event.getWorld()
                );
    }

    /*
     * ============================================================
     * 工具
     * ============================================================
     */

    /*
     * 防御性清绑定。
     *
     * 只有被移除 / 死亡的实体
     * 正是当前绑定实体时才清。
     *
     * 这样清理重复猫（cleanupDuplicateCats）
     * 不会影响正式绑定。
     *
     * 同时该方法在死亡 + 移除连续触发时
     * 是幂等的：第一次已清空，
     * 第二次 currentUUID 为 null 直接返回。
     */
    private void handleEntityLoss(
            Cat cat
    ) {

        if (cat == null) {
            return;
        }

        String ownerUUIDString =
                cat.getPersistentDataContainer()
                        .get(
                                plugin.getCatManager()
                                        .getOwnerKey(),
                                PersistentDataType.STRING
                        );

        if (ownerUUIDString == null) {
            return;
        }

        UUID playerUUID;

        try {

            playerUUID =
                    UUID.fromString(
                            ownerUUIDString
                    );

        } catch (IllegalArgumentException ignored) {

            return;
        }

        /*
         * 确认这就是当前绑定的实体。
         */
        UUID currentUUID =
                plugin.getDataManager()
                        .getCatEntityUUID(
                                playerUUID
                        );

        if (currentUUID == null ||
                !currentUUID.equals(
                        cat.getUniqueId()
                )) {

            return;
        }

        /*
         * 只清绑定。
         * 逻辑猫与全部状态保留。
         */
        plugin.getCatManager()
                .clearEntityBinding(
                        playerUUID
                );

        plugin.getLogger().info(
                "Cat entity "
                        + cat.getUniqueId()
                        + " lost for player "
                        + playerUUID
                        + ". Binding cleared, logical cat kept."
        );
    }
}
