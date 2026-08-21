package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 猫实体恢复与主动召唤流水线（从 CatEntityService 拆分）。
 *
 * <p>
 * 职责：
 * 1. 登录恢复（Entity UUID 直查 → 最后位置区块 → 已加载世界扫描
 *    → 保存位置重建 → 玩家位置兜底，共五级）；
 * 2. 主动召唤（/nekoyume cat spawn：寻找旧实体 → 加载旧区块 →
 *    全图扫描 → 新建）；
 * 3. 待恢复队列（PendingWorldRestores）；
 * 4. 重复实体清理（cleanupDuplicateCats）；
 * 5. 召唤传送（prepareTeleport）。
 * </p>
 *
 * <p>
 * 单线程模型：异步区块加载通过 runTask 回到主线程后继续。
 * </p>
 */
public class CatEntityRestorer {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final CatStore store;
    private final CatCache cache;
    private final CatVariantService variantService;
    private final Lang lang;
    private final CatEntityBinding binding;

    /*
     * 等待世界加载的实体恢复队列
     * （纯逻辑类，退出/世界加载竞态语义由单元测试覆盖）。
     */
    private final PendingWorldRestores pendingWorldRestores =
            new PendingWorldRestores();

    /*
     * 召唤代际（0.8.0 P0-2 修复）：
     * 每次召唤 / 退出都会递增。在途异步回调携带自己的
     * 代际 token，回到主线程后若代际已过期则直接丢弃结果，
     * 实现真正的异步取消语义（退出清标记不再重新打开竞争窗口）。
     */
    private final ConcurrentHashMap<UUID, Long> summonGeneration =
            new ConcurrentHashMap<>();

    public CatEntityRestorer(
            JavaPlugin plugin,
            Logger logger,
            CatStore store,
            CatCache cache,
            CatVariantService variantService,
            Lang lang,
            CatEntityBinding binding
    ) {

        this.plugin = plugin;
        this.logger = logger;
        this.store = store;
        this.cache = cache;
        this.variantService = variantService;
        this.lang = lang;
        this.binding = binding;
    }

    /*
     * ============================================================
     * 召唤代际（0.8.0 P0-2）
     * ============================================================
     */

    long beginSummon(
            UUID playerUUID
    ) {

        return summonGeneration.merge(
                playerUUID,
                1L,
                Long::sum
        );
    }

    boolean isCurrentSummon(
            UUID playerUUID,
            long token
    ) {

        return playerUUID != null &&
                summonGeneration.getOrDefault(
                        playerUUID,
                        0L
                ) == token;
    }

    void invalidateSummons(
            UUID playerUUID
    ) {

        if (playerUUID != null) {

            summonGeneration.merge(
                    playerUUID,
                    1L,
                    Long::sum
            );
        }
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
                                        binding.getOwnerKey(),
                                        PersistentDataType.STRING
                                );

