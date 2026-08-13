package mizukichou.nekonyume.cat;

import io.papermc.paper.registry.RegistryKey;
import mizukichou.nekonyume.NekoNYume;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class CatManager {

    private final NekoNYume plugin;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    private final Random random = new Random();

    /*
     * 防止同一个玩家同时执行多个 summon
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
     * true  = 恢复/新生成了一只实体
     * false = 找到了原来的实体
     */
    public void spawnCat(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 防止同时执行多个召唤请求
         */
        if (!summoning.add(playerUUID)) {

            player.sendMessage(
                    "§e🐱 正在寻找你的猫咪，请稍等一下!"
            );

            return;
        }

        try {

            findCat(
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

    /*
     * =========================
     * 第一级寻找
     * =========================
     */

    private void findCat(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        UUID savedEntityUUID =
                plugin.getDataManager()
                        .getCatEntityUUID(
                                playerUUID
                        );

        /*
         * ① 已知 UUID，首先直接寻找
         */
        if (savedEntityUUID != null) {

            Entity entity =
                    Bukkit.getEntity(
                            savedEntityUUID
                    );

            if (entity instanceof Cat cat &&
                    !cat.isDead() &&
                    cat.isValid()) {

                cleanupDuplicateCats(
                        playerUUID,
                        cat
                );

                prepareTeleport(
                        player,
                        cat,
                        name,
                        callback,
                        false
                );

                return;
            }
        }

        /*
         * ② UUID 当前不可见：
         * 去最后已知位置加载区块
         */
        loadLastKnownChunk(
                player,
                name,
                callback
        );
    }

    /*
     * =========================
     * 加载猫最后已知区块
     * =========================
     */

    private void loadLastKnownChunk(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        UUID worldUUID =
                plugin.getDataManager()
                        .getCatWorldUUID(
                                playerUUID
                        );

        /*
         * 完全没有旧位置：
         * 只能创建逻辑宠物实体
         */
        if (worldUUID == null) {

            restoreNewCat(
                    player,
                    name,
                    callback
            );

            return;
        }

        World world =
                Bukkit.getWorld(
                        worldUUID
                );

        /*
         * 世界当前未加载。
         *
         * 不在这里卡死：
         * 继续寻找其他已加载世界中的旧猫。
         */
        if (world == null) {

            Cat loadedCat =
                    findLoadedCatForPlayer(
                            playerUUID
                    );

            if (loadedCat != null) {

                plugin.getDataManager()
                        .setCatEntityUUID(
                                playerUUID,
                                loadedCat.getUniqueId()
                        );

                cleanupDuplicateCats(
                        playerUUID,
                        loadedCat
                );

                prepareTeleport(
                        player,
                        loadedCat,
                        name,
                        callback,
                        false
                );

                return;
            }

            restoreNewCat(
                    player,
                    name,
                    callback
            );

            return;
        }

        double x =
                plugin.getDataManager()
                        .getCatX(
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
         * Paper 26.2：
         * 异步加载旧猫所在区块
         */
        world.getChunkAtAsync(
                chunkX,
                chunkZ
        ).thenAccept(chunk -> {

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {

                                Cat oldCat =
                                        findCatInChunk(
                                                chunk,
                                                playerUUID
                                        );

                                /*
                                 * 找到了原猫
                                 */
                                if (oldCat != null &&
                                        !oldCat.isDead() &&
                                        oldCat.isValid()) {

                                    plugin.getDataManager()
                                            .setCatEntityUUID(
                                                    playerUUID,
                                                    oldCat.getUniqueId()
                                            );

                                    cleanupDuplicateCats(
                                            playerUUID,
                                            oldCat
                                    );

                                    prepareTeleport(
                                            player,
                                            oldCat,
                                            name,
                                            callback,
                                            false
                                    );

                                    return;
                                }

                                /*
                                 * ③ 最后已知区块没有找到，
                                 * 再扫描当前所有已加载世界。
                                 *
                                 * 防止猫已经自己走远。
                                 */
                                Cat loadedCat =
                                        findLoadedCatForPlayer(
                                                playerUUID
                                        );

                                if (loadedCat != null &&
                                        !loadedCat.isDead() &&
                                        loadedCat.isValid()) {

                                    plugin.getDataManager()
                                            .setCatEntityUUID(
                                                    playerUUID,
                                                    loadedCat.getUniqueId()
                                            );

                                    cleanupDuplicateCats(
                                            playerUUID,
                                            loadedCat
                                    );

                                    prepareTeleport(
                                            player,
                                            loadedCat,
                                            name,
                                            callback,
                                            false
                                    );

                                    return;
                                }

                                /*
                                 * ④ 到这里确实找不到。
                                 * 恢复同款猫。
                                 */
                                restoreNewCat(
                                        player,
                                        name,
                                        callback
                                );
                            }
                    );

        }).exceptionally(exception -> {

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {

                                plugin.getLogger().warning(
                                        "Failed to load cat chunk for "
                                                + player.getName()
                                                + ": "
                                                + exception.getMessage()
                                );

                                /*
                                 * 区块加载失败时，
                                 * 再尝试扫描已加载世界。
                                 */
                                Cat loadedCat =
                                        findLoadedCatForPlayer(
                                                playerUUID
                                        );

                                if (loadedCat != null &&
                                        !loadedCat.isDead() &&
                                        loadedCat.isValid()) {

                                    plugin.getDataManager()
                                            .setCatEntityUUID(
                                                    playerUUID,
                                                    loadedCat.getUniqueId()
                                            );

                                    cleanupDuplicateCats(
                                            playerUUID,
                                            loadedCat
                                    );

                                    prepareTeleport(
                                            player,
                                            loadedCat,
                                            name,
                                            callback,
                                            false
                                    );

                                    return;
                                }

                                /*
                                 * 最终恢复同款实体
                                 */
                                restoreNewCat(
                                        player,
                                        name,
                                        callback
                                );
                            }
                    );

            return null;
        });
    }

    /*
     * =========================
     * 区块内寻找猫
     * =========================
     */

    private Cat findCatInChunk(
            Chunk chunk,
            UUID playerUUID
    ) {

        for (Entity entity :
                chunk.getEntities()) {

            if (!(entity instanceof Cat cat)) {
                continue;
            }

            if (cat.isDead() ||
                    !cat.isValid()) {
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

            if (ownerUUID == null) {
                continue;
            }

            if (!playerUUID.toString()
                    .equals(ownerUUID)) {

                continue;
            }

            return cat;
        }

        return null;
    }

    /*
     * =========================
     * 搜索所有当前已加载世界
     * =========================
     *
     * 这是备用搜索。
     *
     * 只在普通 UUID / 旧位置搜索失败时调用，
     * 不会每 tick 执行。
     */

    private Cat findLoadedCatForPlayer(
            UUID playerUUID
    ) {

        for (World world :
                Bukkit.getWorlds()) {

            for (Entity entity :
                    world.getEntities()) {

                if (!(entity instanceof Cat cat)) {
                    continue;
                }

                if (cat.isDead() ||
                        !cat.isValid()) {
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

                if (ownerUUID == null) {
                    continue;
                }

                if (playerUUID.toString()
                        .equals(ownerUUID)) {

                    return cat;
                }
            }
        }

        return null;
    }

    /*
     * =========================
     * 清理重复猫
     * =========================
     */

    private void cleanupDuplicateCats(
            UUID playerUUID,
            Cat keepCat
    ) {

        for (World world :
                Bukkit.getWorlds()) {

            for (Entity entity :
                    world.getEntities()) {

                if (!(entity instanceof Cat cat)) {
                    continue;
                }

                if (cat.isDead()) {
                    continue;
                }

                if (cat.equals(keepCat)) {
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

                if (ownerUUID == null) {
                    continue;
                }

                if (!playerUUID.toString()
                        .equals(ownerUUID)) {

                    continue;
                }

                /*
                 * 删除玩家多出来的旧猫
                 */
                cat.remove();
            }
        }
    }

    /*
     * =========================
     * 准备传送现有猫
     * =========================
     */

    private void prepareTeleport(
            Player player,
            Cat cat,
            String name,
            Consumer<Boolean> callback,
            boolean replacement
    ) {

        /*
         * 猫已经无效
         */
        if (cat.isDead() ||
                !cat.isValid()) {

            restoreNewCat(
                    player,
                    name,
                    callback
            );

            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 恢复花色
         */
        restoreCatVariant(
                playerUUID,
                cat
        );

        /*
         * 更新名字、主人和 PDC
         */
        updateCat(
                cat,
                player,
                name
        );

        /*
         * 获取玩家目标位置
         */
        Location target =
                player.getLocation().clone();

        World targetWorld =
                target.getWorld();

        if (targetWorld == null) {

            callback.accept(
                    replacement
            );

            return;
        }

        /*
         * 先加载玩家所在目标区块。
         *
         * 加载完成后再执行普通 teleport。
         */
        targetWorld.getChunkAtAsync(
                target.getBlockX() >> 4,
                target.getBlockZ() >> 4
        ).thenAccept(chunk -> {

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {

                                /*
                                 * 如果猫在等待时已经失效，
                                 * 恢复一只新的。
                                 */
                                if (cat.isDead() ||
                                        !cat.isValid()) {

                                    restoreNewCat(
                                            player,
                                            name,
                                            callback
                                    );

                                    return;
                                }

                                /*
                                 * 普通同步传送。
                                 *
                                 * 此时目标区块已经准备好。
                                 */
                                boolean success =
                                        cat.teleport(
                                                target
                                        );

                                /*
                                 * 某些特殊情况下第一次失败，
                                 * 再尝试一次。
                                 */
                                if (!success) {

                                    success =
                                            cat.teleport(
                                                    target
                                            );
                                }

                                if (!success) {

                                    /*
                                     * 不要直接复制猫。
                                     *
                                     * 先保留原实体，
                                     * 防止出现第二只。
                                     */
                                    player.sendMessage(
                                            "§c🐱 猫咪暂时无法传送，请稍后再试。"
                                    );

                                    callback.accept(
                                            false
                                    );

                                    return;
                                }

                                /*
                                 * 确认传送成功后保存位置
                                 */
                                saveCatLocation(
                                        player,
                                        cat
                                );

                                /*
                                 * 确保当前 UUID 正确
                                 */
                                plugin.getDataManager()
                                        .setCatEntityUUID(
                                                playerUUID,
                                                cat.getUniqueId()
                                        );

                                /*
                                 * 删除其他重复实体
                                 */
                                cleanupDuplicateCats(
                                        playerUUID,
                                        cat
                                );

                                callback.accept(
                                        replacement
                                );
                            }
                    );

        }).exceptionally(exception -> {

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {

                                player.sendMessage(
                                        "§c🐱 猫咪目标区块加载失败，请稍后再试。"
                                );

                                plugin.getLogger().warning(
                                        "Failed to load target chunk for "
                                                + player.getName()
                                                + ": "
                                                + exception.getMessage()
                                );

                                callback.accept(
                                        false
                                );
                            }
                    );

            return null;
        });
    }

    /*
     * =========================
     * 恢复同款新猫
     * =========================
     */

    private void restoreNewCat(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 生成之前再扫一次所有已加载世界。
         *
         * 防止旧猫实际上已经加载，
         * 但是之前的查询没有拿到。
         */
        Cat existing =
                findLoadedCatForPlayer(
                        playerUUID
                );

        if (existing != null &&
                !existing.isDead() &&
                existing.isValid()) {

            plugin.getDataManager()
                    .setCatEntityUUID(
                            playerUUID,
                            existing.getUniqueId()
                    );

            cleanupDuplicateCats(
                    playerUUID,
                    existing
            );

            prepareTeleport(
                    player,
                    existing,
                    name,
                    callback,
                    false
            );

            return;
        }

        /*
         * =========================
         * 创建新的逻辑实体
         * =========================
         */

        Cat cat =
                (Cat) player.getWorld()
                        .spawnEntity(
                                player.getLocation(),
                                EntityType.CAT
                        );

        /*
         * 恢复原来的花色
         */
        restoreCatVariant(
                playerUUID,
                cat
        );

        /*
         * 如果第一次生成且没有花色，
         * 给它随机一个花色
         */
        String variant =
                plugin.getDataManager()
                        .getCatVariant(
                                playerUUID
                        );

        if (variant == null ||
                variant.isBlank()) {

            Cat.Type randomType =
                    getRandomCatType();

            cat.setCatType(
                    randomType
            );

            saveCatVariant(
                    playerUUID,
                    randomType
            );
        }

        /*
         * 设置猫基础属性
         */
        updateCat(
                cat,
                player,
                name
        );

        /*
         * 先保存新的 UUID。
         *
         * 这一步非常重要：
         * 防止 EntityAddToWorldEvent
         * 把刚生成的新猫当成旧猫。
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

        /*
         * 下一 tick 再清理重复实体。
         */
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {

                            if (!cat.isDead() &&
                                    cat.isValid()) {

                                cleanupDuplicateCats(
                                        playerUUID,
                                        cat
                                );
                            }
                        }
                );

        player.sendMessage(
                "§e🐱 原猫咪实体无法找到，"
                        + "§7已经恢复了一只相同的猫咪。"
        );

        callback.accept(
                true
        );
    }

    /*
     * =========================
     * 更新猫咪基础属性
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

        if (cat.isDead() ||
                !cat.isValid()) {
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
         * 没有保存花色：
         * 暂时保留实体当前花色。
         *
         * 新猫在 restoreNewCat() 中
         * 会随机并保存。
         */
        if (variantString == null ||
                variantString.isBlank()) {

            return;
        }

        NamespacedKey key =
                NamespacedKey.fromString(
                        variantString
                );

        if (key == null) {
            return;
        }

        Cat.Type variant =
                getCatVariantRegistry()
                        .get(key);

        if (variant == null) {
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