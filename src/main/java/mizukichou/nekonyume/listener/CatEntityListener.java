package mizukichou.nekonyume.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityIndex;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.cat.CatEquipItem;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.cat.EquipBonusAttribute;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.skill.CatBattleState;
import mizukichou.nekonyume.storage.CatStore;
import mizukichou.nekonyume.util.TargetGuard;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * 猫实体监听。
 *
 * <p>
 * 0.7.0：配置改走 ConfigManager 快照；文案改走 Lang。
 * </p>
 */
public class CatEntityListener implements Listener {

    /*
     * 恢复期内清理怪物目标的半径（格）。
     */
    private static final double TARGET_CLEAR_RADIUS = 24.0;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final CatStore store;
    private final CatCache cache;
    private final CatEntityService entityService;
    private final ConfigManager configManager;
    private final CatBattleState battleState;
    private final Lang lang;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    /*
     * 0.8.3：实体索引。
     */
    private final CatEntityIndex entityIndex;

    public CatEntityListener(
            JavaPlugin plugin,
            Logger logger,
            CatStore store,
            CatCache cache,
            CatEntityService entityService,
            ConfigManager configManager,
            CatBattleState battleState,
            NamespacedKey catKey,
            NamespacedKey ownerKey,
            Lang lang,
            CatEntityIndex entityIndex
    ) {

        this.plugin = plugin;
        this.logger = logger;
        this.store = store;
        this.cache = cache;
        this.entityService = entityService;
        this.configManager = configManager;
        this.battleState = battleState;
        this.catKey = catKey;
        this.ownerKey = ownerKey;
        this.lang = lang;
        this.entityIndex = entityIndex;
    }

    /*
     * ============================================================
     * 实体加入世界
     * ============================================================
     */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAddToWorld(
            EntityAddToWorldEvent event
    ) {

        Entity entity =
                event.getEntity();

        if (!(entity instanceof Cat cat)) {
            return;
        }

        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {

                            if (cat.isDead() ||
                                    !cat.isValid()) {
                                return;
                            }

                            if (!cat.getPersistentDataContainer()
                                    .has(
                                            catKey,
                                            PersistentDataType.BYTE
                                    )) {

                                return;
                            }

                            String ownerUUID =
                                    cat.getPersistentDataContainer()
                                            .get(
                                                    ownerKey,
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

                            if (!store.hasCat(playerUUID)) {

                                /*
                                 * 0.8.1 修复（R3）：主人数据已被删除的
                                 * 残留猫实体（如从已卸载区块重新加载）
                                 * 一律移除，杜绝“幽灵猫”重新被绑定。
                                 */
                                cat.remove();

                                return;
                            }

                            /*
                             * 0.8.3：登记实体索引（加速后续恢复/清理）。
                             */
                            entityIndex.put(
                                    cat.getUniqueId(),
                                    playerUUID
                            );

                            UUID currentUUID =
                                    store.getCatEntityUUID(
                                            playerUUID
                                    );

                            if (currentUUID == null) {

                                store.setCatEntityUUID(
                                        playerUUID,
                                        cat.getUniqueId()
                                );

                                mizukichou.nekonyume.cat.Cat logicalCat =
                                        cache.getCat(
                                                playerUUID
                                        );

                                if (logicalCat != null) {

                                    logicalCat.setEntityUuid(
                                            cat.getUniqueId()
                                    );
                                }

                                return;
                            }

                            if (currentUUID.equals(
                                    cat.getUniqueId()
                            )) {

                                return;
                            }

                            cat.remove();
                        }
                );
    }

