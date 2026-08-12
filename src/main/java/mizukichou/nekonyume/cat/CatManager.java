package mizukichou.nekonyume.cat;

import io.papermc.paper.registry.RegistryKey;
import mizukichou.nekonyume.NekoNYume;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class CatManager {

    private final NekoNYume plugin;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    private final Random random = new Random();

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

    /*
     * =========================
     * 召唤猫咪
     * =========================
     *
     * true  = 新生成
     * false = 找到原来的猫
     */

    public boolean spawnCat(
            Player player,
            String name
    ) {

        UUID playerUUID =
                player.getUniqueId();

        /*
         * ① 从 players.yml 获取实体 UUID
         */
        UUID savedEntityUUID =
                plugin.getDataManager()
                        .getCatEntityUUID(
                                playerUUID
                        );

        /*
         * ② 尝试直接找到原实体
         */
        if (savedEntityUUID != null) {

            Entity entity =
                    Bukkit.getEntity(
                            savedEntityUUID
                    );

            if (entity instanceof Cat cat &&
                    !cat.isDead()) {

                restoreCatVariant(
                        playerUUID,
                        cat
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

        /*
         * ③ 如果实体 UUID 找不到，
         * 根据保存的位置寻找
         */
        UUID savedWorldUUID =
                plugin.getDataManager()
                        .getCatWorldUUID(
                                playerUUID
                        );

        if (savedWorldUUID != null) {

            World world =
                    Bukkit.getWorld(
                            savedWorldUUID
                    );

            if (world != null) {

                double x =
                        plugin.getDataManager()
                                .getCatX(
                                        playerUUID
                                );

                double y =
                        plugin.getDataManager()
                                .getCatY(
                                        playerUUID
                                );

                double z =
                        plugin.getDataManager()
                                .getCatZ(
                                        playerUUID
                                );

                int chunkX =
                        ((int) Math.floor(x))
                                >> 4;

                int chunkZ =
                        ((int) Math.floor(z))
                                >> 4;

                /*
                 * 加载猫咪所在区块
                 */
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
                 * 在旧位置寻找自己的猫
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
                    if (!cat.getPersistentDataContainer()
                            .has(
                                    catKey,
                                    PersistentDataType.BYTE
                            )) {

                        continue;
                    }

                    /*
                     * 检查主人
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
                     * 找到原来的猫
                     */

                    plugin.getDataManager()
                            .setCatEntityUUID(
                                    playerUUID,
                                    cat.getUniqueId()
                            );

                    restoreCatVariant(
                            playerUUID,
                            cat
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
         * ④ 确定不存在原猫
         * 才创建新猫
         */

        Cat cat =
                (Cat) player.getWorld()
                        .spawnEntity(
                                player.getLocation(),
                                EntityType.CAT
                        );

        /*
         * 第一次生成：
         * 随机选择花色
         */
        Cat.Type variant =
                getRandomCatType();

        cat.setCatType(
                variant
        );

        /*
         * 保存花色
         */
        saveCatVariant(
                playerUUID,
                variant
        );

        /*
         * 设置基础属性
         */
        updateCat(
                cat,
                player,
                name
        );

        /*
         * 保存 Entity UUID
         */
        plugin.getDataManager()
                .setCatEntityUUID(
                        playerUUID,
                        cat.getUniqueId()
                );

        /*
         * 保存位置
         */
        saveCatLocation(
                player,
                cat
        );

        return true;
    }

    /*
     * =========================
     * 设置猫咪基础属性
     * =========================
     */

    private void updateCat(
            Cat cat,
            Player player,
            String name
    ) {

        cat.setCustomName(
                "§d🐱 " + name
        );

        cat.setCustomNameVisible(
                true
        );

        cat.setOwner(
                player
        );

        cat.setTamed(
                true
        );

        /*
         * 标记为 Neko n' Yume 猫
         */
        cat.getPersistentDataContainer()
                .set(
                        catKey,
                        PersistentDataType.BYTE,
                        (byte) 1
                );

        /*
         * 保存主人 UUID
         */
        cat.getPersistentDataContainer()
                .set(
                        ownerKey,
                        PersistentDataType.STRING,
                        player.getUniqueId()
                                .toString()
                );
    }

    /*
     * =========================
     * 保存猫咪位置
     * =========================
     */

    private void saveCatLocation(
            Player player,
            Cat cat
    ) {

        Location location =
                cat.getLocation();

        if (location.getWorld() == null) {
            return;
        }

        plugin.getDataManager()
                .setCatLocation(
                        player.getUniqueId(),
                        location.getWorld()
                                .getUID(),
                        location.getX(),
                        location.getY(),
                        location.getZ()
                );
    }

    /*
     * =========================
     * 更新猫咪名称
     * =========================
     */

    public void updateCatName(
            Player player,
            String name
    ) {

        UUID playerUUID =
                player.getUniqueId();

        UUID entityUUID =
                plugin.getDataManager()
                        .getCatEntityUUID(
                                playerUUID
                        );

        if (entityUUID == null) {
            return;
        }

        Entity entity =
                Bukkit.getEntity(
                        entityUUID
                );

        if (!(entity instanceof Cat cat)) {
            return;
        }

        if (cat.isDead()) {
            return;
        }

        cat.setCustomName(
                "§d🐱 " + name
        );

        cat.setCustomNameVisible(
                true
        );
    }

    /*
     * =========================
     * 获取 Cat Variant Registry
     * =========================
     */

    private Registry<Cat.Type> getCatVariantRegistry() {

        return io.papermc.paper.registry.RegistryAccess
                .registryAccess()
                .getRegistry(
                        RegistryKey.CAT_VARIANT
                );
    }

    /*
     * =========================
     * 随机花色
     * =========================
     *
     * 使用 Registry.stream()
     * 而不是 Cat.Type.values()
     */

    private Cat.Type getRandomCatType() {

        List<Cat.Type> types =
                getCatVariantRegistry()
                        .stream()
                        .toList();

        if (types.isEmpty()) {

            throw new IllegalStateException(
                    "No cat variants are registered!"
            );
        }

        return types.get(
                random.nextInt(
                        types.size()
                )
        );
    }

    /*
     * =========================
     * 保存花色
     * =========================
     *
     * 不使用 name()
     *
     * 保存：
     * minecraft:tabby
     * minecraft:calico
     * minecraft:black
     */

    private void saveCatVariant(
            UUID playerUUID,
            Cat.Type variant
    ) {

        Registry<Cat.Type> registry =
                getCatVariantRegistry();

        NamespacedKey key =
                registry.getKey(
                        variant
                );

        if (key == null) {
            return;
        }

        plugin.getDataManager()
                .setCatVariant(
                        playerUUID,
                        key.toString()
                );
    }

    /*
     * =========================
     * 恢复花色
     * =========================
     */

    private void restoreCatVariant(
            UUID playerUUID,
            Cat cat
    ) {

        String variantString =
                plugin.getDataManager()
                        .getCatVariant(
                                playerUUID
                        );

        /*
         * 老数据没有花色
         */
        if (variantString == null ||
                variantString.isBlank()) {

            /*
             * 读取猫当前真实花色
             */
            saveCatVariant(
                    playerUUID,
                    cat.getCatType()
            );

            return;
        }

        /*
         * 把：
         * minecraft:calico
         *
         * 转换为 NamespacedKey
         */
        NamespacedKey key =
                NamespacedKey.fromString(
                        variantString
                );

        if (key == null) {

            saveCatVariant(
                    playerUUID,
                    cat.getCatType()
            );

            return;
        }

        /*
         * 从 Registry 找回 Cat.Type
         */
        Cat.Type variant =
                getCatVariantRegistry()
                        .get(key);

        if (variant == null) {

            /*
             * 数据无效：
             * 保留当前实际花色
             */
            saveCatVariant(
                    playerUUID,
                    cat.getCatType()
            );

            return;
        }

        /*
         * 恢复花色
         */
        cat.setCatType(
                variant
        );
    }

    public NamespacedKey getCatKey() {
        return catKey;
    }

    public NamespacedKey getOwnerKey() {
        return ownerKey;
    }
}