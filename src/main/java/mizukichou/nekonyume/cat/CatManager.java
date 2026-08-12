package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.NekoNYume;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class CatManager {

    private final NekoNYume plugin;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    public CatManager(NekoNYume plugin) {
        this.plugin = plugin;

        this.catKey = new NamespacedKey(
                plugin,
                "nekonyume_cat"
        );

        this.ownerKey = new NamespacedKey(
                plugin,
                "owner_uuid"
        );
    }

    /**
     * 召唤玩家自己的猫
     *
     * @return true  = 新生成了一只猫
     *         false = 找到了原来的猫
     */
    public boolean spawnCat(Player player, String name) {

        UUID playerUUID = player.getUniqueId();

        /*
         * ① 先读取 players.yml 中保存的猫咪实体 UUID
         */
        UUID savedEntityUUID =
                plugin.getDataManager()
                        .getCatEntityUUID(playerUUID);

        /*
         * ② 如果有保存的 UUID，直接寻找这只实体
         */
        if (savedEntityUUID != null) {

            Entity entity =
                    Bukkit.getEntity(savedEntityUUID);

            /*
             * 找到了原来的猫
             */
            if (entity instanceof Cat cat && !cat.isDead()) {

                cat.teleport(
                        player.getLocation()
                );

                updateCat(
                        cat,
                        player,
                        name
                );

                saveCatLocation(
                        player,
                        cat
                );

                return false;
            }
        }

        /*
         * ③ 如果 UUID 找不到，
         * 再根据上次保存的世界和坐标寻找
         */
        UUID savedWorldUUID =
                plugin.getDataManager()
                        .getCatWorldUUID(playerUUID);

        if (savedWorldUUID != null) {

            World world =
                    Bukkit.getWorld(savedWorldUUID);

            if (world != null) {

                double x =
                        plugin.getDataManager()
                                .getCatX(playerUUID);

                double y =
                        plugin.getDataManager()
                                .getCatY(playerUUID);

                double z =
                        plugin.getDataManager()
                                .getCatZ(playerUUID);

                /*
                 * 让猫所在的区块加载
                 */
                int chunkX =
                        ((int) Math.floor(x)) >> 4;

                int chunkZ =
                        ((int) Math.floor(z)) >> 4;

                world.getChunkAt(
                        chunkX,
                        chunkZ
                ).load();

                Location savedLocation =
                        new Location(
                                world,
                                x,
                                y,
                                z
                        );

                /*
                 * 在原位置附近寻找 Neko n' Yume 的猫
                 */
                for (Entity entity :
                        savedLocation.getNearbyEntities(
                                4,
                                4,
                                4
                        )) {

                    if (!(entity instanceof Cat cat)) {
                        continue;
                    }

                    /*
                     * 必须是 Neko n' Yume 的猫
                     */
                    if (!cat.getPersistentDataContainer().has(
                            catKey,
                            PersistentDataType.BYTE
                    )) {
                        continue;
                    }

                    /*
                     * 必须属于这个玩家
                     */
                    String ownerUUID =
                            cat.getPersistentDataContainer()
                                    .get(
                                            ownerKey,
                                            PersistentDataType.STRING
                                    );

                    if (!playerUUID.toString()
                            .equals(ownerUUID)) {
                        continue;
                    }

                    /*
                     * 找到了原来的猫
                     */

                    plugin.getDataManager()
                            .setCatEntityUUID(
                                    playerUUID,
                                    cat.getUniqueId()
                            );

                    cat.teleport(
                            player.getLocation()
                    );

                    updateCat(
                            cat,
                            player,
                            name
                    );

                    saveCatLocation(
                            player,
                            cat
                    );

                    return false;
                }
            }
        }

        /*
         * ④ 到这里仍然没找到，
         * 才生成一只全新的猫
         */
        Cat cat =
                (Cat) player.getWorld()
                        .spawnEntity(
                                player.getLocation(),
                                EntityType.CAT
                        );

        updateCat(
                cat,
                player,
                name
        );

        /*
         * 保存猫咪 UUID
         */
        plugin.getDataManager()
                .setCatEntityUUID(
                        playerUUID,
                        cat.getUniqueId()
                );

        /*
         * 保存猫咪位置
         */
        saveCatLocation(
                player,
                cat
        );

        return true;
    }

    /**
     * 设置猫咪属性和 PDC
     */
    private void updateCat(
            Cat cat,
            Player player,
            String name
    ) {

        cat.setCustomName(
                "§d🐱 " + name
        );

        cat.setCustomNameVisible(true);

        cat.setOwner(player);

        cat.setTamed(true);

        /*
         * 标记为 Neko n' Yume 猫
         */
        cat.getPersistentDataContainer().set(
                catKey,
                PersistentDataType.BYTE,
                (byte) 1
        );

        /*
         * 保存主人 UUID
         */
        cat.getPersistentDataContainer().set(
                ownerKey,
                PersistentDataType.STRING,
                player.getUniqueId().toString()
        );
    }

    /**
     * 保存猫咪当前世界和位置
     */
    private void saveCatLocation(
            Player player,
            Cat cat
    ) {

        Location location =
                cat.getLocation();

        plugin.getDataManager()
                .setCatLocation(
                        player.getUniqueId(),
                        location.getWorld().getUID(),
                        location.getX(),
                        location.getY(),
                        location.getZ()
                );
    }
}