    /*
     * 0.8.3：实体离开世界（死亡/移除/区块卸载）时同步撤销索引条目。
     * 索引是尽力而为的加速器：即使本事件未触发（极端情况），
     * 使用方也会在查找时校验实体有效性并自动清理陈旧条目。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoveFromWorld(
            EntityRemoveFromWorldEvent event
    ) {

        if (!(event.getEntity() instanceof Cat cat)) {
            return;
        }

        if (!cat.getPersistentDataContainer()
                .has(
                        catKey,
                        PersistentDataType.BYTE
                )) {

            return;
        }

        entityIndex.removeEntity(
                cat.getUniqueId()
        );
    }

    /*
     * ============================================================
     * 协同战斗（Issue #6）
     * ============================================================
     */

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onOwnerCombat(
            EntityDamageByEntityEvent event
    ) {

        if (!configManager.snapshot()
                .getBattle()
                .isEnabled()) {

            return;
        }

        if (event.getEntity() instanceof Player player) {

            Entity attacker =
                    unwrapProjectile(
                            event.getDamager()
                    );

            if (attacker instanceof Monster monster &&
                    !monster.isDead() &&
                    monster.isValid()) {

                registerAssistTarget(
                        player,
                        monster
                );
            }

            return;
        }

        if (event.getEntity() instanceof LivingEntity living &&
                !(living instanceof Player) &&
                event.getDamager() instanceof Player player &&
                !living.isDead() &&
                living.isValid() &&
                !isOurCat(living) &&
                !isOwnTamedPet(
                        player,
                        living
                )) {

            registerAssistTarget(
                    player,
                    living
            );
        }
    }

    /*
     * 0.8.1 修复（R2）：主人攻击自己驯养的宠物（狼/鹦鹉/驴等）时
     * 不登记协助目标——猫绝不能协助攻击并杀死主人的宠物。
     * 与溅射“只伤敌对生物，避免误伤你养的动物”的保护口径一致。
     */
    private boolean isOwnTamedPet(
            Player player,
            LivingEntity living
    ) {

        if (!(living instanceof Tameable tameable)) {
            return false;
        }

        UUID ownerId =
                tameable.getOwnerUniqueId();

        return ownerId != null &&
                ownerId.equals(
                        player.getUniqueId()
                );
    }

    private Entity unwrapProjectile(
            Entity damager
    ) {

        if (damager instanceof Projectile projectile) {

            Object shooter =
                    projectile.getShooter();

            if (shooter instanceof Entity shooterEntity) {
                return shooterEntity;
            }
        }

        return damager;
    }

    private void registerAssistTarget(
            Player player,
            LivingEntity target
    ) {

        if (!store.hasCat(
                player.getUniqueId()
        )) {

            return;
        }

        battleState.markAssistTarget(
                player.getUniqueId(),
                target.getUniqueId()
        );
    }

    private boolean isOurCat(
            Entity entity
    ) {

        if (!(entity instanceof Cat cat)) {
            return false;
        }

        return cat.getPersistentDataContainer()
                .has(
                        catKey,
                        PersistentDataType.BYTE
                );
    }

    /*
     * ============================================================
     * 恢复期目标屏蔽：怪物视猫为不存在
     * ============================================================
     */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTargetLiving(
            EntityTargetLivingEntityEvent event
    ) {

        if (!(event.getTarget()
                instanceof Cat cat)) {

            return;
        }

        if (!isOurCat(cat)) {
            return;
        }

        if (battleState.isRecovering(
                cat.getUniqueId()
        )) {

            event.setCancelled(
                    true
            );
        }
    }

