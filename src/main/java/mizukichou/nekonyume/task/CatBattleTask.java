package mizukichou.nekonyume.task;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.cat.CareMath;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.cat.CatEquipItem;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.cat.EquipBonusAttribute;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.skill.CatBattleState;
import mizukichou.nekonyume.util.SafeTeleport;
import mizukichou.nekonyume.util.TargetGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 猫咪战斗任务。
 *
 * <p>
 * 每 10 tick 检查一次：
 * 跟随模式下自动攻击主人附近的敌对生物。
 * 近战为主；拥有「灵弹」技能时改为远程魔法弹。
 * </p>
 *
 * <p>
 * 受伤恢复期：
 * - 恢复期内禁止攻击（含扑击/灵弹）；
 * - AI 冻结 + 隐身（"幽灵化"，怪物视猫为不存在）；
 * - 周期性清空怪物的目标锁定并清零监守者愤怒；
 * - 每 tick 刷新头顶倒计时悬浮字；
 * - 倒计时结束恢复 AI 与可见性并满血复活；
 * - 恢复期外，血量不满时每 4 秒缓慢回复 1 点。
 * </p>
 *
 * <p>
 * 目标规则：
 * - 自动索敌：只打敌对生物（Monster）；
 * - 主人出手：任意活物都可成为协同目标（除玩家与本插件猫）；
 * - 主人被打：仅敌对生物触发反击。
 * </p>
 */
public class CatBattleTask implements Runnable {

    /*
     * 近战范围（格）。
     */
    private static final double MELEE_RANGE = 2.5;

    /*
     * 单次扑击最大距离（格）。
     */
    private static final double MAX_POUNCE_DISTANCE = 6.0;

    /*
     * 扑击最小间隔（毫秒）。
     */
    private static final long POUNCE_INTERVAL_MS =
            1000L;

    /*
     * 协助目标的判定半径 = 仇恨半径 × 倍率。
     */
    private static final double ASSIST_RADIUS_MULTIPLIER = 1.5;

    /*
     * 远程（灵弹）射程（格）。
     */
    private static final double RANGED_RANGE = 12.0;

    /*
     * 远程攻击间隔（毫秒）。
     */
    private static final long RANGED_ATTACK_INTERVAL_MS =
            3000L;

    /*
     * 恢复期内清理怪物目标的半径（格）。
     */
    private static final double TARGET_CLEAR_RADIUS = 24.0;

    /*
     * 恢复期隐身效果的续期时长（tick）。
     * 每 10 tick 续一次，保证持续隐身。
     */
    private static final int INVISIBILITY_REFRESH_TICKS = 40;

    /*
     * 近战攻击间隔下限（tick）：
     * 毛线球的间隔缩减不会把攻速压到低于 4 次/秒。
     */
    private static final long MIN_ATTACK_INTERVAL_TICKS = 5L;

    private final Logger logger;
    private final ConfigManager configManager;
    private final CatCache cache;
    private final CatBattleState battleState;
    private final CatEntityService entityService;

    private final Lang lang;

    /*
     * 任务内统一随机源（不用 Math.random）。
     */
    private final Random random =
            new Random();

    public CatBattleTask(
            Logger logger,
            ConfigManager configManager,
            CatCache cache,
            CatBattleState battleState,
            CatEntityService entityService,
            Lang lang
    ) {

        this.logger = logger;
        this.configManager = configManager;
        this.cache = cache;
        this.battleState = battleState;
        this.entityService = entityService;
        this.lang = lang;
    }

