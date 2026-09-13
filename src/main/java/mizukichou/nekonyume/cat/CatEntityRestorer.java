package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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

    private final CatEntityRuntime runtime;
    private final Logger logger;
    private final CatStore store;
    private final CatCache cache;
    private final CatVariantService variantService;
    private final Lang lang;
    private final CatEntityBinding binding;

    /*
     * 0.8.3：实体索引（恢复/清理路径的 O(1) 加速器）。
     */
    private final CatEntityIndex entityIndex;

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
    /*
     * 
     * 全局单调令牌序列 + 每玩家"当前令牌"——
     * 令牌永不重复（AtomicLong），退出/删除时移除条目，
     * 不再随玩家数量无界增长；
     * 旧流水线的陈旧令牌永远不可能撞上新会话。
     */
    private final AtomicLong summonTokenSequence =
            new AtomicLong();

    private final ConcurrentHashMap<UUID, Long> summonTokens =

            new ConcurrentHashMap<>();

    /*
     * 0.9.0更新：恢复与主动召唤的令牌 namespace
     * 分离——此前 restore 复用 summonTokens，会把在途的
     * 主动召唤令牌覆盖失效，导致 summoning 集合永久残留、
     * 玩家再也无法召唤。
     */
    private final ConcurrentHashMap<UUID, Long> restoreTokens =

            new ConcurrentHashMap<>();

    /*
     * 0.9.0更新：召唤 busy 集合移入本类（社区审查 R14-04）——
     * busy 归属与令牌归属统一，恢复/删除交叉失效时
     * 一并释放，不再出现"令牌已失效、busy 永久残留"。
     */
    private final java.util.Set<UUID> summoning =
            java.util.concurrent.ConcurrentHashMap
                    .newKeySet();

    boolean tryAcquireSummoning(
            UUID playerUUID
    ) {

        return playerUUID != null &&
                summoning.add(
                        playerUUID
                );
    }

    void releaseSummoning(
            UUID playerUUID
    ) {

        if (playerUUID != null) {

            summoning.remove(
                    playerUUID
            );
        }
    }

    public CatEntityRestorer(
            CatEntityRuntime runtime,
            Logger logger,
            CatStore store,
            CatCache cache,
            CatVariantService variantService,
            Lang lang,
            CatEntityBinding binding,
            CatEntityIndex entityIndex
    ) {

        this.runtime = runtime;
        this.logger = logger;
        this.store = store;
        this.cache = cache;
        this.variantService = variantService;
        this.lang = lang;
        this.binding = binding;
        this.entityIndex = entityIndex;
    }

    /*
     * ============================================================
     * 召唤代际（0.8.0 P0-2）
     * ============================================================
     */

    long beginSummon(
            UUID playerUUID
    ) {

        long token =
                summonTokenSequence.incrementAndGet();

        if (playerUUID != null) {

            /*
             * 0.9.0更新：新召唤取代在途恢复
             * （社区审查 R-1）——两种令牌此前只分存储
             * 不分语义，旧恢复回调在新召唤后仍被判为
             * 有效，两条流水线同时生效。交叉失效保证
             * 每名玩家同一时刻至多一条在途流水线。
             */
            restoreTokens.remove(
                    playerUUID
            );

            summonTokens.put(
                    playerUUID,
                    token
            );
        }

        return token;
    }

    long beginRestore(
            UUID playerUUID
    ) {

        long token =
                summonTokenSequence.incrementAndGet();

        if (playerUUID != null) {

            /*
             * 0.9.0更新：新恢复取代在途召唤
             * （社区审查 R-1，与 beginSummon 对称）。
             */
            summonTokens.remove(
                    playerUUID
            );

            restoreTokens.put(
                    playerUUID,
                    token
            );

            /*
             * 0.9.0更新：恢复取代在途召唤时同步释放 busy
             * （社区审查 R14-04）——否则旧召唤回调回来时
             * hasCurrentSummon 已为 false，释放分支依赖的
             * 正是这里先于回调的统一释放。
             */
            summoning.remove(
                    playerUUID
            );
        }

        return token;
    }



    /**
     * 0.9.0更新：召唤流水线的权威名字（审查 R11-1）——
     * 异步窗口内玩家可能 rename，开始时捕获的 name 是
     * 陈旧值；绑定/刷名必须用当前缓存/存档名字。
     */
    private String authoritativeName(
            UUID playerUUID,
            String fallback
    ) {

        Cat cached =
                cache.getCat(
                        playerUUID
                );

        if (cached != null &&
                cached.getName() != null &&
                !cached.getName().isBlank()) {

            return cached.getName();
        }

        String stored =
                store.getCatName(
                        playerUUID
                );

        if (stored != null &&
                !stored.isBlank()) {

            return stored;
        }

        return fallback;
    }

    /**
     * 是否存在当前有效的召唤令牌（0.9.0更新，
     * 社区审查 P-2：召唤流水线被恢复/删除取代后，
     * busy 标记的释放需要区分"被更新召唤取代"与
     * "被非召唤操作取代"）。
     */
    boolean hasCurrentSummon(
            UUID playerUUID
    ) {

        return playerUUID != null &&
                summonTokens.containsKey(
                        playerUUID
                );
    }

    boolean isCurrentSummon(
            UUID playerUUID,
            long token
    ) {

        if (playerUUID == null) {
            return false;
        }

        /*
         * 0.9.0更新：只认召唤令牌（社区审查 R-1）——
         * 此前本方法同时接受两种令牌，旧恢复回调在
         * 新召唤开始后仍判为有效。恢复流水线改用
         * isCurrentRestore。
         */
        Long current =
                summonTokens.get(
                        playerUUID
                );

        return current != null &&
                current == token;
    }

    /**
     * 恢复令牌校验（0.9.0更新，社区审查 R-1）。
     * 与 isCurrentSummon 语义分离：只认恢复令牌。
     */
    boolean isCurrentRestore(
            UUID playerUUID,
            long token
    ) {

        if (playerUUID == null) {
            return false;
        }

        Long current =
                restoreTokens.get(
                        playerUUID
                );

        return current != null &&
                current == token;
    }

    /**
     * 共享链路令牌校验（0.9.0更新，社区审查 R-1）。
     *
     * <p>
     * 召唤与恢复共用同一条实体查找链路
     * （loadLastKnownChunk → findCatInChunk → prepareTeleport
     * → restoreNewCat），链路内校验接受任一种令牌；
     * 安全性由 beginSummon/beginRestore 的交叉失效保证——
     * 每名玩家同一时刻至多一条在途流水线，旧令牌
     * 必已被清除。
     * </p>
     */
    boolean isCurrentOperation(
            UUID playerUUID,
            long token
    ) {

        if (playerUUID == null) {
            return false;
        }

        Long summon =
                summonTokens.get(
                        playerUUID
                );

        if (summon != null &&
                summon == token) {

            return true;
        }

        Long restoring =
                restoreTokens.get(
                        playerUUID
                );

        return restoring != null &&
                restoring == token;
    }

    void invalidateSummons(
            UUID playerUUID
    ) {

        if (playerUUID != null) {

            /*
             * 
             * 失效即移除——令牌全局单调不会复用，
             * 移除既杀掉全部在途回调，又不留无界增长。
             */
            summonTokens.remove(
                    playerUUID
            );

            restoreTokens.remove(
                    playerUUID
            );

            summoning.remove(
                    playerUUID
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
         * 
         * 登录恢复同样领取生命周期令牌。
         * 删除 →重新领取（generation++）后，在途异步回调
         * 立即失效——旧逻辑猫对象 A 绝不污染新一代猫 B。
         */
        long restoreToken =
                beginRestore(
                        playerUUID
                );

        /*
         * 1. 根据 Entity UUID 找原实体。
         */
        UUID savedEntityUUID =
                logicalCat.getEntityUuid();

        if (savedEntityUUID != null) {

            Entity entity =
                    runtime.getEntity(savedEntityUUID);

            if (entity instanceof org.bukkit.entity.Cat cat &&
                    !cat.isDead() &&
                    cat.isValid() &&
                    isOwnedCat(
                            cat,
                            playerUUID
                    ) &&
                    binding.matchesCatId(
                            cat,
                            logicalCat.getId()
                    )) {

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

                /*
                 * 0.9.0更新：同步完成的恢复必须消费
                 * 自己的生命周期令牌——令牌语义是"在途流水线"，
                 * 完成后仍留在 current 状态会让未来新增逻辑
                 * 误判"该流水线仍在进行"。
                 */
                invalidateSummons(
                        playerUUID
                );

                return;
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
                    logicalCat,
                    restoreToken
            );

            return;
        }

        World world =
                runtime.worldByUuid(worldUUID);

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

        runtime.chunkAtAsync(world, chunkX, chunkZ)
                .thenAccept(chunk -> {

                    if (!runtime.isPluginEnabled()) {
                        return;
                    }

                    runtime
                            .runTask(
                                    () -> {

                                        try {

                                            if (!player.isOnline()) {
                                                return;
                                            }

                                            /*
                                             * 
                                             * 登录恢复的异步窗口内数据可能被删除——
                                             * 无数据即中止，不重建实体。
                                             */
                                            if (!store.hasCat(
                                                    playerUUID
                                            )) {

                                                return;
                                            }

                                            /*
                                             * 
                                             * 代际校验：删除 +重新领取后旧回调失效。
                                             */
                                            if (!isCurrentRestore(
                                                    playerUUID,
                                                    restoreToken
                                            )) {

                                                return;
                                            }

                                            /*
                                             * 再次确认 Entity UUID。
                                             */
                                            if (logicalCat
                                                    .getEntityUuid() != null) {

                                                Entity existing =
                                                        runtime.getEntity(
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
                                                    world,
                                                    restoreToken
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

                    if (!runtime.isPluginEnabled()) {
                        return null;
                    }

                    runtime
                            .runTask(
                                    () -> {

                                        try {

                                            if (!player.isOnline() ||
                                                    !store.hasCat(
                                                            playerUUID
                                                    ) ||
                                                    !isCurrentRestore(
                                                            playerUUID,
                                                            restoreToken
                                                    )) {

                                                return;
                                            }

                                            logger.warning(
                                                    "Failed to restore cat "
                                                            + logicalCat.getId()
                                                            + " for "
                                                            + player.getName()
                                                            + ": "
                                                            + exception.getMessage()
                                                            + " — falling back to player location."
                                            );

                                            /*
                                             * 
                                             * 区块加载失败不能静默丢猫。
                                             * 降级到“玩家位置兜底重建”，
                                             * 与“无存档位置”的兜底语义一致，
                                             * 保证登录恢复永远有结果。
                                             * 重建路径自带清理重复猫逻辑，
                                             * 不会产生双猫。
                                             */
                                            restoreCatEntityAtFallback(
                                                    player,
                                                    logicalCat,
                                                    restoreToken
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
            World world,
            long restoreToken
    ) {

        /*
         * 
         * 恢复入口必须自证玩家数据仍存在——
         * 管理员删除猫咪后，任何重建入口都立即中止，
         * 不依赖调用方提前检查。
         */
        if (!store.hasCat(
                player.getUniqueId()
        )) {

            return;
        }

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
                runtime.spawnCat(
                        location
                );

        /*
         * 0.9.0更新：恢复流程同样绑定代际——
         * spawn 期间的新召唤/恢复/删除都可能让本次
         * 请求失效，提交前必须重做后置校验。
         */
        if (cat == null ||
                !cat.isValid() ||
                cat.isDead() ||
                !player.isOnline() ||
                !store.hasCat(
                        player.getUniqueId()
                ) ||
                (restoreToken >= 0 &&
                        !isCurrentRestore(
                                player.getUniqueId(),
                                restoreToken
                        ))) {

            if (cat != null) {
                cat.remove();
            }

            return;
        }

        /*
         * 0.9.0更新：尾部绑定事务化（社区审查 R14-03）——
         * 任一环节异常时移除刚生成的实体并回滚存档指向，
         * 不留"实体活着但恢复失败"的半成品。
         */
        /*
         * 0.9.0更新：成功终点统一消费生命周期令牌
         * （审查 R11）——与"已有实体快速路径"口径一致。
         */
        invalidateSummons(
                player.getUniqueId()
        );

        UUID previousEntityUuid =
                store.getCatEntityUUID(
                        player.getUniqueId()
                );

        try {

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

        } catch (Exception exception) {

            cat.remove();

            entityIndex.removeEntity(
                    cat.getUniqueId()
            );

            logicalCat.setEntityUuid(
                    previousEntityUuid
            );

            store.setCatEntityUUID(
                    player.getUniqueId(),
                    previousEntityUuid
            );

            logger.log(
                    Level.SEVERE,
                    "Failed to finish saved-location restore for "
                            + player.getName()
                            + "; spawned entity removed.",
                    exception
            );

            return;
        }

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
            Cat logicalCat,
            long restoreToken
    ) {

        /*
         * 
         * 与 restoreCatEntityAtSavedLocation 同口径——
         * 数据已被删除时绝不重建实体。
         */
        if (!store.hasCat(
                player.getUniqueId()
        )) {

            return;
        }

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
                runtime.spawnCat(
                        location
                );

        /*
         * 0.9.0更新：spawn 后置校验（社区审查 E-1）——
         * CreatureSpawnEvent 是同步的，第三方监听器可在
         * spawn 期间删除实体/玩家数据/触发新召唤；
         * 与 saved-location 路径同口径：提交前必须确认
         * 实体有效、玩家在线、存档仍存在。
         */
        /*
         * 0.9.0更新：补代际校验（社区审查 P0-4）——fallback
         * 是异步链路的最后一环，新召唤/删除可能在途中取代
         * 本流水线；spawn 后 token 已失效必须立即中止并
         * 移除实体，否则旧恢复抢占新召唤的实体绑定。
         */
        if (cat == null ||
                !cat.isValid() ||
                cat.isDead() ||
                !player.isOnline() ||
                !store.hasCat(
                        player.getUniqueId()
                ) ||
                (restoreToken >= 0 &&
                        !isCurrentRestore(
                                player.getUniqueId(),
                                restoreToken
                        ))) {

            if (cat != null) {
                cat.remove();
            }

            return;
        }

        /*
         * 0.9.0更新：绑定与持久化全程异常回滚
         * （社区审查 E-3）——spawn 已产生物理实体，
         * 后续任一步失败都必须移除新猫，否则留下
         * 孤儿猫（下次恢复找不到存档猫又 spawn 一只）。
         */
        /*
         * 0.9.0更新：成功终点统一消费生命周期令牌
         * （审查 R11）——与"已有实体快速路径"口径一致。
         */
        invalidateSummons(
                player.getUniqueId()
        );

        UUID previousEntityUuid =
                store.getCatEntityUUID(
                        player.getUniqueId()
                );

        try {

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

        } catch (RuntimeException exception) {

            logger.log(
                    java.util.logging.Level.SEVERE,
                    "Fallback restore failed for "
                            + logicalCat.getName()
                            + " (" + player.getName()
                            + "); removing spawned cat.",
                    exception
            );

            try {

                cat.remove();

            } catch (RuntimeException cleanupException) {

                exception.addSuppressed(
                        cleanupException
                );
            }

            /*
             * 0.9.0更新：回滚存档指向/索引/逻辑猫
             * （社区审查 P0-4 收尾，与 saved-location 同口径）。
             */
            entityIndex.removeEntity(
                    cat.getUniqueId()
            );

            logicalCat.setEntityUuid(
                    previousEntityUuid
            );

            store.setCatEntityUUID(
                    player.getUniqueId(),
                    previousEntityUuid
            );
        }
    }

    /*
     * ============================================================
     * 世界加载后重试实体恢复
     * ============================================================
     */

    /*
     * 
     * 世界卸载 → 该世界的待恢复记录作废（对称于 retry）。
     */
    public void forgetPendingWorldRestores(
            World world
    ) {

        if (world != null) {

            pendingWorldRestores.forgetWorld(
                    world.getUID()
            );
        }
    }

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

    /*
     * 
     * PDC 归属校验——猫标记与主人 UUID 必须同时匹配，
     * 防止把其他插件/原版/回档实体“收编”成本插件猫。
     */
    private boolean isOwnedCat(
            org.bukkit.entity.Cat cat,
            UUID playerUUID
    ) {

        if (!cat.getPersistentDataContainer()
                .has(
                        binding.getCatKey(),
                        PersistentDataType.BYTE
                )) {

            return false;
        }

        String owner =
                cat.getPersistentDataContainer()
                        .get(
                                binding.getOwnerKey(),
                                PersistentDataType.STRING
                        );

        return playerUUID.toString()
                .equals(owner);
    }

    void findCat(
            Player player,
            String name,
            Consumer<SummonResult> callback,
            long summonToken
    ) {

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 0.9.0更新：loadCat 是状态创建操作
         * （加载/修复/写缓存），过期请求连它都不能执行。
         */
        if (summonToken >= 0 &&
                !isCurrentSummon(
                        playerUUID,
                        summonToken
                )) {

            callback.accept(
                    SummonResult.FAILED
            );

            return;
        }

        cache.loadCat(player);

        UUID savedEntityUUID =
                store.getCatEntityUUID(playerUUID);

        if (savedEntityUUID != null) {

            Entity entity =
                    runtime.getEntity(savedEntityUUID);

            /*
             * 
             * Entity UUID 分支必须验证 PDC 归属——
             * 只有“猫标记 + 主人一致”的实体才允许收编。
             * 存档损坏 / 其他插件实体 / 世界回档
             * 都不能被 updateCat() 强行占为己有。
             */
            if (entity instanceof org.bukkit.entity.Cat cat &&
                    !cat.isDead() &&
                    cat.isValid() &&
                    isOwnedCat(
                            cat,
                            playerUUID
                    ) &&
                    binding.matchesCatId(
                            cat,
                            store.getCatUUID(
                                    playerUUID
                            )
                    )) {

                cleanupDuplicateCats(
                        playerUUID,
                        cat
                );

                binding.bindLogicalCat(
                        player,
                        cat,
                        authoritativeName(
                                playerUUID,
                                name
                        )
                );

                prepareTeleport(
                        player,
                        cat,
                        name,
                        callback,
                        summonToken
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
            Consumer<SummonResult> callback,
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
                    callback,
                    summonToken
            );

            return;
        }

        World world =
                runtime.worldByUuid(worldUUID);

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
                        summonToken
                );

                return;
            }

            restoreNewCat(
                    player,
                    name,
                    callback,
                    summonToken
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

        runtime.chunkAtAsync(world, chunkX, chunkZ)
                .thenAccept(chunk -> {

                    if (!runtime.isPluginEnabled()) {
                        return;
                    }

                    runtime
                            .runTask(
                                    () -> {

                                        try {

                                            /*
                                             * 0.8.0 P0-2：代际校验。
                                             * 退出/新召唤后，旧流水线的结果直接丢弃。
                                             */
                                            if (!isCurrentOperation(
                                                    playerUUID,
                                                    summonToken
                                            ) ||
                                                    !player.isOnline() ||
                                                    !store.hasCat(
                                                            playerUUID
                                                    )) {

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
                                                        summonToken
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
                                                        authoritativeName(
                                                                playerUUID,
                                                                name
                                                        )
                                                );

                                                prepareTeleport(
                                                        player,
                                                        loadedCat,
                                                        name,
                                                        callback,
                                                        summonToken
                                                );

                                                return;
                                            }

                                            restoreNewCat(
                                                    player,
                                                    name,
                                                    callback,
                                                    summonToken
                                            );

                                        } catch (Exception exception) {

                                            logger.log(
                                                    Level.SEVERE,
                                                    "Failed to process cat chunk for "
                                                            + player.getName(),
                                                    exception
                                            );

                                            callback.accept(
                                                    SummonResult.FAILED
                                            );
                                        }
                                    }
                            );

                }).exceptionally(exception -> {

                    if (!runtime.isPluginEnabled()) {
                        return null;
                    }

                    runtime
                            .runTask(
                                    () -> {

                                        try {

                                            /*
                                             * 0.8.0 P0-2：代际校验。
                                             * 退出/新召唤后，旧流水线的结果直接丢弃。
                                             */
                                            if (!isCurrentOperation(
                                                    playerUUID,
                                                    summonToken
                                            ) ||
                                                    !player.isOnline() ||
                                                    !store.hasCat(
                                                            playerUUID
                                                    )) {

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
                                                        authoritativeName(
                                                                playerUUID,
                                                                name
                                                        )
                                                );

                                                prepareTeleport(
                                                        player,
                                                        loadedCat,
                                                        name,
                                                        callback,
                                                        summonToken
                                                );

                                                return;
                                            }

                                            restoreNewCat(
                                                    player,
                                                    name,
                                                    callback,
                                                    summonToken
                                            );

                                        } catch (Exception ex) {

                                            logger.log(
                                                    Level.SEVERE,
                                                    "Failed to recover cat after chunk failure for "
                                                            + player.getName(),
                                                    ex
                                            );

                                            callback.accept(
                                                    SummonResult.FAILED
                                            );
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

        /*
         * 0.8.3：第一遍——实体索引 O(1) 查找。
         * 陈旧条目（实体已消失 / UUID 被其他实体复用 /
         * PDC 归属不符）会被顺带清理。
         */
        for (UUID entityUuid :
                entityIndex.entitiesOf(playerUUID)) {

            Entity indexed =
                    runtime.getEntity(entityUuid);

            if (indexed instanceof org.bukkit.entity.Cat cat &&
                    !cat.isDead() &&
                    cat.isValid() &&
                    isOwnedCat(
                            cat,
                            playerUUID
                    )) {

                return cat;
            }

            entityIndex.removeEntity(entityUuid);
        }

        /*
         * 第二遍——全图扫描兑底（索引永远不是唯一事实来源）。
         */
        for (World world :
                runtime.worlds()) {

            /*
             */
            for (org.bukkit.entity.Cat cat :
                    runtime.catsIn(world)) {

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

    /*
     * 
     * 删除玩家猫咪时清理全部已加载世界中属于该玩家的
     * 所有猫实体——包括游离的重复实体，而不仅是
     * 存档登记的 Entity UUID。防止残留实体被后续绑定
     * 路径重新“复活”。仅用于管理删除这一低频操作，
     * 全图扫描成本可接受。
     */
    public void cleanupAllOwnedEntities(
            UUID playerUUID
    ) {

        if (playerUUID == null) {
            return;
        }

        /*
         * 0.8.3：第一遍——实体索引 O(1) 清理。
         *
         * 0.9.0更新：索引命中也必须验证 PDC
         * 归属——索引污染时“删除 A 却命中 B 的实体”
         * 会误删别人的猫；第二遍扫描只能防漏删、防不了
         * 已经执行的误删。
         */
        for (UUID entityUuid :
                entityIndex.entitiesOf(playerUUID)) {

            Entity indexed =
                    runtime.getEntity(entityUuid);

            if (indexed instanceof org.bukkit.entity.Cat cat &&
                    !cat.isDead() &&
                    isOwnedCat(
                            cat,
                            playerUUID
                    )) {

                cat.remove();
            }

            entityIndex.removeEntity(entityUuid);
        }

        /*
         * 第二遍——全图扫描兑底（索引永远不是唯一事实来源）。
         */
        for (World world :
                runtime.worlds()) {

            /*
     * 服务端层过滤（同 cleanupDuplicateCats），比逐个实体遍历省事多啦。
             */
            for (org.bukkit.entity.Cat cat :
                    runtime.catsIn(world)) {

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

                if (playerUUID.toString()
                        .equals(ownerUUID)) {

                    entityIndex.removeEntity(
                            cat.getUniqueId()
                    );

                    cat.remove();
                }
            }
        }

        entityIndex.removeOwner(playerUUID);
    }

    void cleanupDuplicateCats(
            UUID playerUUID,
            org.bukkit.entity.Cat keepCat
    ) {

        /*
         * 0.8.3：第一遍——实体索引 O(1) 清理。
         */
        for (UUID entityUuid :
                entityIndex.entitiesOf(playerUUID)) {

            if (entityUuid.equals(
                    keepCat.getUniqueId()
            )) {

                continue;
            }

            Entity indexed =
                    runtime.getEntity(entityUuid);

            /*
             * 0.9.0更新：索引路径同样校验 PDC 所有权
             * （社区审查 P2-1）——索引是尽力而为的
             * 缓存加速器，不是真相来源；owner 不符的
             * 实体绝不删除。
             */
            if (indexed instanceof org.bukkit.entity.Cat cat &&
                    !cat.isDead() &&
                    cat.isValid() &&
                    isOwnedCat(
                            cat,
                            playerUUID
                    )) {

                cat.remove();
            }

            entityIndex.removeEntity(entityUuid);
        }

        /*
         * 第二遍——全图扫描兑底。
         */
        for (World world :
                runtime.worlds()) {

            /*
             * 
             * getEntitiesByClass(Cat.class)由服务端在实体切片层过滤，
             * 避免遍历世界中全部实体类型；
             * 语义与逐实体 instanceof 判定完全一致。
             */
            for (org.bukkit.entity.Cat cat :
                    runtime.catsIn(world)) {

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

                entityIndex.removeEntity(
                        cat.getUniqueId()
                );

                cat.remove();
            }
        }

        /*
         * keepCat 即为该玩家当前唯一实体，登记索引。
         */
        entityIndex.put(
                keepCat.getUniqueId(),
                playerUUID
        );
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
            Consumer<SummonResult> callback,
            long summonToken
    ) {

        /*
         * 0.9.0更新：token 校验必须先于一切副作用——
         * 过期召唤连实体 PDC / variant / location 都不能碰，
         * 而不仅仅是"最终不 commit"。
         */
        if (summonToken >= 0 &&
                !isCurrentOperation(
                        player.getUniqueId(),
                        summonToken
                )) {

            callback.accept(
                    SummonResult.FAILED
            );

            return;
        }

        if (cat.isDead() || !cat.isValid()) {

            restoreNewCat(
                    player,
                    name,
                    callback,
                    summonToken
            );

            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 0.9.0更新：绑定用权威名字（审查 R11-1/2）。
         */
        String currentName =
                authoritativeName(
                        playerUUID,
                        name
                );

        binding.updateCat(
                cat,
                player,
                currentName
        );

        Cat logicalCat =
                binding.bindLogicalCat(
                        player,
                        cat,
                        currentName
                );

        /*
         * 玩家数据已被删除时中止流水线，
         * 绝不把已删除的猫重新绑回实体。
         */
        if (logicalCat == null) {

            callback.accept(
                    SummonResult.FAILED
            );

            return;
        }

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

            callback.accept(
                    SummonResult.ALREADY_PRESENT
            );

            return;
        }

        runtime.chunkAtAsync(
                        targetWorld,
                        target.getBlockX() >> 4,
                        target.getBlockZ() >> 4
                )
                .thenAccept(chunk -> {

                    if (!runtime.isPluginEnabled()) {
                        return;
                    }

                    runtime
                            .runTask(
                                    () -> {

                                        try {

                                            /*
                                             * 
                                             * 异步回调回归主线程后必须重新验证：
                                             * 1. 召唤代际未失效（退出/删除/新召唤）
                                             * 2. 玩家仍在线
                                             * 3. 玩家数据仍存在（管理员删除后绝不重连）
                                             * 否则旧回调可能把旧实体写回新一代猫的存档。
                                             */
                                            if (summonToken >= 0 &&
                                                    !isCurrentOperation(
                                                            playerUUID,
                                                            summonToken
                                                    )) {

                                                return;
                                            }

                                            if (!player.isOnline()) {
                                                return;
                                            }

                                            if (!store.hasCat(
                                                    playerUUID
                                            )) {

                                                return;
                                            }

                                            if (cat.isDead() ||
                                                    !cat.isValid()) {

                                                restoreNewCat(
                                                        player,
                                                        name,
                                                        callback,
                                                        summonToken
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

                                                callback.accept(
                                                        SummonResult.FAILED
                                                );

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

                                            callback.accept(
                                                    SummonResult.ALREADY_PRESENT
                                            );

                                        } catch (Exception exception) {

                                            logger.log(
                                                    Level.SEVERE,
                                                    "Failed to teleport cat for "
                                                            + player.getName(),
                                                    exception
                                            );

                                            callback.accept(
                                                    SummonResult.FAILED
                                            );
                                        }
                                    }
                            );

                }).exceptionally(exception -> {

                    if (!runtime.isPluginEnabled()) {
                        return null;
                    }

                    runtime
                            .runTask(
                                    () -> {

                                        try {

                                            /*
                                             * 0.9.0更新：失败路径同样
                                             * 必须验证代际——旧召唤的迟到失败
                                             * 不得污染新召唤的结果。
                                             */
                                            if (!isCurrentOperation(
                                                    playerUUID,
                                                    summonToken
                                            )) {

                                                return;
                                            }

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

                                            callback.accept(
                                                    SummonResult.FAILED
                                            );

                                        } catch (Exception ex) {

                                            logger.log(
                                                    Level.SEVERE,
                                                    "Failed to finish teleport after chunk failure for "
                                                            + player.getName(),
                                                    ex
                                            );

                                            callback.accept(
                                                    SummonResult.FAILED
                                            );
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
            Consumer<SummonResult> callback,
            long summonToken
    ) {

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 
         * 异步窗口内玩家数据可能已被管理员删除——
         * 无数据即中止，绝不“复活”已删除的猫。
         */
        if (!store.hasCat(playerUUID)) {

            callback.accept(
                    SummonResult.FAILED
            );

            return;
        }

        /*
         * 
         * 同步重建入口同样自证代际未失效，
         * 旧召唤不得把新建实体写回新一代猫的存档。
         */
        if (summonToken >= 0 &&
                !isCurrentOperation(
                        playerUUID,
                        summonToken
                )) {

            callback.accept(
                    SummonResult.FAILED
            );

            return;
        }

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

            if (binding.bindLogicalCat(
                    player,
                    existing,
                    name
            ) == null) {

                callback.accept(
                        SummonResult.FAILED
                );

                return;
            }

            prepareTeleport(
                    player,
                    existing,
                    name,
                    callback,
                    summonToken
            );

            return;
        }

        /*
         * 新建 Bukkit 猫实体。
         */
        org.bukkit.entity.Cat cat =
                runtime.spawnCat(
                        player.getLocation()
                );

        /*
         * 0.9.0更新：spawn 会触发同步事件（CreatureSpawn /
         * 其它插件监听器），期间状态可能被重入破坏——
         * 提交绑定前必须重做后置校验。
         */
        if (cat == null ||
                !cat.isValid() ||
                cat.isDead() ||
                !player.isOnline() ||
                !store.hasCat(playerUUID) ||
                (summonToken >= 0 &&
                        !isCurrentOperation(
                                playerUUID,
                                summonToken
                        ))) {

            if (cat != null) {
                cat.remove();
            }

            callback.accept(
                    SummonResult.FAILED
            );

            return;
        }

        /*
         * 0.9.0更新：绑定尾部事务化（社区审查 R14-03）——
         * spawn 之后任一环节（绑定/花色/存档 UUID/位置）
         * 异常时移除实体 + 回滚存档指向，杜绝"实体已存在
         * 但回调 FAILED"的半成品与随之而来的重复猫。
         */
        UUID previousEntityUuid =
                store.getCatEntityUUID(
                        playerUUID
                );

        /*
         * 0.9.0更新：绑定用权威名字（审查 R11-1/2）。
         */
        String currentName =
                authoritativeName(
                        playerUUID,
                        name
                );

        Cat logicalCat;

        try {

            binding.updateCat(
                    cat,
                    player,
                    currentName
            );

            logicalCat =
                    binding.bindLogicalCat(
                            player,
                            cat,
                            currentName
                    );

            if (logicalCat == null) {

                cat.remove();

                callback.accept(
                        SummonResult.FAILED
                );

                return;
            }

            variantService.restoreVariant(
                    playerUUID,
                    cat,
                    logicalCat
            );

            store.setCatEntityUUID(
                    playerUUID,
                    cat.getUniqueId()
            );

            binding.saveCatLocation(
                    player,
                    cat
            );

        } catch (Exception exception) {

            cat.remove();

            entityIndex.removeEntity(
                    cat.getUniqueId()
            );

            store.setCatEntityUUID(
                    playerUUID,
                    previousEntityUuid
            );

            Cat cachedCat =
                    cache.getCat(
                            playerUUID
                    );

            if (cachedCat != null) {

                cachedCat.setEntityUuid(
                        previousEntityUuid
                );
            }

            logger.log(
                    Level.SEVERE,
                    "Failed to finish new cat bind for "
                            + player.getName()
                            + "; spawned entity removed.",
                    exception
            );

            callback.accept(
                    SummonResult.FAILED
            );

            return;
        }

        /*
         * 0.9.0更新：延迟 cleanup 绑定操作代际（社区审查
         * R14-03）——callback SPAWNED 与新召唤之间
         * 存在窗口：旧 restore 的下一 tick cleanup 若只
         * 认 (玩家, 旧猫)，会把新召唤的猫当重复删除。
         * 执行前必须三重验证：实体仍存活 + token 仍是
         * 当前操作 + 存档指向的仍是本实体。
         */
        UUID expectedEntityUuid =
                cat.getUniqueId();

        runtime
                .runTask(
                        () -> {

                            if (!cat.isDead() &&
                                    cat.isValid() &&
                                    (summonToken < 0 ||
                                            isCurrentOperation(
                                                    playerUUID,
                                                    summonToken
                                            )) &&
                                    expectedEntityUuid.equals(
                                            store.getCatEntityUUID(
                                                    playerUUID
                                            ))) {

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

        callback.accept(
                SummonResult.SPAWNED
        );
    }
}