    /*
     * ============================================================
     * 猫受伤与致死保护
     * ============================================================
     */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCatDamage(
            EntityDamageEvent event
    ) {

        if (!(event.getEntity()
                instanceof Cat cat)) {

            return;
        }

        if (!cat.getPersistentDataContainer()
                .has(
                        catKey,
                        PersistentDataType.BYTE
                )) {

            return;
        }

        /*
         * 互操作：若更早的监听器（领地/保护插件等）已取消事件，
         * 猫根本不会受伤，致死保护协议无需介入。
         */
        if (event.isCancelled()) {
            return;
        }

        /*
         * 异常伤害治理：他插件可能写入 NaN 伤害，
         * NaN 会击穿下方的致死判定（NaN <= 0 为 false），
         * 使猫带着 NaN 血量继续存在。直接取消该次伤害。
         */
        double finalDamage =
                event.getFinalDamage();

        if (!Double.isFinite(
                finalDamage
        )) {

            event.setCancelled(
                    true
            );

            return;
        }

        if (!configManager.snapshot()
                .getBattle()
                .isEnabled()) {

            event.setCancelled(
                    true
            );

            return;
        }

        mizukichou.nekonyume.cat.Cat logicalCat =
                resolveLogicalCat(
                        cat
                );

        double reduction = 0.0;

        if (logicalCat != null) {

            if (logicalCat.hasSkill(
                    CatSkill.LIGHT_FUR
            )) {

                reduction += 0.10;
            }

            if (logicalCat.hasSkill(
                    CatSkill.IRON_WALL
            )) {

                reduction += 0.25;
            }

            /*
             * 装备（0.8.0）：项圈的受伤减免与技能相乘，
             * 避免与铁壁/轻毛相加后溢出。
             */
            CatEquipItem equip =
                    logicalCat.getEquippedItem();

            if (equip != null &&
                    equip.getDamageReductionPercent() > 0) {

                double equipFactor =
                        1.0
                                - equip.getDamageReductionPercent()
                                / 100.0;

                reduction =
                        1.0
                                - (1.0 - reduction)
                                * equipFactor;
            }

            /*
             * 附加属性（0.8.0）：不朽的受伤减免同样相乘。
             */
            EquipBonusAttribute equipBonus =
                    logicalCat.getEquippedBonus();

            if (equipBonus != null &&
                    equipBonus.getDamageReductionPercent() > 0) {

                double bonusFactor =
                        1.0
                                - equipBonus.getDamageReductionPercent()
                                / 100.0;

                reduction =
                        1.0
                                - (1.0 - reduction)
                                * bonusFactor;
            }
        }

        if (reduction > 0.0) {

            event.setDamage(
                    event.getDamage()
                            * (1.0 - reduction)
            );
        }

        double finalHealth =
                cat.getHealth()
                        - event.getFinalDamage();

        if (finalHealth <= 0.0) {

            event.setCancelled(
                    true
            );

            handleDeathProtection(
                    cat,
                    logicalCat
            );
        }
    }

    private mizukichou.nekonyume.cat.Cat resolveLogicalCat(
            Cat cat
    ) {

        mizukichou.nekonyume.cat.Cat logicalCat =
                cache.getCatByEntity(
                        cat.getUniqueId()
                );

        if (logicalCat != null) {
            return logicalCat;
        }

        String ownerUUID =
                cat.getPersistentDataContainer()
                        .get(
                                ownerKey,
                                PersistentDataType.STRING
                        );

        if (ownerUUID == null) {
            return null;
        }

        try {

            UUID playerUUID =
                    UUID.fromString(
                            ownerUUID
                    );

            return cache.loadCat(
                    playerUUID
            );

        } catch (IllegalArgumentException e) {

            return null;
        }
    }