    @Override
    public void run() {

        if (!configManager.snapshot()
                .getBattle()
                .isEnabled()) {

            return;
        }

        /*
         * 状态清理：
         * 移除已不存在实体/主人的残留战斗状态，
         * 防止长跑服务器上 CatBattleState 无限膨胀。
         */
        List<UUID> activeEntities = new ArrayList<>();
        List<UUID> activeOwners = new ArrayList<>();

        for (Cat cat : cache.getCats()) {

            activeOwners.add(cat.getOwnerUuid());

            if (cat.getEntityUuid() != null) {

                activeEntities.add(cat.getEntityUuid());
            }
        }

        battleState.retainOnly(
                activeEntities,
                activeOwners
        );

        for (Cat logicalCat :
                cache.getCats()) {

            /*
             * 单猫异常隔离：
             * 一只猫的数据/实体出现任何意外，
             * 都只跳过这一只，绝不瘫痪整个任务。
             */
            try {

                processCat(
                        logicalCat
                );

            } catch (Exception exception) {

                logger.warning(
                        "Battle tick failed for cat "
                                + logicalCat.getId()
                                + ": "
                                + exception.getMessage()
                );
            }
        }
    }

    /*
     * ============================================================
     * 单只猫的战斗结算
     * ============================================================
     */

