package mizukichou.nekonyume.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import mizukichou.nekonyume.NekoNYume;
import org.bukkit.Bukkit;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class CatEntityListener implements Listener {

    private final NekoNYume plugin;

    public CatEntityListener(NekoNYume plugin) {
        this.plugin = plugin;
    }

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
         * 这里只处理我们的猫
         */
        if (!cat.getPersistentDataContainer()
                .has(
                        plugin.getCatManager().getCatKey(),
                        PersistentDataType.BYTE
                )) {

            return;
        }

        /*
         * 延迟到下一 tick。
         *
         * 这样新生成的猫已经完成：
         *
         * spawnEntity()
         * ↓
         * setCatEntityUUID()
         *
         * 再进行重复检查。
         */
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {

                            if (cat.isDead() ||
                                    !cat.isValid()) {
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
                             * 当前没有逻辑实体 UUID。
                             * 让这个实体成为正式实体。
                             */
                            if (currentUUID == null) {

                                plugin.getDataManager()
                                        .setCatEntityUUID(
                                                playerUUID,
                                                cat.getUniqueId()
                                        );

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
                             * 这是旧实体/重复实体。
                             */
                            cat.remove();
                        }
                );
    }
}