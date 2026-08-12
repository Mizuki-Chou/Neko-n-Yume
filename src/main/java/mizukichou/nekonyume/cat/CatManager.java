package mizukichou.nekonyume.cat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class CatManager {

    private final JavaPlugin plugin;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    public CatManager(JavaPlugin plugin) {
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
     * 召唤玩家自己的猫。
     *
     * @return true  = 新生成了一只猫
     *         false = 找到了已有的猫
     */
    public boolean spawnCat(Player player, String name) {

        UUID playerUUID = player.getUniqueId();

        Cat existingCat = null;

        /*
         * 搜索服务器所有世界
         */
        for (World world : Bukkit.getWorlds()) {

            for (Entity entity : world.getEntities()) {

                if (!(entity instanceof Cat cat)) {
                    continue;
                }

                /*
                 * 判断是不是 Neko n' Yume 的猫
                 */
                if (!cat.getPersistentDataContainer().has(
                        catKey,
                        PersistentDataType.BYTE
                )) {
                    continue;
                }

                /*
                 * 获取主人 UUID
                 */
                String ownerUUID =
                        cat.getPersistentDataContainer().get(
                                ownerKey,
                                PersistentDataType.STRING
                        );

                if (ownerUUID == null) {
                    continue;
                }

                if (!ownerUUID.equals(
                        playerUUID.toString()
                )) {
                    continue;
                }

                /*
                 * 找到了这名玩家的猫
                 */
                if (existingCat == null) {

                    existingCat = cat;

                } else {

                    /*
                     * 如果发现重复猫，
                     * 删除多余的旧猫
                     */
                    cat.remove();
                }
            }
        }

        /*
         * 已经有猫
         */
        if (existingCat != null && !existingCat.isDead()) {

            existingCat.teleport(
                    player.getLocation()
            );

            existingCat.setCustomName(
                    "§d🐱 " + name
            );

            existingCat.setCustomNameVisible(true);

            existingCat.setOwner(player);
            existingCat.setTamed(true);

            return false;
        }

        /*
         * 没有猫，生成新的
         */
        Location loc = player.getLocation();

        Cat cat =
                (Cat) player.getWorld()
                        .spawnEntity(
                                loc,
                                EntityType.CAT
                        );

        cat.setCustomName(
                "§d🐱 " + name
        );

        cat.setCustomNameVisible(true);

        cat.setOwner(player);
        cat.setTamed(true);

        /*
         * 标记 Neko n' Yume 猫
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
                playerUUID.toString()
        );

        return true;
    }
}