    private void processCat(
            Cat logicalCat
    ) {

        UUID entityUuid =
                logicalCat.getEntityUuid();

        if (entityUuid == null) {
            return;
        }

        Entity entity =
                Bukkit.getEntity(
                        entityUuid
                );

        if (!(entity instanceof org.bukkit.entity.Cat cat) ||
                cat.isDead() ||
                !cat.isValid()) {

            return;
        }

        /*
         * ========================================================
         * 受伤恢复期结算（任何模式下都执行）
         * ========================================================
         */
        Long remaining =
                battleState.getRecoveryRemainingMillis(
                        entityUuid
                );

        if (remaining != null) {

            if (remaining <= 0) {

                /*
                 * 倒计时结束：恢复 AI 与可见性，满血复活。
                 */
                battleState.clearRecovery(
                        entityUuid
                );

                cat.setHealth(
                        cat.getMaxHealth()
                );

                cat.setAI(
                        true
                );

                cat.removePotionEffect(
                        PotionEffectType.INVISIBILITY
                );

                cat.getWorld()
                        .spawnParticle(
                                Particle.HEART,
                                cat.getLocation()
                                        .add(0, 1, 0),
                                30,
                                0.5,
                                0.5,
                                0.5,
                                0.05
                        );

                /*
                 * 立即恢复正常头顶名称。
                 */
                entityService.refreshCustomName(
                        cat,
                        logicalCat
                );

            } else {

                /*
                 * 恢复期内：
                 * 1. 持续"幽灵化"（幂等，每 tick 续期）；
                 * 2. 禁止一切战斗行为；
                 * 3. 没有缓慢回血（血量固定在 1）；
                 * 4. 半径 24 格的怪物目标清扫与倒计时刷新
                 *    降频到每 20 tick（1 秒）一次，
                 *    避免 120 秒恢复期产生 240 次全半径扫描。
                 */
                cat.setAI(
                        false
                );

                cat.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.INVISIBILITY,
                                INVISIBILITY_REFRESH_TICKS,
                                0,
                                false,
                                false,
                                false
                        )
                );

                long remainingSeconds =
                        (long) Math.ceil(
                                remaining / 1000.0
                        );

                if (battleState.shouldSweepTargets(
                        entityUuid,
                        remainingSeconds
                )) {

                    TargetGuard.clearTargetsOn(
                            cat,
                            TARGET_CLEAR_RADIUS
                    );

                    entityService.refreshCustomName(
                            cat,
                            logicalCat
                    );
                }

                return;
            }

        } else {

            /*
             * 恢复期外：血量不满时缓慢回血
             * （每 4 秒 1 点，围巾至极翻倍）。
             */
            tickRegen(
                    logicalCat,
                    cat,
                    entityUuid
            );
        }

        /*
         * 只在跟随模式战斗。
         */
        if (logicalCat.getBehaviorMode()
                != CatBehaviorMode.FOLLOW) {

            battleState.setChasing(
                    entityUuid,
                    false
            );

            return;
        }

        /*
         * 羁绊纪元（0.8.0）：
         * 饥饿到门槛以下拒绝索敌（守在主人身边不上前）。
         * 阈值 -1 = 关闭。
         * 状态变化只提示一次。
         */
        int starvingThreshold =
                configManager.snapshot()
                        .getCare()
                        .getStarvingFightThreshold();

        if (starvingThreshold >= 0 &&
                logicalCat.getHunger() <= starvingThreshold) {

            battleState.setChasing(
                    entityUuid,
                    false
            );

            if (battleState.markStarvingAlerted(
                    entityUuid
            )) {

                Player starvingOwner =
                        Bukkit.getPlayer(
                                logicalCat.getOwnerUuid()
                        );

                if (starvingOwner != null &&
                        starvingOwner.isOnline()) {

                    starvingOwner.sendMessage(
                            lang.forPlayer(starvingOwner)
                                    .message(
                                            "battle.starving",
                                            logicalCat.getName()
                                    )
                    );
                }
            }

            return;
        }

        if (logicalCat.getHunger() > starvingThreshold) {

            battleState.clearStarvingAlerted(
                    entityUuid
            );
        }

        Player owner =
                Bukkit.getPlayer(
                        logicalCat.getOwnerUuid()
                );

        if (owner == null ||
                !owner.isOnline()) {

            return;
        }

        World world =
                cat.getLocation()
                        .getWorld();

        if (world == null ||
                !world.equals(
                        owner.getWorld()
                )) {

            return;
        }

        /*
         * 主人距离限制：
         * 猫离主人太远不战斗，
         * 避免在野外乱引怪。
         */
        int aggroRadius =
                configManager.snapshot()
                        .getBattle()
                        .getAggroRadius();

        if (cat.getLocation()
                .distanceSquared(
                        owner.getLocation()
                )
                > (double) aggroRadius
                * aggroRadius) {

            battleState.setChasing(
                    entityUuid,
                    false
            );

            return;
        }

        /*
         * 找目标：
         * 优先协助目标（主人攻击 / 被攻击的怪物），
         * 其次猫附近的最近敌对生物。
         */
        LivingEntity target =
                findTarget(
                        logicalCat,
                        cat,
                        aggroRadius
                );

        if (target == null) {

            battleState.setChasing(
                    entityUuid,
                    false
            );

            return;
        }

        /*
         * 有目标：进入追击状态。
         */
        battleState.setChasing(
                entityUuid,
                true
        );

        /*
         * 远程（灵弹）。
         */
        if (logicalCat.hasSkill(
                CatSkill.SPIRIT_SHOT
        )) {

            handleRangedAttack(
                    logicalCat,
                    cat,
                    owner,
                    target,
                    entityUuid
            );

            return;
        }

        /*
         * 近战：目标太远 → 扑击靠近（限速 1 秒一跳），
         * 进入范围后正常攻击。
         */
        double distSq =
                cat.getLocation()
                        .distanceSquared(
                                target.getLocation()
                        );

        if (distSq >
                MELEE_RANGE * MELEE_RANGE) {

            if (battleState.canPounce(
                    entityUuid,
                    POUNCE_INTERVAL_MS
            )) {

                battleState.markPounce(
                        entityUuid
                );

                pounceTowards(
                        cat,
                        target
                );
            }

            return;
        }

        /*
         * 隔墙不打：
         * 没有视线就不攻击，
         * 防止隔墙输出伤害。
         */
        if (!cat.hasLineOfSight(target)) {
            return;
        }

        long intervalTicks =
                configManager.snapshot()
                        .getBattle()
                        .getAttackIntervalTicks();

        /*
         * 灵步：攻击间隔 -20%。
         */
        if (logicalCat.hasSkill(
                CatSkill.LIGHT_STEP
        )) {

            intervalTicks =
                    (long) (intervalTicks * 0.8);
        }

        /*
         * 装备（0.8.0）：毛线球的攻击间隔缩减（下限保护）；
         * 附加属性「迅影」同样计入。
         */
        CatEquipItem intervalEquip =
                logicalCat.getEquippedItem();

        EquipBonusAttribute intervalBonus =
                logicalCat.getEquippedBonus();

        int intervalReduction =
                (intervalEquip == null
                        ? 0
                        : intervalEquip.getAttackIntervalReductionTicks())
                        + (intervalBonus == null
                        ? 0
                        : intervalBonus.getAttackIntervalReductionTicks());

        if (intervalReduction > 0) {

            intervalTicks =
                    Math.max(
                            MIN_ATTACK_INTERVAL_TICKS,
                            intervalTicks
                                    - intervalReduction
                    );
        }

        long intervalMs =
                intervalTicks * 50L;

        if (!battleState.canAttack(
                entityUuid,
                intervalMs
        )) {

            return;
        }

        battleState.markAttack(
                entityUuid
        );

        /*
         * 伤害计算。
         */
        int damage =
                computeDamage(
                        logicalCat,
                        cat
                );

        /*
         * 影袭：每 5 次攻击 3 倍暴击。
         */
        if (logicalCat.hasSkill(
                CatSkill.SHADOW_STRIKE
        )) {

            int count =
                    battleState.nextAttackCount(
                            entityUuid
                    );

            if (count % 5 == 0) {
                damage *= 3;
            }
        }

        target.damage(
                damage,
                cat
        );

        /*
         * 装备（0.8.0）：至极项圈吸血自愈（治疗猫自身）；
         * 附加属性「血月」同样计入。
         */
        CatEquipItem equip =
                logicalCat.getEquippedItem();

        EquipBonusAttribute bonus =
                logicalCat.getEquippedBonus();

        int lifestealPercent =
                (equip == null
                        ? 0
                        : equip.getLifestealPercent())
                        + (bonus == null
                        ? 0
                        : bonus.getLifestealPercent());

        if (lifestealPercent > 0 &&
                cat.isValid() &&
                !cat.isDead()) {

            double lifesteal =
                    damage
                            * lifestealPercent
                            / 100.0;

            if (lifesteal > 0.0) {

                org.bukkit.attribute.AttributeInstance maxAttribute =
                        cat.getAttribute(
                                Attribute.MAX_HEALTH
                        );

                double maxHealth =
                        maxAttribute == null
                                ? cat.getHealth()
                                : maxAttribute.getValue();

                cat.setHealth(
                        Math.min(
                                maxHealth,
                                cat.getHealth()
                                        + lifesteal
                        )
                );
            }
        }

        /*
         * 汲取：伤害 20% 治疗主人。
         */
        if (logicalCat.hasSkill(
                CatSkill.DRAIN
        )) {

            healOwner(
                    owner,
                    damage * 0.2
            );
        }

        /*
         * 星屑：20% 概率溅射（溅射只伤敌对生物）。
         */
        if (logicalCat.hasSkill(
                CatSkill.STAR_DUST
        ) &&
                random.nextDouble() < 0.2) {

            applySplash(
                    cat,
                    target,
                    damage
            );
        }
    }

    /*
     * ============================================================
     * 缓慢回血（4 秒 1 点，恢复期外）
     * ============================================================
     */

    private void tickRegen(
            Cat logicalCat,
            org.bukkit.entity.Cat cat,
            UUID entityUuid
    ) {

        double max =
                cat.getMaxHealth();

        if (cat.getHealth() >= max) {
            return;
        }

        long intervalMillis =
                configManager.snapshot()
                        .getBattle()
                        .getRegenIntervalSeconds()
                        * 1000L;

        if (!battleState.canRegen(
                entityUuid,
                intervalMillis
        )) {

            return;
        }

        battleState.markRegen(
                entityUuid
        );

        /*
         * 装备（0.8.0）：至极围巾的缓慢回血加成。
         */
        double regenAmount = 1.0;

        CatEquipItem equip =
                logicalCat.getEquippedItem();

        if (equip != null &&
                equip.getRegenBoostPercent() > 0) {

            regenAmount =
                    1.0
                            + equip.getRegenBoostPercent()
                            / 100.0;
        }

        cat.setHealth(
                Math.min(
                        max,
                        cat.getHealth()
                                + regenAmount
                )
        );
    }

    /*
     * ============================================================
     * 目标选择（Issue #6 + 跨世界修复 + 活物协助）
     * ============================================================
     */

    private LivingEntity findTarget(
            Cat logicalCat,
            org.bukkit.entity.Cat cat,
            int aggroRadius
    ) {

        UUID ownerUuid =
                logicalCat.getOwnerUuid();

        UUID assistId =
                battleState.getAssistTarget(
                        ownerUuid
                );

        if (assistId != null) {

            Entity assistEntity =
                    Bukkit.getEntity(
                            assistId
                    );

            /*
             * 跨世界修复：
             * 距离计算前必须先确认协助目标与猫同世界。
             *
             * 协助目标可以是任意活物（主人主动攻击的
             * 和平生物也算），但排除玩家自身。
             */
            if (assistEntity instanceof LivingEntity living &&
                    !(living instanceof Player) &&
                    !living.isDead() &&
                    living.isValid() &&
                    living.getWorld() != null &&
                    living.getWorld()
                            .equals(
                                    cat.getWorld()
                            )) {

                double assistRadius =
                        aggroRadius
                                * ASSIST_RADIUS_MULTIPLIER;

                double distSq =
                        cat.getLocation()
                                .distanceSquared(
                                        living.getLocation()
                                );

                if (distSq <=
                        assistRadius * assistRadius) {

                    return living;
                }
            }

            /*
             * 协助目标已失效 / 太远 / 跨世界：
             * 惰性清除，回落到普通索敌。
             */
            battleState.clearAssistTarget(
                    ownerUuid
            );
        }

        /*
         * 自动索敌仍然只打敌对生物。
         */
        return findNearestMonster(
                cat.getLocation(),
                aggroRadius
        );
    }

    /*
     * ============================================================
     * 扑击靠近（Issue #6 + 落点安全）
     * ============================================================
     *
     * 沿"猫 → 目标"连线传送一段距离（单次最多 6 格）。
     * 已限速 1 秒一跳；
     * 落点必须通过 SafeTeleport 校验，
     * 找不到安全落点就放弃这一跳。
     */

    private void pounceTowards(
            org.bukkit.entity.Cat cat,
            LivingEntity target
    ) {

        Location catLoc =
                cat.getLocation();

        Location targetLoc =
                target.getLocation();

        /*
         * 防御性世界守卫：
         * 绝不跨世界量距离。
         */
        if (catLoc.getWorld() == null ||
                targetLoc.getWorld() == null ||
                !catLoc.getWorld()
                        .equals(
                                targetLoc.getWorld()
                        )) {

            return;
        }

        /*
         * 安全落点：
         * 沿连线尝试不同距离与高度，
         * 脚/头必须可通行、下方有实体、且不在岩浆或水中。
         * 找不到安全落点就放弃这一跳（下个 tick 再试），
         * 绝不盲跳进墙里。
         */
        Location destination =
                SafeTeleport.findPounceDestination(
                        catLoc,
                        targetLoc,
                        MAX_POUNCE_DISTANCE
                );

        if (destination == null) {
            return;
        }

        destination.setYaw(
                catLoc.getYaw()
        );

        destination.setPitch(
                catLoc.getPitch()
        );

        cat.teleport(
                destination
        );

        /*
         * 扑击尘土粒子反馈。
         */
        cat.getWorld()
                .spawnParticle(
                        Particle.CLOUD,
                        catLoc,
                        6,
                        0.3,
                        0.1,
                        0.3,
                        0.02
                );
    }

    /*
     * ============================================================
     * 伤害计算
     * ============================================================
     *
     * 基础 + 喵阶加成 + 锐爪 + 共鸣 + 月华（夜晚 ×1.2）。
     */

    private int computeDamage(
            Cat logicalCat,
            org.bukkit.entity.Cat cat
    ) {

        ConfigSnapshot.Battle battleConfig =
                configManager.snapshot()
                        .getBattle();

        int base =
                battleConfig.getBaseDamage();

        int perRank =
                battleConfig.getPerRankDamage();

        int damage =
                base
                        + perRank
                        * logicalCat.getMeowRank();

        if (logicalCat.hasSkill(
                CatSkill.SHARP_CLAW
        )) {

            damage +=
                    configManager.snapshot()
                            .getSkills()
                            .valueInt(
                                    CatSkill.SHARP_CLAW,
                                    "power",
                                    2
                            );
        }

        if (logicalCat.hasSkill(
                CatSkill.RESONANCE
        )) {

            damage +=
                    configManager.snapshot()
                            .getSkills()
                            .valueInt(
                                    CatSkill.RESONANCE,
                                    "power",
                                    5
                            );
        }

        /*
         * 装备（0.8.0）：项圈的近战伤害加成。
         */
        CatEquipItem equip =
                logicalCat.getEquippedItem();

        if (equip != null &&
                equip.getDamageBonus() > 0) {

            damage +=
                    equip.getDamageBonus();
        }

        /*
         * 附加属性（0.8.0）：星辉的近战伤害加成。
         */
        EquipBonusAttribute bonus =
                logicalCat.getEquippedBonus();

        if (bonus != null &&
                bonus.getDamageBonus() > 0) {

            damage +=
                    bonus.getDamageBonus();
        }

        /*
         * 月华：夜晚 +20%。
         */
        if (logicalCat.hasSkill(
                CatSkill.MOONLIGHT
        )) {

            World world =
                    cat.getWorld();

            if (world != null &&
                    world.getTime() >= 13000 &&
                    world.getTime() <= 23000) {

                damage =
                        (int) (damage * 1.2);
            }
        }

        /*
         * 羁绊纪元（0.8.0）：
         * 心情 × 羁绊战斗倍率，四舍五入后保底 1。
         */
        ConfigSnapshot.Care care =
                configManager.snapshot()
                        .getCare();

        damage =
                CareMath.applyDamage(
                        damage,
                        CareMath.battleDamageMultiplier(
                                logicalCat.getMood(),
                                CareMath.bondFor(
                                        logicalCat,
                                        care
                                ),
                                care
                        )
                );

        return Math.max(
                1,
                damage
        );
    }

    private Monster findNearestMonster(
            Location location,
            int radius
    ) {

        Monster nearest = null;

        double nearestSq =
                (double) radius * radius;

        for (Entity entity :
                location.getWorld()
                        .getNearbyEntities(
                                location,
                                radius,
                                radius,
                                radius
                        )) {

            if (entity instanceof Monster monster &&
                    !monster.isDead() &&
                    monster.isValid()) {

                double distSq =
                        location.distanceSquared(
                                monster.getLocation()
                        );

                if (distSq < nearestSq) {

                    nearestSq = distSq;
                    nearest = monster;
                }
            }
        }

        return nearest;
    }

    private void handleRangedAttack(
            Cat logicalCat,
            org.bukkit.entity.Cat cat,
            Player owner,
            LivingEntity target,
            UUID entityUuid
    ) {

        if (cat.getLocation()
                .distanceSquared(
                        target.getLocation()
                )
                > RANGED_RANGE * RANGED_RANGE) {

            return;
        }

        /*
         * 隔墙不打。
         */
        if (!cat.hasLineOfSight(target)) {
            return;
        }

        if (!battleState.canAttack(
                entityUuid,
                RANGED_ATTACK_INTERVAL_MS
        )) {

            return;
        }

        battleState.markAttack(
                entityUuid
        );

        /*
         * 灵弹伤害为近战 80%：
         * 取整后必须至少为 1，
         * 避免 1 × 0.8 = 0 的零伤害。
         */
        int damage =
                Math.max(
                        1,
                        (int) (computeDamage(
                                logicalCat,
                                cat
                        ) * 0.8)
                );

        target.damage(
                damage,
                cat
        );

        /*
         * 弹道粒子。
         */
        spawnProjectileParticles(
                cat.getLocation()
                        .add(0, 1, 0),
                target.getLocation()
                        .add(0, 1, 0)
        );

        /*
         * 汲取（远程同样生效）。
         */
        if (logicalCat.hasSkill(
                CatSkill.DRAIN
        )) {

            healOwner(
                    owner,
                    damage * 0.2
            );
        }

        /*
         * 装备（0.8.0）：至极项圈吸血自愈（远程同样生效）；
         * 附加属性「血月」同样计入。
         */
        CatEquipItem equip =
                logicalCat.getEquippedItem();

        EquipBonusAttribute bonus =
                logicalCat.getEquippedBonus();

        int lifestealPercent =
                (equip == null
                        ? 0
                        : equip.getLifestealPercent())
                        + (bonus == null
                        ? 0
                        : bonus.getLifestealPercent());

        if (lifestealPercent > 0 &&
                cat.isValid() &&
                !cat.isDead()) {

            double lifesteal =
                    damage
                            * lifestealPercent
                            / 100.0;

            if (lifesteal > 0.0) {

                org.bukkit.attribute.AttributeInstance maxAttribute =
                        cat.getAttribute(
                                Attribute.MAX_HEALTH
                        );

                double maxHealth =
                        maxAttribute == null
                                ? cat.getHealth()
                                : maxAttribute.getValue();

                cat.setHealth(
                        Math.min(
                                maxHealth,
                                cat.getHealth()
                                        + lifesteal
                        )
                );
            }
        }
    }

    private void healOwner(
            Player owner,
            double amount
    ) {

        double maxHealth =
                owner.getMaxHealth();

        owner.setHealth(
                Math.min(
                        maxHealth,
                        owner.getHealth()
                                + amount
                )
        );
    }

    private void applySplash(
            org.bukkit.entity.Cat cat,
            LivingEntity target,
            int damage
    ) {

        int splashDamage =
                (int) (damage * 0.5);

        for (Entity entity :
                target.getLocation()
                        .getWorld()
                        .getNearbyEntities(
                                target.getLocation(),
                                2,
                                2,
                                2
                        )) {

            /*
             * 溅射只伤敌对生物，
             * 避免误伤你养的动物。
             */
            if (entity instanceof Monster monster &&
                    !monster.isDead() &&
                    monster.isValid() &&
                    !monster.equals(target)) {

                monster.damage(
                        splashDamage,
                        cat
                );
            }
        }
    }

    private void spawnProjectileParticles(
            Location from,
            Location to
    ) {

        if (from == null ||
                to == null) {

            return;
        }

        World world =
                from.getWorld();

        /*
         * 防御性守卫：
         * Location.distance() 对跨世界/空世界同样抛异常。
         */
        if (world == null ||
                to.getWorld() == null ||
                !world.equals(
                        to.getWorld()
                )) {

            return;
        }

        double distance =
                from.distance(
                        to
                );

        /*
         * 边界条件治理：
         * 猫与目标完全重合时 distance == 0，
         * 会导致 steps == 0 且 t = 0 / 0.0 = NaN，
         * NaN 坐标会击穿 spawnParticle 的坐标校验。
         * 此时只生成一颗粒子即可。
         */
        if (distance <= 0.0) {

            world.spawnParticle(
                    Particle.END_ROD,
                    from,
                    1,
                    0,
                    0,
                    0,
                    0
            );

            return;
        }

        /*
         * steps 下限 1：t 永远不会除以 0。
         */
        int steps =
                Math.max(
                        1,
                        (int) Math.ceil(
                                distance * 4
                        )
                );

        for (int i = 0;
             i <= steps;
             i++) {

            double t =
                    i / (double) steps;

            Location point =
                    from.clone()
                            .add(
                                    to.toVector()
                                            .subtract(
                                                    from.toVector()
                                            )
                                            .multiply(t)
                            );

            world.spawnParticle(
                    Particle.END_ROD,
                    point,
                    1,
                    0,
                    0,
                    0,
                    0
            );
        }
    }
}
