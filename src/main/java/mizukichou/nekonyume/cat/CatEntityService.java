package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 猫咪 Bukkit 实体服务门面。
 *
 * <p>
 * God Object 拆分（0.7.3）：
 * 本类只负责编排与对外 API，实现已按职责下沉到：
 * </p>
 *
 * <ul>
 *   <li>{@link CatEntityBinding}：PDC 绑定 / 名称 / 行为模式 / 状态捕获；</li>
 *   <li>{@link CatEntityRestorer}：登录恢复 / 主动召唤 / 待恢复队列。</li>
 * </ul>
 *
 * <p>
 * 门面自身保留两个跨组件编排操作：
 * removePlayerCat（删除：实体 → 缓存 → 队列 → 数据）与
 * spawnCat（召唤：重入防护 + 回调包装）。
 * </p>
 *
 * <p>
 * 单线程模型：业务方法全部在主线程调用；
 * 异步区块加载由 {@link CatEntityRestorer} 负责回到主线程。
 * </p>
 */
public class CatEntityService {

    private final Logger logger;
    private final CatStore store;
    private final CatCache cache;
    private final Lang lang;

    private final CatEntityBinding binding;
    private final CatEntityRestorer restorer;

    /*
     * 防止同一个玩家同时执行多个 summon。
     */
    private final Set<UUID> summoning =
            ConcurrentHashMap.newKeySet();

    public CatEntityService(
            Logger logger,
            CatStore store,
            CatCache cache,
            Lang lang,
            CatEntityBinding binding,
            CatEntityRestorer restorer
    ) {

        this.logger = logger;
        this.store = store;
        this.cache = cache;
        this.lang = lang;
        this.binding = binding;
        this.restorer = restorer;
    }

    /*
     * ============================================================
     * PDC Keys（CatEntityBinding）
     * ============================================================
     */

    public NamespacedKey getCatKey() {
        return binding.getCatKey();
    }

    public NamespacedKey getOwnerKey() {
        return binding.getOwnerKey();
    }

    /*
     * ============================================================
     * 状态捕获 / 名称 / 行为（CatEntityBinding）
     * ============================================================
     */

    public void captureEntityState(Cat logicalCat) {
        binding.captureEntityState(logicalCat);
    }

    public void clearEntityBinding(UUID playerUUID) {
        binding.clearEntityBinding(playerUUID);
    }

    public void updateCatName(
            Player player,
            String name
    ) {

        binding.updateCatName(player, name);
    }

    public void setCatBehaviorMode(
            Player player,
            CatBehaviorMode mode
    ) {

        binding.setCatBehaviorMode(player, mode);
    }

    public void refreshCustomName(
            org.bukkit.entity.Cat entity,
            Cat logicalCat
    ) {

        binding.refreshCustomName(entity, logicalCat);
    }

    /*
     * 0.8.0：装备变更后刷新战斗相关状态（最大生命）。
     *
     * 注意：updateCat 会用 name 刷新实体头顶名，
     * 因此必须传逻辑猫的名字（而非玩家名）。
     */
    public void refreshEquipStats(
            Player player,
            Cat logicalCat,
            org.bukkit.entity.Cat entity
    ) {

        if (player == null ||
                logicalCat == null ||
                entity == null) {

            return;
        }

        binding.updateCat(
                entity,
                player,
                logicalCat.getName()
        );
    }

    /*
     * ============================================================
     * 恢复 / 召唤流水线（CatEntityRestorer）
     * ============================================================
     */

    public void restoreCatEntity(Player player) {
        restorer.restoreCatEntity(player);
    }

    public void retryPendingWorldRestores(World world) {
        restorer.retryPendingWorldRestores(world);
    }

    public void clearPendingRestore(UUID playerUUID) {
        restorer.clearPendingRestore(playerUUID);
    }

    /*
     * ============================================================
     * 删除玩家的猫咪（管理操作，不可逆）
     * ============================================================
     *
     * 仅在 /nekoyumeadmin cat remove confirm 确认后调用。
     * 顺序：实体 → 运行时缓存 → 待恢复队列 → 持久化数据。
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
        restorer.clearPendingRestore(playerUUID);

        /*
         * 4. 删除持久化数据。
         */
        return store.removeCat(playerUUID);
    }

    /*
     * 释放召唤标记（玩家退出时由 PlayerQuitListener 调用）。
     *
     * 0.8.0 P0-2：同时递增代际，使所有在途异步回调失效——
     * 退出后旧流水线即使回来也会直接丢弃结果，
     * 不再与下一次登录的新召唤竞争。
     */
    public void clearSummoning(
            UUID playerUUID
    ) {

        if (playerUUID != null) {

            restorer.invalidateSummons(
                    playerUUID
            );

            summoning.remove(
                    playerUUID
            );
        }
    }

    /*
     * ============================================================
     * 主动召唤（重入防护 + 流水线编排）
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
                    lang.forPlayer(player).message(
                            "entity.summoning"
                    )
            );

            return;
        }

        /*
         * 0.8.0 P0-2：领取本次召唤的代际 token。
         * 异步回调凭 token 判断自己是否仍然有效。
         */
        long summonToken =
                restorer.beginSummon(
                        playerUUID
                );

        /*
         * 包装回调：无论成功失败，只要回调被执行，
         * 就保证释放 summoning 标记。
         *
         * 仅当自身代际仍为当前代际时才释放——
         * 旧流水线的迟到回调不得解除新召唤的标记。
         */
        Consumer<Boolean> wrappedCallback =
                result -> {

                    try {

                        callback.accept(result);

                    } finally {

                        if (restorer.isCurrentSummon(
                                playerUUID,
                                summonToken
                        )) {

                            summoning.remove(
                                    playerUUID
                            );
                        }
                    }
                };

        try {

            restorer.findCat(
                    player,
                    name,
                    wrappedCallback,
                    summonToken
            );

        } catch (Exception exception) {

            restorer.invalidateSummons(
                    playerUUID
            );

            summoning.remove(playerUUID);

            logger.log(
                    Level.SEVERE,
                    "Failed to summon cat for "
                            + player.getName(),
                    exception
            );

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "entity.summon-error"
                    )
            );
        }
    }
}