    private void handleDeathProtection(
            Cat cat,
            mizukichou.nekonyume.cat.Cat logicalCat
    ) {

        boolean hasNineLives =
                logicalCat != null &&
                        logicalCat.hasSkill(
                                CatSkill.NINE_LIVES
                        );

        boolean hasEternity =
                logicalCat != null &&
                        logicalCat.hasSkill(
                                CatSkill.ETERNITY
                        );

        ConfigSnapshot.Battle battleConfig =
                configManager.snapshot()
                        .getBattle();

        /*
         * 永恒：满血重生。
         */
        if (hasEternity) {

            long cooldownMs =
                    battleConfig.getEternityRebirthSeconds()
                            * 1000L;

            if (battleState.tryRebirth(
                    cat.getUniqueId(),
                    cooldownMs
            )) {

                cat.setHealth(
                        cat.getMaxHealth()
                );

                battleState.clearRecovery(
                        cat.getUniqueId()
                );

                cat.setAI(true);

                cat.removePotionEffect(
                        PotionEffectType.INVISIBILITY
                );

                cat.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.STRENGTH,
                                5 * 20,
                                1
                        )
                );

                cat.getWorld()
                        .spawnParticle(
                                Particle.HEART,
                                cat.getLocation()
                                        .add(0, 1, 0),
                                40,
                                0.6,
                                0.6,
                                0.6,
                                0.05
                        );

                return;
            }
        }

        /*
         * 已经处于恢复期：只保底 1 血，绝不重置倒计时。
         */
        if (battleState.isRecovering(
                cat.getUniqueId()
        )) {

            if (cat.getHealth() < 1.0) {

                cat.setHealth(
                        1.0
                );
            }

            return;
        }

        /*
         * 首次进入恢复期。
         */
        cat.setHealth(
                1.0
        );

        long recoveryMillis =
                battleConfig.getRecoverySeconds()
                        * 1000L;

        /*
         * 九命：恢复期固定 20 秒（0.6.2）。
         */
        if (hasNineLives) {

            recoveryMillis = 20_000L;
        }

        /*
         * 羁绊纪元（0.8.0）：战败扣逻辑健康（重生分支不扣）。
         * 健康下跌经既有心情规则（health <= 30 → -30）联动战力。
         */
        if (logicalCat != null) {

            int defeatHealthLoss =
                    configManager.snapshot()
                            .getCare()
                            .getDefeatHealthLoss();

            if (defeatHealthLoss > 0) {

                logicalCat.removeHealth(
                        defeatHealthLoss
                );

                store.setCatHealth(
                        logicalCat.getOwnerUuid(),
                        logicalCat.getHealth()
                );
            }
        }

        battleState.markRecovering(
                cat.getUniqueId(),
                recoveryMillis
        );

        cat.setAI(
                false
        );

        cat.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.INVISIBILITY,
                        (int) (recoveryMillis / 50L),
                        0,
                        false,
                        false,
                        false
                )
        );

        TargetGuard.clearTargetsOn(
                cat,
                TARGET_CLEAR_RADIUS
        );

        int recoverySeconds =
                (int) Math.ceil(
                        recoveryMillis / 1000.0
                );

        entityService.refreshCustomName(
                cat,
                logicalCat
        );

        if (logicalCat != null) {

            Player owner =
                    Bukkit.getPlayer(
                            logicalCat.getOwnerUuid()
                    );

            if (owner != null &&
                    owner.isOnline()) {

                owner.sendMessage(
                        lang.forPlayer(owner).message(
                                "battle.recovering",
                                logicalCat.getName(),
                                String.valueOf(
                                        recoverySeconds
                                )
                        )
                );
            }
        }

        cat.getWorld()
                .spawnParticle(
                        Particle.END_ROD,
                        cat.getLocation()
                                .add(0, 1, 0),
                        30,
                        0.4,
                        0.4,
                        0.4,
                        0.02
                );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(
            EntityDeathEvent event
    ) {

        if (!(event.getEntity() instanceof Cat cat)) {
            return;
        }

        handleEntityLoss(
                cat
        );
    }

    @EventHandler
    public void onWorldLoad(
            WorldLoadEvent event
    ) {

        entityService.retryPendingWorldRestores(
                event.getWorld()
        );
    }

    /*
     * 0.8.4 R24（审查复核）：
     * 对称清理——世界卸载时作废该世界的待恢复记录，
     * 防止动态世界场景下的无界累积。
     */
    @EventHandler
    public void onWorldUnload(
            WorldUnloadEvent event
    ) {

        entityService.forgetPendingWorldRestores(
                event.getWorld()
        );
    }

    private void handleEntityLoss(
            Cat cat
    ) {

        if (cat == null) {
            return;
        }

        String ownerUUIDString =
                cat.getPersistentDataContainer()
                        .get(
                                ownerKey,
                                PersistentDataType.STRING
                        );

        if (ownerUUIDString == null) {
            return;
        }

        UUID playerUUID;

        try {

            playerUUID =
                    UUID.fromString(
                            ownerUUIDString
                    );

        } catch (IllegalArgumentException ignored) {

            return;
        }

        UUID currentUUID =
                store.getCatEntityUUID(
                        playerUUID
                );

        if (currentUUID == null ||
                !currentUUID.equals(
                        cat.getUniqueId()
                )) {

            return;
        }

        entityService.clearEntityBinding(
                playerUUID
        );

        logger.info(
                "Cat entity "
                        + cat.getUniqueId()
                        + " lost for player "
                        + playerUUID
                        + ". Binding cleared, logical cat kept."
        );
    }
}
