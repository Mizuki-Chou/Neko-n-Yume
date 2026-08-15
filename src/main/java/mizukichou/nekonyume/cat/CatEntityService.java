package mizukichou.nekonyume.cat;

import io.papermc.paper.registry.RegistryKey;
import mizukichou.nekonyume.skill.CatBattleState;
import mizukichou.nekonyume.storage.CatStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 猫咪 Bukkit 实体服务。
 *
 * <p>
 * 职责：
 * 1. 实体恢复（登录 / 世界加载 / 召唤）；
 * 2. 实体绑定与逻辑猫关系（bindLogicalCat，含出生梦槽同步）；
 * 3. 花色 / 名称 / 行为模式 / 头顶名称；
 * 4. PDC Keys（catKey / ownerKey，由组合根注入）。
 * </p>
 *
 * <p>
 * 构造注入：plugin 仅用于调度器与 isEnabled()，
 * 数据读写走 CatStore，成长走 CatProgressionService，
 * 受伤恢复状态走 CatBattleState。
 * </p>
 */
public class CatEntityService {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final CatStore store;
    private final CatCache cache;
    private final CatProgressionService progression;
    private final CatBattleState battleState;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    private final MiniMessage mm =
            MiniMessage.miniMessage();

    private final Random random =
            new Random();

    /*
     * 防止同一个玩家同时执行多个 summon。
     */
    private final Set<UUID> summoning =
            ConcurrentHashMap.newKeySet();

    /*
     * 等待世界加载的实体恢复队列。
     * key = World UUID，value = 等待恢复的玩家 UUID。
     */
    private final ConcurrentHashMap<UUID, Set<UUID>>
            pendingWorldRestores =
            new ConcurrentHashMap<>();

    public CatEntityService(
            JavaPlugin plugin,
            Logger logger,
            CatStore store,
            CatCache cache,
            CatProgressionService progression,
            NamespacedKey catKey,
            NamespacedKey ownerKey,
            CatBattleState battleState
    ) {

        this.plugin = plugin;
        this.logger = logger;
        this.store = store;
        this.cache = cache;
        this.progression = progression;
        this.catKey = catKey;
        this.ownerKey = ownerKey;
        this.battleState = battleState;
    }

    /*
     * ============================================================
     * PDC Keys
     * ============================================================
     */

    public NamespacedKey getCatKey() {
        return catKey;
    }

    public NamespacedKey getOwnerKey() {
        return ownerKey;
    }

    /*
     * ============================================================
     * 从实体捕获状态（自动保存前调用）
     * ============================================================
     *
     * 实体在线时，把当前花色与位置同步回逻辑猫，
     * 供 CatManager.saveAllCats() 回写。
     */

    public void captureEntityState(Cat logicalCat) {

        if (logicalCat == null) {
            return;
        }

        UUID entityUUID =
                logicalCat.getEntityUuid();

        if (entityUUID == null) {
            return;
        }

        Entity entity =
                Bukkit.getEntity(entityUUID);

        if (!(entity instanceof org.bukkit.entity.Cat bukkitCat) ||
                bukkitCat.isDead() ||
                !bukkitCat.isValid()) {

            return;
        }

        Registry<org.bukkit.entity.Cat.Type> registry =
                getCatVariantRegistry();

        NamespacedKey key =
                registry.getKey(
                        bukkitCat.getCatType()
                );

        if (key != null) {

            logicalCat.setVariant(
                    key.toString()
            );
        }

        syncLogicalCatLocation(
                logicalCat,
                bukkitCat
        );
    }

    /*
     * ============================================================
     * 登录时恢复猫实体
     * ============================================================
     */

