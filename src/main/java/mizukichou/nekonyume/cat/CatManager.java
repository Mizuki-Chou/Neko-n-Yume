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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class CatManager {

    private final NekoNYume plugin;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    private final Random random = new Random();

    /*
     * 防止同一个玩家同时执行多个 summon
     *
     * /ny summon
     * /ny summon
     * /ny summon
     *
     * 不会同时产生多个生成请求
     */
    private final Set<UUID> summoning =
            ConcurrentHashMap.newKeySet();

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
    public void spawnCat(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 防止重复请求
         */
        if (!summoning.add(playerUUID)) {

            player.sendMessage(
                    "§e🐱 正在寻找你的猫咪，请稍等一下！"
            );

            return;
        }

        /*
         * 最终结束时解除锁
         */
        try {

            findAndSummonCat(
                    player,
                    name,
                    result -> {

                        summoning.remove(
                                playerUUID
                        );

                        callback.accept(
                                result
                        );
                    }
            );

        } catch (Exception exception) {

            summoning.remove(
                    playerUUID
            );

            plugin.getLogger().severe(
                    "Failed to summon cat for "
                            + player.getName()
            );

            exception.printStackTrace();

            player.sendMessage(
                    "§c🐱 召唤猫咪时发生错误，请查看服务器日志。"
            );
        }
    }

    private void findAndSummonCat(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        /*
         * =========================
         * ① 读取已经保存的猫 UUID
         * =========================
         */

        UUID savedEntityUUID =
                plugin.getDataManager()
                        .getCatEntityUUID(
                                playerUUID
                        );

        /*
         * =========================
         * 有 UUID：
         * 绝对不能直接生成新猫
         * =========================
         */

        if (savedEntityUUID != null) {

            UUID savedWorldUUID =
                    plugin.getDataManager()
                            .getCatWorldUUID(
                                    playerUUID
                            );

            /*
             * 没有世界信息
             *
             * 暂时不要生成新猫
             */
            if (savedWorldUUID == null) {

                player.sendMessage(
                        "§c🐱 无法确定猫咪所在的世界。"
                                + "§7请联系管理员。"
                );

                callback.accept(false);
                return;
            }

            World world =
                    Bukkit.getWorld(
                            savedWorldUUID
                    );

            /*
             * 世界没有加载
             */
            if (world == null) {

                player.sendMessage(
                        "§c🐱 猫咪所在的世界当前未加载。"
                );

                callback.accept(false);
                return;
            }

            /*
             * 读取猫咪最后保存的位置
             */
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
             * 异步加载猫所在的区块
             */
            world.getChunkAtAsync(
                    chunkX,
                    chunkZ
            ).thenAccept(chunk -> {

                Bukkit.getScheduler()
                        .runTask(
                                plugin,
                                () -> {

                                    /*
                                     * 区块加载完成后
                                     * 再通过 UUID 查找猫
                                     */
                                    Entity entity =
                                            world.getEntity(
                                                    savedEntityUUID
                                            );

                                    /*
                                     * 找到了！
                                     */
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

                                        callback.accept(
                                                false
                                        );

                                        return;
                                    }

                                    /*
                                     * =========================
                                     * UUID 存在，
                                     * 但实体仍未找到
                                     *
                                     * 重要：
                                     * 这里绝对不能 spawnEntity
                                     * =========================
                                     */

                                    player.sendMessage(
                                            "§e🐱 暂时没有找到你的猫咪实体。"
                                                    + "§7为了防止重复生成，"
                                                    + "§7本次不会生成新的猫。"
                                    );

                                    callback.accept(
                                            false
                                    );
                                }
                        );
            });

            return;
        }

        /*
         * =========================
         * ② 没有实体 UUID
         *
         * 兼容早期版本数据
         * =========================
         */

        UUID savedWorldUUID =
                plugin.getDataManager()
                        .getCatWorldUUID(
                                playerUUID
                        );

        /*
         * 如果没有任何旧实体信息
         * 才允许生成新的猫
         */
        if (savedWorldUUID == null) {

            spawnNewCat(
                    player,
                    name,
                    callback
            );

            return;
        }

        World world =
                Bukkit.getWorld(
                        savedWorldUUID
                );

        if (world == null) {

            player.sendMessage(
                    "§c🐱 无法加载猫咪所在的世界。"
            );

            callback.accept(false);

            return;
        }

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

        world.getChunkAtAsync(
                chunkX,
                chunkZ
        ).thenAccept(chunk -> {

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {

                                Location savedLocation =
                                        new Location(
                                                world,
                                                x,
                                                y,
                                                z
                                        );

                                /*
                                 * 旧版本没有 entity UUID，
                                 * 尝试在旧位置附近寻找
                                 */
                                for (Entity entity :
                                        savedLocation
                                                .getNearbyEntities(
                                                        4,
                                                        4,
                                                        4
                                                )) {

                                    if (!(entity instanceof Cat cat)) {
                                        continue;
                                    }

                                    if (!cat.getPersistentDataContainer()
                                            .has(
                                                    catKey,
                                                    PersistentDataType.BYTE
                                            )) {

                                        continue;
                                    }

                                    String ownerUUID =
                                            cat.getPersistentDataContainer()
                                                    .get(
                                                            ownerKey,
                                                            PersistentDataType.STRING
                                                    );

                                    if (!playerUUID.toString()
                                            .equals(
                                                    ownerUUID
                                            )) {

                                        continue;
                                    }

                                    /*
                                     * 找到了旧猫
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

                                    callback.accept(
                                            false
                                    );

                                    return;
                                }

                                /*
                                 * 旧版本完全没有找到猫
                                 *
                                 * 由于没有 UUID 可以确认，
                                 * 这里才允许生成新猫。
                                 */
                                spawnNewCat(
                                        player,
                                        name,
                                        callback
                                );
                            }
                    );
        });
    }

    /*
     * =========================
     * 创建新猫
     * =========================
     */

    private void spawnNewCat(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        Cat cat =
                (Cat) player.getWorld()
                        .spawnEntity(
                                player.getLocation(),
                                EntityType.CAT
                        );

        /*
         * 第一次生成随机花色
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
         * 保存 UUID
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

        callback.accept(true);
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
         * 标记 Neko n' Yume 猫
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
     * Cat Variant Registry
     * =========================
     */

    private Registry<Cat.Type>
    getCatVariantRegistry() {

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

            saveCatVariant(
                    playerUUID,
                    cat.getCatType()
            );

            return;
        }

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

        Cat.Type variant =
                getCatVariantRegistry()
                        .get(key);

        if (variant == null) {

            saveCatVariant(
                    playerUUID,
                    cat.getCatType()
            );

            return;
        }

        cat.setCatType(
                variant
        );
    }

    /*
     * =========================
     * Getter
     * =========================
     */

    public NamespacedKey getCatKey() {
        return catKey;
    }

    public NamespacedKey getOwnerKey() {
        return ownerKey;
    }
}