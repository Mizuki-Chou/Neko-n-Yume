package mizukichou.nekonyume.task;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.config.PluginConfig;
import mizukichou.nekonyume.skill.CatBattleState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.util.Random;
import java.util.UUID;

/**
 * 猫咪战斗任务。
 *
 * <p>
 * 每 10 tick 检查一次：
 * 跟随模式下自动攻击主人附近的敌对生物。
 * 近战为主；拥有「灵弹」技能时改为远程魔法弹。
 * </p>
 */
public class CatBattleTask implements Runnable {

    /*
     * 近战范围（格）。
     */
    private static final double MELEE_RANGE = 2.5;

    /*
     * 远程（灵弹）射程（格）。
     */
    private static final double RANGED_RANGE = 12.0;

    /*
     * 远程攻击间隔（毫秒）。
     */
    private static final long RANGED_ATTACK_INTERVAL_MS =
            3000L;

    private final PluginConfig config;
    private final CatCache cache;
    private final CatBattleState battleState;

    /*
     * 任务内统一随机源（不用 Math.random）。
     */
    private final Random random =
            new Random();

    public CatBattleTask(
            PluginConfig config,
            CatCache cache,
            CatBattleState battleState
    ) {

        this.config = config;
        this.cache = cache;
        this.battleState = battleState;
    }

    @Override
    public void run() {

        if (!config.isBattleEnabled()) {
            return;
        }

        for (Cat logicalCat :
                cache.getCats()) {

            /*
             * 只在跟随模式战斗。
             */
            if (logicalCat.getBehaviorMode()
                    != CatBehaviorMode.FOLLOW) {

                continue;
            }

            Player owner =
                    Bukkit.getPlayer(
                            logicalCat.getOwnerUuid()
                    );

            if (owner == null ||
                    !owner.isOnline()) {

                continue;
            }

            UUID entityUuid =
                    logicalCat.getEntityUuid();

            if (entityUuid == null) {
                continue;
            }

            Entity entity =
                    Bukkit.getEntity(
                            entityUuid
                    );

            if (!(entity instanceof org.bukkit.entity.Cat cat) ||
                    cat.isDead() ||
                    !cat.isValid()) {

                continue;
            }

            World world =
                    cat.getLocation()
                            .getWorld();

            if (world == null ||
                    !world.equals(
                            owner.getWorld()
                    )) {

                continue;
            }

            /*
             * 主人距离限制：
             * 猫离主人太远不战斗，
             * 避免在野外乱引怪。
             */
            int aggroRadius =
                    config.getBattleAggroRadius();

            if (cat.getLocation()
                    .distanceSquared(
                            owner.getLocation()
                    )
                    > (double) aggroRadius
                    * aggroRadius) {

                continue;
            }

            /*
             * 虚弱期不攻击。
             */
            long weaknessMs =
                    (logicalCat.hasSkill(
                            CatSkill.NINE_LIVES
                    )
                            ? 3L
                            : config.getBattleWeaknessSeconds())
                            * 1000L;

            if (battleState.isWeakened(
                    entityUuid,
                    weaknessMs
            )) {

                continue;
            }

            /*
             * 找最近敌对目标。
             */
            Monster target =
                    findNearestMonster(
                            cat.getLocation(),
                            aggroRadius
                    );

            if (target == null) {
                continue;
            }

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

                continue;
            }

            /*
             * 近战。
             */
            if (cat.getLocation()
                    .distanceSquared(
                            target.getLocation()
                    )
                    > MELEE_RANGE * MELEE_RANGE) {

                continue;
            }

            long intervalTicks =
                    config.getBattleAttackIntervalTicks();

            /*
             * 灵步：攻击间隔 -20%。
             */
            if (logicalCat.hasSkill(
                    CatSkill.LIGHT_STEP
            )) {

                intervalTicks =
                        (long) (intervalTicks * 0.8);
            }

            long intervalMs =
                    intervalTicks * 50L;

            if (!battleState.canAttack(
                    entityUuid,
                    intervalMs
            )) {

                continue;
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
             * 星屑：20% 概率溅射。
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

        int base =
                config.getBattleBaseDamage();

        int perRank =
                config.getBattlePerRankDamage();

        int damage =
                base
                        + perRank
                        * logicalCat.getMeowRank();

        if (logicalCat.hasSkill(
                CatSkill.SHARP_CLAW
        )) {

            damage +=
                    config.getSkillValue(
                            CatSkill.SHARP_CLAW,
                            "power",
                            2
                    );
        }

        if (logicalCat.hasSkill(
                CatSkill.RESONANCE
        )) {

            damage +=
                    config.getSkillValue(
                            CatSkill.RESONANCE,
                            "power",
                            5
                    );
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
            Monster target,
            UUID entityUuid
    ) {

        if (cat.getLocation()
                .distanceSquared(
                        target.getLocation()
                )
                > RANGED_RANGE * RANGED_RANGE) {

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

        int damage =
                (int) (computeDamage(
                        logicalCat,
                        cat
                ) * 0.8);

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
            Monster target,
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

        double distance =
                from.distance(
                        to
                );

        int steps =
                (int) Math.ceil(
                        distance * 4
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

            from.getWorld()
                    .spawnParticle(
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