                if (playerUUID.toString().equals(owner)) {

                    binding.updateCat(
                            cat,
                            player,
                            logicalCat.getName()
                    );

                    variantService.restoreVariant(
                            playerUUID,
                            cat,
                            logicalCat
                    );

                    binding.syncLogicalCatLocation(
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

            pendingWorldRestores.add(
                    worldUUID,
                    playerUUID
            );

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
                                                                            binding.getOwnerKey(),
                                                                            PersistentDataType.STRING
                                                                    );

                                                    if (playerUUID
                                                            .toString()
                                                            .equals(owner)) {

                                                        binding.updateCat(
                                                                cat,
                                                                player,
                                                                logicalCat.getName()
                                                        );

                                                        variantService.restoreVariant(
                                                                playerUUID,
                                                                cat,
                                                                logicalCat
                                                        );

                                                        binding.syncLogicalCatLocation(
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

                                                binding.updateCat(
                                                        oldCat,
                                                        player,
                                                        logicalCat.getName()
                                                );

                                                variantService.restoreVariant(
                                                        playerUUID,
                                                        oldCat,
                                                        logicalCat
                                                );

                                                logicalCat.setEntityUuid(
                                                        oldCat.getUniqueId()
                                                );

                                                binding.syncLogicalCatLocation(
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

                                                binding.updateCat(
                                                        loadedCat,
                                                        player,
                                                        logicalCat.getName()
                                                );

                                                variantService.restoreVariant(
                                                        playerUUID,
                                                        loadedCat,
                                                        logicalCat
                                                );

                                                logicalCat.setEntityUuid(
                                                        loadedCat.getUniqueId()
                                                );

                                                binding.syncLogicalCatLocation(
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

                                            logger.log(
                                                    Level.SEVERE,
                                                    "Failed to restore cat entity for "
                                                            + player.getName(),
                                                    exception
                                            );
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

                                            logger.log(
                                                    Level.SEVERE,
                                                    "Failed to finish cat restore after chunk failure for "
                                                            + player.getName(),
                                                    ex
                                            );
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

        binding.updateCat(
                cat,
                player,
                logicalCat.getName()
        );

        variantService.restoreVariant(
                player.getUniqueId(),
                cat,
                logicalCat
        );

        logicalCat.setEntityUuid(
                cat.getUniqueId()
        );

        binding.syncLogicalCatLocation(
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

        binding.updateCat(
                cat,
                player,
                logicalCat.getName()
        );

        variantService.restoreVariant(
                player.getUniqueId(),
                cat,
                logicalCat
        );

        logicalCat.setEntityUuid(
                cat.getUniqueId()
        );

        binding.syncLogicalCatLocation(
                logicalCat,
                cat
        );

        store.setCatEntityUUID(
                player.getUniqueId(),
                cat.getUniqueId()
        );

        binding.saveCatLocation(player, cat);

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

        /*
         * 取走该世界的全部等待玩家（空集时循环自然跳过）。
         */
        Set<UUID> players =
                pendingWorldRestores.consumeForWorld(
                        world.getUID()
                );

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

        pendingWorldRestores.removePlayer(
                playerUUID
        );
    }

    /*
     * ============================================================
     * 主动召唤 - 寻找实体
     * ============================================================
     */

    void findCat(
            Player player,
            String name,
            Consumer<Boolean> callback,
            long summonToken
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

                binding.bindLogicalCat(
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
                callback,
                summonToken
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
            Consumer<Boolean> callback,
            long summonToken
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

                binding.bindLogicalCat(
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

                                            /*
                                             * 0.8.0 P0-2：代际校验。
                                             * 退出/新召唤后，旧流水线的结果直接丢弃。
                                             */
                                            if (!isCurrentSummon(
                                                    playerUUID,
                                                    summonToken
                                            ) ||
                                                    !player.isOnline()) {

                                                return;
                                            }

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

                                                binding.bindLogicalCat(
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

                                                binding.bindLogicalCat(
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

                                            logger.log(
                                                    Level.SEVERE,
                                                    "Failed to process cat chunk for "
                                                            + player.getName(),
                                                    exception
                                            );

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

                                            /*
                                             * 0.8.0 P0-2：代际校验。
                                             * 退出/新召唤后，旧流水线的结果直接丢弃。
                                             */
                                            if (!isCurrentSummon(
                                                    playerUUID,
                                                    summonToken
                                            ) ||
                                                    !player.isOnline()) {

                                                return;
                                            }

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

                                                binding.bindLogicalCat(
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

                                            logger.log(
                                                    Level.SEVERE,
                                                    "Failed to recover cat after chunk failure for "
                                                            + player.getName(),
                                                    ex
                                            );

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
                            binding.getCatKey(),
                            PersistentDataType.BYTE
                    )) {

                continue;
            }

            String ownerUUID =
                    cat.getPersistentDataContainer()
                            .get(
                                    binding.getOwnerKey(),
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
                                binding.getCatKey(),
                                PersistentDataType.BYTE
                        )) {

                    continue;
                }

                String ownerUUID =
                        cat.getPersistentDataContainer()
                                .get(
                                        binding.getOwnerKey(),
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
                                binding.getCatKey(),
                                PersistentDataType.BYTE
                        )) {

                    continue;
                }

                String ownerUUID =
                        cat.getPersistentDataContainer()
                                .get(
                                        binding.getOwnerKey(),
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

        binding.updateCat(
                cat,
                player,
                name
        );

        Cat logicalCat =
                binding.bindLogicalCat(
                        player,
                        cat,
                        name
                );

        variantService.restoreVariant(
                playerUUID,
                cat,
                logicalCat
        );

        binding.syncLogicalCatLocation(
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
                                                        lang.forPlayer(player).message(
                                                                "entity.teleport-fail"
                                                        )
                                                );

                                                callback.accept(false);

                                                return;
                                            }

                                            binding.syncLogicalCatLocation(
                                                    logicalCat,
                                                    cat
                                            );

                                            binding.saveCatLocation(
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

                                            logger.log(
                                                    Level.SEVERE,
                                                    "Failed to teleport cat for "
                                                            + player.getName(),
                                                    exception
                                            );

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
                                                    lang.forPlayer(player).message(
                                                            "entity.chunk-fail"
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

                                            logger.log(
                                                    Level.SEVERE,
                                                    "Failed to finish teleport after chunk failure for "
                                                            + player.getName(),
                                                    ex
                                            );

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

            binding.bindLogicalCat(
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
        binding.updateCat(
                cat,
                player,
                name
        );

        /*
         * 建立逻辑猫关系。
         */
        Cat logicalCat =
                binding.bindLogicalCat(
                        player,
                        cat,
                        name
                );

        /*
         * 确定并永久保存花色。
         */
        variantService.restoreVariant(
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
        binding.saveCatLocation(
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

                                binding.syncLogicalCatLocation(
                                        logicalCat,
                                        cat
                                );
                            }
                        }
                );

        player.sendMessage(
                lang.forPlayer(player).message(
                        "entity.restored-new"
                )
        );

        callback.accept(true);
    }
}