    public void restoreCatEntity(Player player) {

        if (player == null) {
            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        Cat logicalCat =
                cache.loadCat(player);

        if (logicalCat == null) {
            return;
        }

        /*
         * 1. 根据 Entity UUID 找原实体。
         */
        UUID savedEntityUUID =
                logicalCat.getEntityUuid();

        if (savedEntityUUID != null) {

            Entity entity =
                    Bukkit.getEntity(savedEntityUUID);

            if (entity instanceof org.bukkit.entity.Cat cat &&
                    !cat.isDead() &&
                    cat.isValid()) {

                String owner =
                        cat.getPersistentDataContainer()
                                .get(
                                        ownerKey,
                                        PersistentDataType.STRING
                                );

                if (playerUUID.toString().equals(owner)) {

                    updateCat(
                            cat,
                            player,
                            logicalCat.getName()
                    );

                    restoreCatVariant(
                            playerUUID,
                            cat,
                            logicalCat
                    );

                    syncLogicalCatLocation(
                            logicalCat,
                            cat
                    );

                    store.setCatEntityUUID(
                            playerUUID,
                            cat.getUniqueId()
                    );

                    cleanupDuplicateCats(
                            playerUUID,
                            cat
                    );

                    return;
                }
            }
        }

        /*
         * 2. 根据最后位置加载区块。
         */
        UUID worldUUID =
                store.getCatWorldUUID(playerUUID);

        if (worldUUID == null) {

            restoreCatEntityAtFallback(
                    player,
                    logicalCat
            );

            return;
        }

        World world =
                Bukkit.getWorld(worldUUID);

        if (world == null) {

            /*
             * 猫咪所在世界尚未加载。
             * 放入等待队列，世界加载完成后重试。
             */
            logger.warning(
                    "Cannot restore cat "
                            + logicalCat.getId()
                            + " for "
                            + player.getName()
                            + ": world "
                            + worldUUID
                            + " is not loaded. Waiting for world load."
            );

            pendingWorldRestores
                    .computeIfAbsent(
                            worldUUID,
                            key -> ConcurrentHashMap.newKeySet()
                    )
                    .add(playerUUID);

            return;
        }

        double x = logicalCat.getX();
        double z = logicalCat.getZ();

        int chunkX =
                ((int) Math.floor(x)) >> 4;

        int chunkZ =
                ((int) Math.floor(z)) >> 4;

        world.getChunkAtAsync(chunkX, chunkZ)
                .thenAccept(chunk -> {

                    if (!plugin.isEnabled()) {
                        return;
                    }

                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {

                                        try {

                                            if (!player.isOnline()) {
                                                return;
                                            }

                                            /*
                                             * 再次确认 Entity UUID。
                                             */
                                            if (logicalCat
                                                    .getEntityUuid() != null) {

                                                Entity existing =
                                                        Bukkit.getEntity(
                                                                logicalCat
                                                                        .getEntityUuid()
                                                        );

                                                if (existing
                                                        instanceof org.bukkit.entity.Cat cat &&
                                                        !cat.isDead() &&
                                                        cat.isValid()) {

                                                    String owner =
                                                            cat.getPersistentDataContainer()
                                                                    .get(
                                                                            ownerKey,
                                                                            PersistentDataType.STRING
                                                                    );

                                                    if (playerUUID
                                                            .toString()
                                                            .equals(owner)) {

                                                        updateCat(
                                                                cat,
                                                                player,
                                                                logicalCat.getName()
                                                        );

                                                        restoreCatVariant(
                                                                playerUUID,
                                                                cat,
                                                                logicalCat
                                                        );

                                                        syncLogicalCatLocation(
                                                                logicalCat,
                                                                cat
                                                        );

                                                        return;
                                                    }
                                                }
                                            }

                                            /*
                                             * 最后已知区块寻找。
                                             */
                                            org.bukkit.entity.Cat oldCat =
                                                    findCatInChunk(
                                                            chunk,
                                                            playerUUID
                                                    );

                                            if (oldCat != null &&
                                                    !oldCat.isDead() &&
                                                    oldCat.isValid()) {

                                                updateCat(
                                                        oldCat,
                                                        player,
                                                        logicalCat.getName()
                                                );

                                                restoreCatVariant(
                                                        playerUUID,
                                                        oldCat,
                                                        logicalCat
                                                );

                                                logicalCat.setEntityUuid(
                                                        oldCat.getUniqueId()
                                                );

                                                syncLogicalCatLocation(
                                                        logicalCat,
                                                        oldCat
                                                );

                                                store.setCatEntityUUID(
                                                        playerUUID,
                                                        oldCat.getUniqueId()
                                                );

                                                cleanupDuplicateCats(
                                                        playerUUID,
                                                        oldCat
                                                );

                                                return;
                                            }

                                            /*
                                             * 扫描当前已加载世界。
                                             */
                                            org.bukkit.entity.Cat loadedCat =
                                                    findLoadedCatForPlayer(
                                                            playerUUID
                                                    );

                                            if (loadedCat != null &&
                                                    !loadedCat.isDead() &&
                                                    loadedCat.isValid()) {

                                                updateCat(
                                                        loadedCat,
                                                        player,
                                                        logicalCat.getName()
                                                );

                                                restoreCatVariant(
                                                        playerUUID,
                                                        loadedCat,
                                                        logicalCat
                                                );

                                                logicalCat.setEntityUuid(
                                                        loadedCat.getUniqueId()
                                                );

                                                syncLogicalCatLocation(
                                                        logicalCat,
                                                        loadedCat
                                                );

                                                store.setCatEntityUUID(
                                                        playerUUID,
                                                        loadedCat.getUniqueId()
                                                );

                                                cleanupDuplicateCats(
                                                        playerUUID,
                                                        loadedCat
                                                );

                                                return;
                                            }

                                            /*
                                             * 原实体确实不存在。
                                             */
                                            restoreCatEntityAtSavedLocation(
                                                    player,
                                                    logicalCat,
                                                    world
                                            );

                                        } catch (Exception exception) {

                                            logger.severe(
                                                    "Failed to restore cat entity for "
                                                            + player.getName()
                                            );

                                            exception.printStackTrace();
                                        }
                                    }
                            );

                }).exceptionally(exception -> {

                    if (!plugin.isEnabled()) {
                        return null;
                    }

                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {

                                        try {

                                            logger.warning(
                                                    "Failed to restore cat "
                                                            + logicalCat.getId()
                                                            + " for "
                                                            + player.getName()
                                                            + ": "
                                                            + exception.getMessage()
                                            );

                                        } catch (Exception ex) {

                                            logger.severe(
                                                    "Failed to finish cat restore after chunk failure for "
                                                            + player.getName()
                                            );

                                            ex.printStackTrace();
                                        }
                                    }
                            );

                    return null;
                });
    }

    /*
     * ============================================================
     * 在保存位置恢复
     * ============================================================
     */

    private void restoreCatEntityAtSavedLocation(
            Player player,
            Cat logicalCat,
            World world
    ) {

        Location location =
                new Location(
                        world,
                        logicalCat.getX(),
                        logicalCat.getY(),
                        logicalCat.getZ(),
                        logicalCat.getYaw(),
                        logicalCat.getPitch()
                );

        org.bukkit.entity.Cat cat =
                (org.bukkit.entity.Cat) world.spawnEntity(
                        location,
                        EntityType.CAT
                );

        updateCat(
                cat,
                player,
                logicalCat.getName()
        );

        restoreCatVariant(
                player.getUniqueId(),
                cat,
                logicalCat
        );

        logicalCat.setEntityUuid(
                cat.getUniqueId()
        );

        syncLogicalCatLocation(
                logicalCat,
                cat
        );

        store.setCatEntityUUID(
                player.getUniqueId(),
                cat.getUniqueId()
        );

        cleanupDuplicateCats(
                player.getUniqueId(),
                cat
        );

        logger.info(
                "Restored cat "
                        + logicalCat.getName()
                        + " at saved location for "
                        + player.getName()
        );
    }

    /*
     * ============================================================
     * 没有位置时的兜底恢复
     * ============================================================
     */

    private void restoreCatEntityAtFallback(
            Player player,
            Cat logicalCat
    ) {

        Location location =
                player.getLocation().clone();

        World world =
                location.getWorld();

        if (world == null) {

            logger.warning(
                    "Cannot restore cat "
                            + logicalCat.getId()
                            + " for "
                            + player.getName()
                            + ": player world is null."
            );

            return;
        }

        org.bukkit.entity.Cat cat =
                (org.bukkit.entity.Cat) world.spawnEntity(
                        location,
                        EntityType.CAT
                );

        updateCat(
                cat,
                player,
                logicalCat.getName()
        );

        restoreCatVariant(
                player.getUniqueId(),
                cat,
                logicalCat
        );

        logicalCat.setEntityUuid(
                cat.getUniqueId()
        );

        syncLogicalCatLocation(
                logicalCat,
                cat
        );

        store.setCatEntityUUID(
                player.getUniqueId(),
                cat.getUniqueId()
        );

        saveCatLocation(player, cat);

        cleanupDuplicateCats(
                player.getUniqueId(),
                cat
        );

        logger.info(
                "Restored cat "
                        + logicalCat.getName()
                        + " at fallback location for "
                        + player.getName()
        );
    }

    /*
     * ============================================================
     * 世界加载后重试实体恢复
     * ============================================================
     */

    public void retryPendingWorldRestores(World world) {

        if (world == null) {
            return;
        }

        Set<UUID> players =
                pendingWorldRestores.remove(
                        world.getUID()
                );

        if (players == null || players.isEmpty()) {
            return;
        }

        for (UUID playerUUID : players) {

            Player player =
                    Bukkit.getPlayer(playerUUID);

            if (player != null && player.isOnline()) {

                restoreCatEntity(player);
            }
        }
    }

    /*
     * ============================================================
     * 清除玩家的待恢复记录
     * ============================================================
     */

    public void clearPendingRestore(UUID playerUUID) {

        if (playerUUID == null) {
            return;
        }

        for (Set<UUID> players :
                pendingWorldRestores.values()) {

            players.remove(playerUUID);
        }
    }

    /*
     * ============================================================
     * 清除实体绑定（不删除逻辑猫）
     * ============================================================
     */

    public void clearEntityBinding(UUID playerUUID) {

        if (playerUUID == null) {
            return;
        }

        Cat cat = cache.getCat(playerUUID);

        if (cat != null) {

            cat.setEntityUuid(null);
        }

        store.removeCatEntityUUID(playerUUID);
    }

    /*
     * ============================================================
     * 删除玩家的猫咪（管理操作，不可逆）
     * ============================================================
     *
     * 仅在 /nekoyumeadmin cat remove confirm 确认后调用。
     */

    public boolean removePlayerCat(UUID playerUUID) {

        if (playerUUID == null) {
            return false;
        }

        /*
         * 1. 移除实体。
         */
        UUID entityUuid =
                store.getCatEntityUUID(playerUUID);

        if (entityUuid != null) {

            Entity entity =
                    Bukkit.getEntity(entityUuid);

            if (entity != null && entity.isValid()) {

                entity.remove();
            }
        }

        /*
         * 2. 清除运行时缓存。
         */
        cache.removeByOwner(playerUUID);

        /*
         * 3. 清除待恢复队列。
         */
        clearPendingRestore(playerUUID);

        /*
         * 4. 删除持久化数据。
         */
        return store.removeCat(playerUUID);
    }

    /*
     * ============================================================
     * 主动召唤
     * ============================================================
     */

    public void spawnCat(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        if (!summoning.add(playerUUID)) {

            player.sendMessage(
                    mm.deserialize(
                            "<yellow>🐱 正在寻找你的猫咪，请稍等一下!</yellow>"
                    )
            );

            return;
        }

        /*
         * 包装回调：无论成功失败，只要回调被执行，
         * 就保证释放 summoning 标记。
         */
        Consumer<Boolean> wrappedCallback =
                result -> {

                    try {

                        callback.accept(result);

                    } finally {

                        summoning.remove(playerUUID);
                    }
                };

        try {

            findCat(
                    player,
                    name,
                    wrappedCallback
            );

        } catch (Exception exception) {

            summoning.remove(playerUUID);

            logger.severe(
                    "Failed to summon cat for "
                            + player.getName()
            );

            exception.printStackTrace();

            player.sendMessage(
                    mm.deserialize(
                            "<red>🐱 召唤猫咪时发生错误，请查看服务器日志。</red>"
                    )
            );
        }
    }

    /*
     * ============================================================
     * 主动召唤 - 寻找实体
     * ============================================================
     */

    private void findCat(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        cache.loadCat(player);

        UUID savedEntityUUID =
                store.getCatEntityUUID(playerUUID);

        if (savedEntityUUID != null) {

            Entity entity =
                    Bukkit.getEntity(savedEntityUUID);

            if (entity instanceof org.bukkit.entity.Cat cat &&
                    !cat.isDead() &&
                    cat.isValid()) {

                cleanupDuplicateCats(
                        playerUUID,
                        cat
                );

                bindLogicalCat(
                        player,
                        cat,
                        name
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

        loadLastKnownChunk(
                player,
                name,
                callback
        );
    }

    /*
     * ============================================================
     * 主动召唤 - 加载旧位置区块
     * ============================================================
     */

    private void loadLastKnownChunk(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        UUID worldUUID =
                store.getCatWorldUUID(playerUUID);

        if (worldUUID == null) {

            restoreNewCat(
                    player,
                    name,
                    callback
            );

            return;
        }

        World world =
                Bukkit.getWorld(worldUUID);

        if (world == null) {

            org.bukkit.entity.Cat loadedCat =
                    findLoadedCatForPlayer(playerUUID);

            if (loadedCat != null) {

                store.setCatEntityUUID(
                        playerUUID,
                        loadedCat.getUniqueId()
                );

                cleanupDuplicateCats(
                        playerUUID,
                        loadedCat
                );

                bindLogicalCat(
                        player,
                        loadedCat,
                        name
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
                store.getCatX(playerUUID);

        double z =
                store.getCatZ(playerUUID);

        int chunkX =
                ((int) Math.floor(x)) >> 4;

        int chunkZ =
                ((int) Math.floor(z)) >> 4;

        world.getChunkAtAsync(chunkX, chunkZ)
                .thenAccept(chunk -> {

                    if (!plugin.isEnabled()) {
                        return;
                    }

                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {

                                        try {

                                            org.bukkit.entity.Cat oldCat =
                                                    findCatInChunk(
                                                            chunk,
                                                            playerUUID
                                                    );

                                            if (oldCat != null &&
                                                    !oldCat.isDead() &&
                                                    oldCat.isValid()) {

                                                store.setCatEntityUUID(
                                                        playerUUID,
                                                        oldCat.getUniqueId()
                                                );

                                                cleanupDuplicateCats(
                                                        playerUUID,
                                                        oldCat
                                                );

                                                bindLogicalCat(
                                                        player,
                                                        oldCat,
                                                        name
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

                                            org.bukkit.entity.Cat loadedCat =
                                                    findLoadedCatForPlayer(
                                                            playerUUID
                                                    );

                                            if (loadedCat != null &&
                                                    !loadedCat.isDead() &&
                                                    loadedCat.isValid()) {

                                                store.setCatEntityUUID(
                                                        playerUUID,
                                                        loadedCat.getUniqueId()
                                                );

                                                cleanupDuplicateCats(
                                                        playerUUID,
                                                        loadedCat
                                                );

                                                bindLogicalCat(
                                                        player,
                                                        loadedCat,
                                                        name
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

                                        } catch (Exception exception) {

                                            logger.severe(
                                                    "Failed to process cat chunk for "
                                                            + player.getName()
                                            );

                                            exception.printStackTrace();

                                            callback.accept(false);
                                        }
                                    }
                            );

                }).exceptionally(exception -> {

                    if (!plugin.isEnabled()) {
                        return null;
                    }

                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {

                                        try {

                                            logger.warning(
                                                    "Failed to load cat chunk for "
                                                            + player.getName()
                                                            + ": "
                                                            + exception.getMessage()
                                            );

                                            org.bukkit.entity.Cat loadedCat =
                                                    findLoadedCatForPlayer(
                                                            playerUUID
                                                    );

                                            if (loadedCat != null &&
                                                    !loadedCat.isDead() &&
                                                    loadedCat.isValid()) {

                                                store.setCatEntityUUID(
                                                        playerUUID,
                                                        loadedCat.getUniqueId()
                                                );

                                                cleanupDuplicateCats(
                                                        playerUUID,
                                                        loadedCat
                                                );

                                                bindLogicalCat(
                                                        player,
                                                        loadedCat,
                                                        name
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

                                        } catch (Exception ex) {

                                            logger.severe(
                                                    "Failed to recover cat after chunk failure for "
                                                            + player.getName()
                                            );

                                            ex.printStackTrace();

                                            callback.accept(false);
                                        }
                                    }
                            );

                    return null;
                });
    }

    /*
     * ============================================================
     * 区块内寻找
     * ============================================================
     */

    private org.bukkit.entity.Cat findCatInChunk(
            Chunk chunk,
            UUID playerUUID
    ) {

        for (Entity entity :
                chunk.getEntities()) {

            if (!(entity instanceof org.bukkit.entity.Cat cat)) {
                continue;
            }

            if (cat.isDead() || !cat.isValid()) {
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

            if (!playerUUID.toString().equals(ownerUUID)) {
                continue;
            }

            return cat;
        }

        return null;
    }

    /*
     * ============================================================
     * 全部已加载世界寻找
     * ============================================================
     */

    private org.bukkit.entity.Cat findLoadedCatForPlayer(
            UUID playerUUID
    ) {

        for (World world :
                Bukkit.getWorlds()) {

            for (Entity entity :
                    world.getEntities()) {

                if (!(entity instanceof org.bukkit.entity.Cat cat)) {
                    continue;
                }

                if (cat.isDead() || !cat.isValid()) {
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

                if (playerUUID.toString().equals(ownerUUID)) {
                    return cat;
                }
            }
        }

        return null;
    }

    /*
     * ============================================================
     * 清理重复猫
     * ============================================================
     */

    private void cleanupDuplicateCats(
            UUID playerUUID,
            org.bukkit.entity.Cat keepCat
    ) {

        for (World world :
                Bukkit.getWorlds()) {

            for (Entity entity :
                    world.getEntities()) {

                if (!(entity instanceof org.bukkit.entity.Cat cat)) {
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

                if (!playerUUID.toString().equals(ownerUUID)) {
                    continue;
                }

                cat.remove();
            }
        }
    }

    /*
     * ============================================================
     * 主动召唤 - 传送到玩家
     * ============================================================
     */

    private void prepareTeleport(
            Player player,
            org.bukkit.entity.Cat cat,
            String name,
            Consumer<Boolean> callback,
            boolean replacement
    ) {

        if (cat.isDead() || !cat.isValid()) {

            restoreNewCat(
                    player,
                    name,
                    callback
            );

            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        updateCat(
                cat,
                player,
                name
        );

        Cat logicalCat =
                bindLogicalCat(
                        player,
                        cat,
                        name
                );

        restoreCatVariant(
                playerUUID,
                cat,
                logicalCat
        );

        syncLogicalCatLocation(
                logicalCat,
                cat
        );

        Location target =
                player.getLocation().clone();

        World targetWorld =
                target.getWorld();

        if (targetWorld == null) {

            callback.accept(replacement);

            return;
        }

        targetWorld.getChunkAtAsync(
                        target.getBlockX() >> 4,
                        target.getBlockZ() >> 4
                )
                .thenAccept(chunk -> {

                    if (!plugin.isEnabled()) {
                        return;
                    }

                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {

                                        try {

                                            if (cat.isDead() ||
                                                    !cat.isValid()) {

                                                restoreNewCat(
                                                        player,
                                                        name,
                                                        callback
                                                );

                                                return;
                                            }

                                            boolean success =
                                                    cat.teleport(target);

                                            if (!success) {

                                                success =
                                                        cat.teleport(target);
                                            }

                                            if (!success) {

                                                player.sendMessage(
                                                        mm.deserialize(
                                                                "<red>🐱 猫咪暂时无法传送，请稍后再试。</red>"
                                                        )
                                                );

                                                callback.accept(false);

                                                return;
                                            }

                                            syncLogicalCatLocation(
                                                    logicalCat,
                                                    cat
                                            );

                                            saveCatLocation(
                                                    player,
                                                    cat
                                            );

                                            store.setCatEntityUUID(
                                                    playerUUID,
                                                    cat.getUniqueId()
                                            );

                                            cleanupDuplicateCats(
                                                    playerUUID,
                                                    cat
                                            );

                                            callback.accept(replacement);

                                        } catch (Exception exception) {

                                            logger.severe(
                                                    "Failed to teleport cat for "
                                                            + player.getName()
                                            );

                                            exception.printStackTrace();

                                            callback.accept(false);
                                        }
                                    }
                            );

                }).exceptionally(exception -> {

                    if (!plugin.isEnabled()) {
                        return null;
                    }

                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {

                                        try {

                                            player.sendMessage(
                                                    mm.deserialize(
                                                            "<red>🐱 猫咪目标区块加载失败，请稍后再试。</red>"
                                                    )
                                            );

                                            logger.warning(
                                                    "Failed to load target chunk for "
                                                            + player.getName()
                                                            + ": "
                                                            + exception.getMessage()
                                            );

                                            callback.accept(false);

                                        } catch (Exception ex) {

                                            logger.severe(
                                                    "Failed to finish teleport after chunk failure for "
                                                            + player.getName()
                                            );

                                            ex.printStackTrace();

                                            callback.accept(false);
                                        }
                                    }
                            );

                    return null;
                });
    }

    /*
     * ============================================================
     * 主动召唤 - 恢复 / 新建实体
     * ============================================================
     */

    private void restoreNewCat(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 最后一次检查当前世界中是否已经有猫。
         */
        org.bukkit.entity.Cat existing =
                findLoadedCatForPlayer(playerUUID);

        if (existing != null &&
                !existing.isDead() &&
                existing.isValid()) {

            store.setCatEntityUUID(
                    playerUUID,
                    existing.getUniqueId()
            );

            cleanupDuplicateCats(
                    playerUUID,
                    existing
            );

            bindLogicalCat(
                    player,
                    existing,
                    name
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
         * 新建 Bukkit 猫实体。
         */
        org.bukkit.entity.Cat cat =
                (org.bukkit.entity.Cat) player.getWorld()
                        .spawnEntity(
                                player.getLocation(),
                                EntityType.CAT
                        );

        /*
         * 设置基础属性。
         */
        updateCat(
                cat,
                player,
                name
        );

        /*
         * 建立逻辑猫关系。
         */
        Cat logicalCat =
                bindLogicalCat(
                        player,
                        cat,
                        name
                );

        /*
         * 确定并永久保存花色。
         */
        restoreCatVariant(
                playerUUID,
                cat,
                logicalCat
        );

        /*
         * Entity UUID。
         */
        store.setCatEntityUUID(
                playerUUID,
                cat.getUniqueId()
        );

        /*
         * 位置。
         */
        saveCatLocation(
                player,
                cat
        );

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

                                syncLogicalCatLocation(
                                        logicalCat,
                                        cat
                                );
                            }
                        }
                );

        player.sendMessage(
                mm.deserialize(
                        "<yellow>🐱 原猫咪实体无法找到，<gray>已经恢复了一只相同的猫咪。</gray></yellow>"
                )
        );

        callback.accept(true);
    }

    /*
     * ============================================================
     * 创建 / 绑定逻辑猫
     * ============================================================
     *
     * 补丁 2：
     * 绑定完成后立即调用 syncSkillSlots，
     * 梦幻猫出生即拥有梦槽技能。
     */

    private Cat bindLogicalCat(
            Player player,
            org.bukkit.entity.Cat entity,
            String name
    ) {

        UUID ownerUUID =
                player.getUniqueId();

        Cat logicalCat =
                cache.getCat(ownerUUID);

        if (logicalCat == null) {

            /*
             * 显式绑定路径允许建档
             * （领取 / 召唤时玩家必定已有数据，这是兜底）。
             */
            store.ensureCat(ownerUUID);

            logicalCat =
                    cache.loadCat(player);
        }

        if (logicalCat == null) {
            return null;
        }

        logicalCat.setEntityUuid(
                entity.getUniqueId()
        );

        logicalCat.setName(name);

        syncLogicalCatLocation(
                logicalCat,
                entity
        );

        restoreCatVariant(
                ownerUUID,
                entity,
                logicalCat
        );

        /*
         * 补丁 2：
         * 梦幻猫出生即有 1 个梦槽，
         * 绑定完成必须同步一次技能槽，保证出生即抽取。
         */
        progression.syncSkillSlots(
                player,
                logicalCat
        );

        return logicalCat;
    }

    /*
     * ============================================================
     * 更新 Bukkit 猫实体
     * ============================================================
     */

    private void updateCat(
            org.bukkit.entity.Cat cat,
            Player player,
            String name
    ) {

        /*
         * 过滤 §，防止名字注入传统颜色码。
         */
        String safeName =
                name == null
                        ? ""
                        : name.replace("§", "");

        cat.setCustomName(
                "§d🐱 " + safeName
        );

        cat.setCustomNameVisible(true);

        cat.setOwner(player);

        cat.setTamed(true);

        /*
         * 猫参与战斗：可受伤，但不会死亡。
         * 受伤减免与致死保护由 CatEntityListener 处理。
         */
        cat.setInvulnerable(false);

        cat.getPersistentDataContainer()
                .set(
                        catKey,
                        PersistentDataType.BYTE,
                        (byte) 1
                );

        cat.getPersistentDataContainer()
                .set(
                        ownerKey,
                        PersistentDataType.STRING,
                        player.getUniqueId().toString()
                );
    }

    /*
     * ============================================================
     * 保存位置
     * ============================================================
     */

    private void saveCatLocation(
            Player player,
            org.bukkit.entity.Cat entity
    ) {

        Location location =
                entity.getLocation();

        if (location.getWorld() == null) {
            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        store.setCatLocation(
                playerUUID,
                location.getWorld().getUID(),
                location.getX(),
                location.getY(),
                location.getZ()
        );

        Cat logicalCat =
                cache.getCat(playerUUID);

        if (logicalCat == null) {
            return;
        }

        syncLogicalCatLocation(
                logicalCat,
                entity
        );
    }

    /*
     * ============================================================
     * 更新名称
     * ============================================================
     */

    public void updateCatName(
            Player player,
            String name
    ) {

        if (player == null ||
                name == null ||
                name.isBlank()) {

            return;
        }

        String safeName =
                name.replace("§", "");

        UUID playerUUID =
                player.getUniqueId();

        Cat logicalCat =
                cache.loadCat(player);

        if (logicalCat != null) {

            logicalCat.setName(safeName);
        }

        store.setCatName(
                playerUUID,
                safeName
        );

        UUID entityUUID =
                store.getCatEntityUUID(playerUUID);

        if (entityUUID == null) {
            return;
        }

        Entity entity =
                Bukkit.getEntity(entityUUID);

        if (!(entity instanceof org.bukkit.entity.Cat cat)) {
            return;
        }

        if (cat.isDead() || !cat.isValid()) {
            return;
        }

        refreshCustomName(
                cat,
                logicalCat
        );
    }

    /*
     * ============================================================
     * 切换行为模式
     * ============================================================
     */

    public void setCatBehaviorMode(
            Player player,
            CatBehaviorMode mode
    ) {

        if (player == null || mode == null) {
            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 防止猫被删除后通过 GUI 按钮触发 ensureCat 重建。
         */
        if (!store.hasCat(playerUUID)) {
            return;
        }

        Cat logicalCat =
                cache.loadCat(player);

        if (logicalCat == null) {
            return;
        }

        logicalCat.setBehaviorMode(mode);

        store.setCatBehaviorMode(
                playerUUID,
                mode.name()
        );

        /*
         * 立即应用到实体。
         */
        UUID entityUuid =
                logicalCat.getEntityUuid();

        if (entityUuid == null) {
            return;
        }

        Entity entity =
                Bukkit.getEntity(entityUuid);

        if (!(entity instanceof org.bukkit.entity.Cat cat) ||
                cat.isDead() ||
                !cat.isValid()) {

            return;
        }

        if (mode == CatBehaviorMode.SIT) {

            cat.setSitting(true);

        } else {

            cat.setSitting(false);
        }
    }

    /*
     * ============================================================
     * 刷新头顶名称
     * ============================================================
     *
     * 受伤恢复期内显示倒计时悬浮字。
     */

    public void refreshCustomName(
            org.bukkit.entity.Cat entity,
            Cat logicalCat
    ) {

        if (entity == null ||
                !entity.isValid() ||
                logicalCat == null) {

            return;
        }

        String safeName =
                logicalCat.getName()
                        .replace("§", "");

        /*
         * 受伤恢复期：悬浮字显示倒计时。
         */
        if (battleState.isRecovering(
                entity.getUniqueId()
        )) {

            int seconds =
                    battleState.getRecoveryRemainingSeconds(
                            entity.getUniqueId()
                    );

            entity.setCustomName(
                    "§c❤ "
                            + safeName
                            + " 受伤了 · "
                            + seconds
                            + "s 后恢复"
            );

        } else {

            entity.setCustomName(
                    "§d🐱 "
                            + safeName
                            + " "
                            + logicalCat.getMood().getHeadIcon()
            );
        }

        entity.setCustomNameVisible(true);
    }

    /*
     * ============================================================
     * 同步位置
     * ============================================================
     */

    private void syncLogicalCatLocation(
            Cat logicalCat,
            org.bukkit.entity.Cat entity
    ) {

        Location location =
                entity.getLocation();

        if (location.getWorld() == null) {
            return;
        }

        logicalCat.setWorldName(
                location.getWorld().getName()
        );

        logicalCat.setX(location.getX());
        logicalCat.setY(location.getY());
        logicalCat.setZ(location.getZ());
        logicalCat.setYaw(location.getYaw());
        logicalCat.setPitch(location.getPitch());

        logicalCat.setEntityUuid(
                entity.getUniqueId()
        );
    }

    /*
     * ============================================================
     * Cat Variant Registry
     * ============================================================
     */

    private Registry<org.bukkit.entity.Cat.Type>
    getCatVariantRegistry() {

        return io.papermc.paper.registry.RegistryAccess
                .registryAccess()
                .getRegistry(
                        RegistryKey.CAT_VARIANT
                );
    }

    /*
     * ============================================================
     * 随机花色
     * ============================================================
     */

    private org.bukkit.entity.Cat.Type getRandomCatType() {

        java.util.List<org.bukkit.entity.Cat.Type> types =
                getCatVariantRegistry()
                        .stream()
                        .toList();

        if (types.isEmpty()) {

            throw new IllegalStateException(
                    "No cat variants are registered!"
            );
        }

        return types.get(
                random.nextInt(types.size())
        );
    }

    /*
     * ============================================================
     * 保存花色
     * ============================================================
     */

    private String saveCatVariant(
            UUID playerUUID,
            org.bukkit.entity.Cat.Type variant
    ) {

        if (playerUUID == null || variant == null) {
            return null;
        }

        Registry<org.bukkit.entity.Cat.Type> registry =
                getCatVariantRegistry();

        NamespacedKey key =
                registry.getKey(variant);

        if (key == null) {
            return null;
        }

        String variantString =
                key.toString();

        store.setCatVariant(
                playerUUID,
                variantString
        );

        return variantString;
    }

    /*
     * ============================================================
     * 恢复 / 建立永久花色
     * ============================================================
     *
     * 规则：
     * 1. Cat 已有 variant → 使用 Cat 的 variant
     * 2. 存档已有 variant → 使用存档 variant
     * 3. 当前 Bukkit 实体存在 → 使用当前实体花色并永久保存
     * 4. 完全没有历史信息 → 随机一次，然后永久保存
     */

    private void restoreCatVariant(
            UUID playerUUID,
            org.bukkit.entity.Cat entity,
            Cat logicalCat
    ) {

        if (playerUUID == null ||
                entity == null ||
                logicalCat == null) {

            return;
        }

        /*
         * 1. 逻辑 Cat 已经有 variant。
         */
        String logicalVariant =
                logicalCat.getVariant();

        if (logicalVariant != null &&
                !logicalVariant.isBlank()) {

            org.bukkit.entity.Cat.Type variant =
                    getCatType(logicalVariant);

            if (variant != null) {

                entity.setCatType(variant);

                return;
            }
        }

        /*
         * 2. 从 CatStore 恢复。
         */
        String savedVariant =
                store.getCatVariant(playerUUID);

        if (savedVariant != null &&
                !savedVariant.isBlank()) {

            org.bukkit.entity.Cat.Type variant =
                    getCatType(savedVariant);

            if (variant != null) {

                entity.setCatType(variant);

                logicalCat.setVariant(savedVariant);

                return;
            }

            /*
             * 存档中的 variant 无效。
             * 不让插件崩溃，后面使用当前实体花色修复。
             */
        }

        /*
         * 3. 使用当前 Bukkit 实体已经拥有的花色。
         * 这个分支对老存档非常重要。
         */
        org.bukkit.entity.Cat.Type currentType =
                entity.getCatType();

        if (currentType == null) {

            /*
             * 4. 完全没有可用历史信息。
             * 只能随机一次。
             */
            currentType =
                    getRandomCatType();

            entity.setCatType(currentType);
        }

        String variantString =
                saveCatVariant(
                        playerUUID,
                        currentType
                );

        if (variantString != null) {

            logicalCat.setVariant(variantString);
        }
    }

    /*
     * ============================================================
     * NamespacedKey → Cat.Type
     * ============================================================
     */

    private org.bukkit.entity.Cat.Type getCatType(
            String variantString
    ) {

        if (variantString == null ||
                variantString.isBlank()) {

            return null;
        }

        NamespacedKey key =
                NamespacedKey.fromString(
                        variantString
                );

        if (key == null) {
            return null;
        }

        return getCatVariantRegistry()
                .get(key);
    }
}
