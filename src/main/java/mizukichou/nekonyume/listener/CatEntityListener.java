package mizukichou.nekonyume.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.config.PluginConfig;
import mizukichou.nekonyume.skill.CatBattleState;
import mizukichou.nekonyume.storage.CatStore;
import mizukichou.nekonyume.util.TargetGuard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.logging.Logger;

public class CatEntityListener implements Listener {

    /*
     * 恢复期内清理怪物目标的半径（格）。
     * 覆盖常规索敌与监守者愤怒系统的生效范围。
     */
    private static final double TARGET_CLEAR_RADIUS = 24.0;

    /*
     * plugin 仅用于调度器（延迟 PDC 检查）。
     */
    private final JavaPlugin plugin;
    private final Logger logger;
    private final CatStore store;
    private final CatCache cache;
    private final CatEntityService entityService;
    private final PluginConfig config;
    private final CatBattleState battleState;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    private final MiniMessage mm =
            MiniMessage.miniMessage();

    public CatEntityListener(
            JavaPlugin plugin,
            Logger logger,
            CatStore store,
            CatCache cache,
            CatEntityService entityService,
            PluginConfig config,
            CatBattleState battleState,
            NamespacedKey catKey,
            NamespacedKey ownerKey
    ) {

        this.plugin = plugin;
        this.logger = logger;
        this.store = store;
        this.cache = cache;
        this.entityService = entityService;
        this.config = config;
        this.battleState = battleState;
        this.catKey = catKey;
        this.ownerKey = ownerKey;
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

        /*
         * 注意：
         * 新生成的猫在 spawnEntity 时触发本事件，
         * 此时 updateCat() 还没有写入 PDC。
         *
         * 所以 PDC 检查必须放在延迟任务里，
         * 而不是事件触发的那一刻。
         * 否则新生成的正式实体永远无法通过检查。
         */
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {

                            if (cat.isDead() ||
                                    !cat.isValid()) {
                                return;
                            }

                            /*
                             * 这里只处理我们的猫
                             */
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

                            /*
                             * 玩家已经没有宠物数据
                             */
                            if (!store.hasCat(playerUUID)) {
                                return;
                            }

                            UUID currentUUID =
                                    store.getCatEntityUUID(
                                            playerUUID
                                    );

                            /*
                             * 当前没有正式实体。
                             * 让这个实体成为正式实体。
                             */
                            if (currentUUID == null) {

                                store.setCatEntityUUID(
                                        playerUUID,
                                        cat.getUniqueId()
                                );

                                /*
                                 * 同步运行时缓存。
                                 */
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

                            /*
                             * UUID 相同：
                             * 正常实体。
                             */
                            if (currentUUID.equals(
                                    cat.getUniqueId()
                            )) {

                                return;
                            }

                            /*
                             * UUID 不同：
                             * 这是旧实体 / 重复实体。
                             */
                            cat.remove();
                        }
                );
    }

    /*
     * ============================================================
     * 协同战斗（Issue #6）
     * ============================================================
     *
     * 主人攻击任意活物 → 猫协同攻击该目标；
     * 主人被怪物攻击 → 猫反击该目标。
     *
     * 目标写入 CatBattleState，
     * 由 CatBattleTask 在下一个战斗周期接管。
     */

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onOwnerCombat(
            EntityDamageByEntityEvent event
    ) {

        if (!config.isBattleEnabled()) {
            return;
        }

        /*
         * 1. 主人被怪物攻击 → 反击。
         */
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

        /*
         * 2. 主人攻击任意活物 → 协同。
         *
         * 和平生物也算：主人出手了，猫就帮忙。
         * 排除：玩家（PVP 不介入）与本插件的猫。
         */
        if (event.getEntity() instanceof LivingEntity living &&
                !(living instanceof Player) &&
                event.getDamager() instanceof Player player &&
                !living.isDead() &&
                living.isValid() &&
                !isOurCat(living)) {

            registerAssistTarget(
                    player,
                    living
            );
        }
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

        /*
         * 只有拥有猫咪数据的玩家才需要记录。
         */
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

    /*
     * 目标是否为本插件的猫实体。
     */
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
     *
     * 拦截标准索敌路径：
     * 任何怪物尝试锁定一只"恢复期中的本插件猫"时，
     * 直接取消目标获取。
     *
     * 非标准路径（如监守者愤怒系统、受伤前已锁定）
     * 由 CatBattleTask 的周期性扫荡 + TargetGuard 兜底。
     */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTargetLiving(
            EntityTargetLivingEntityEvent event
    ) {

        if (!(event.getTarget()
                instanceof Cat cat)) {

            return;
        }

        /*
         * 只保护我们自己的、处于恢复期的猫；
         * 其他猫完全不受影响（保留原版索敌行为）。
         */
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
     *
     * 战斗开启时：
     * - 受伤减免（轻毛 / 铁壁）
     * - 致死拦截：进入 120 秒受伤恢复期
     *   （1 血保底、AI 冻结、隐身、
     *     怪物视猫为不存在、悬浮字倒计时、
     *     结束满血复活；恢复期内重复受伤不重置倒计时）
     * - 永恒：满血重生（冷却内）
     *
     * 战斗关闭时：
     * 猫保持无敌（旧行为）。
     */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCatDamage(
            EntityDamageEvent event
    ) {

        if (!(event.getEntity()
                instanceof Cat cat)) {

            return;
        }

        /*
         * 只处理我们的猫。
         */
        if (!cat.getPersistentDataContainer()
                .has(
                        catKey,
                        PersistentDataType.BYTE
                )) {

            return;
        }

        /*
         * 战斗关闭：猫恢复无敌。
         */
        if (!config.isBattleEnabled()) {

            event.setCancelled(
                    true
            );

            return;
        }

        mizukichou.nekonyume.cat.Cat logicalCat =
                resolveLogicalCat(
                        cat
                );

        /*
         * 受伤减免。
         */
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
        }

        if (reduction > 0.0) {

            event.setDamage(
                    event.getDamage()
                            * (1.0 - reduction)
            );
        }

        /*
         * 致死保护。
         */
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

    /*
     * 解析猫对应的逻辑 Cat。
     *
     * 主人离线时缓存中可能没有逻辑猫，
     * 这里按 PDC 的 owner UUID 加载一次，
     * 保证减伤与致死保护技能在离线场景同样生效。
     */

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

        /*
         * 永恒：满血重生。
         */
        if (hasEternity) {

            long cooldownMs =
                    config.getBattleEternityRebirthSeconds()
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

                /*
                 * 恢复 AI 与可见性
                 * （防止在恢复期冻结状态下触发永恒）。
                 */
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
         * 已经处于恢复期：
         * 只保底 1 血，绝不重置恢复倒计时。
         * （否则高伤怪物持续攻击会让猫永远无法复活——
         *   这正是打坚守者只打一下就不动的根因。）
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
         * 首次进入恢复期：
         * 1 血保底 + 倒计时 + 悬浮字 + 主人提示。
         */
        cat.setHealth(
                1.0
        );

        long recoveryMillis =
                config.getBattleRecoverySeconds()
                        * 1000L;

        /*
         * 九命：恢复期缩短为四分之一。
         */
        if (hasNineLives) {

            recoveryMillis /= 4;
        }

        battleState.markRecovering(
                cat.getUniqueId(),
                recoveryMillis
        );

        /*
         * 恢复期"幽灵化"：
         * - AI 冻结：不移动、不发声，
         *   不给监守者任何振动信号；
         * - 隐身（无粒子）：视觉/感知类索敌失效。
         * 配合 TargetGuard 的清目标 + 清愤怒，
         * 怪物才会真正当作猫不存在。
         */
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

        /*
         * 立刻清空当前以它为目标的怪物：
         * 让怪物即刻"当作猫不存在"。
         */
        TargetGuard.clearTargetsOn(
                cat,
                TARGET_CLEAR_RADIUS
        );

        int recoverySeconds =
                (int) Math.ceil(
                        recoveryMillis / 1000.0
                );

        /*
         * 悬浮字：立即刷新头顶名称。
         */
        entityService.refreshCustomName(
                cat,
                logicalCat
        );

        /*
         * 主人提示。
         */
        if (logicalCat != null) {

            Player owner =
                    Bukkit.getPlayer(
                            logicalCat.getOwnerUuid()
                    );

            if (owner != null &&
                    owner.isOnline()) {

                owner.sendMessage(
                        mm.deserialize(
                                "<red>🐱 </red>"
                        ).append(
                                Component.text(
                                        logicalCat.getName()
                                )
                        ).append(
                                mm.deserialize(
                                        "<white> 受伤了，<red>"
                                                + recoverySeconds
                                                + "</red> 秒内无法继续活动…</white>"
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

    /*
     * ============================================================
     * 猫死亡
     * ============================================================
     *
     * 致死保护之外的死亡路径（例如 /kill）：
     * 只清绑定，
     * 逻辑猫与全部存档数据保留。
     * 玩家执行 /nekoyume summon 即可恢复。
     */

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

    /*
     * ============================================================
     * 世界加载
     * ============================================================
     *
     * 玩家登录时，猫咪所在世界可能尚未加载。
     * 世界加载完成后，
     * 重试等待中的实体恢复。
     */

    @EventHandler
    public void onWorldLoad(
            WorldLoadEvent event
    ) {

        entityService.retryPendingWorldRestores(
                event.getWorld()
        );
    }

    /*
     * ============================================================
     * 工具
     * ============================================================
     */

    /*
     * 防御性清绑定。
     *
     * 只有被移除 / 死亡的实体
     * 正是当前绑定实体时才清。
     *
     * 同时该方法在死亡 + 移除连续触发时
     * 是幂等的：第一次已清空，
     * 第二次 currentUUID 为 null 直接返回。
     */
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

        /*
         * 确认这就是当前绑定的实体。
         */
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

        /*
         * 只清绑定。
         * 逻辑猫与全部状态保留。
         */
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
