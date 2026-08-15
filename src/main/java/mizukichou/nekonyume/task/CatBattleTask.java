package mizukichou.nekonyume.task;

import mizukichou.nekonyume.NekoNYume;
import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.cat.CatSkill;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

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

    private final NekoNYume plugin;

    public CatBattleTask(
            NekoNYume plugin
    ) {

        this.plugin = plugin;
    }

    @Override
    public void run() {

        if (!plugin.getPluginConfig()
                .isBattleEnabled()) {

            return;
        }

        for (Cat logicalCat :
                plugin.getCatManager()
                        .getCats()) {

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
                    plugin.getPluginConfig()
                            .getBattleAggroRadius();

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
                            : plugin.getPluginConfig()
                            .getBattleWeaknessSeconds())
                            * 1000L;

            if (plugin.getBattleState()
                    .isWeakened(
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
                    plugin.getPluginConfig()
                            .getBattleAttackIntervalTicks();

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

            if (!plugin.getBattleState()
                    .canAttack(
                            entityUuid,
                            intervalMs
                    )) {

                continue;
            }

            plugin.getBattleState()
                    .markAttack(
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
                        plugin.getBattleState()
                                .nextAttackCount(
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
                    Math.random() < 0.2) {

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
                plugin.getPluginConfig()
                        .getBattleBaseDamage();

        int perRank =
                plugin.getPluginConfig()
                        .getBattlePerRankDamage();

        int damage =
                base
                        + perRank
                        * logicalCat.getMeowRank();

        if (logicalCat.hasSkill(
                CatSkill.SHARP_CLAW
        )) {

            damage +=
                    plugin.getPluginConfig()
                            .getSkillValue(
                                    CatSkill.SHARP_CLAW,
                                    "power",
                                    2
                            );
        }

        if (logicalCat.hasSkill(
                CatSkill.RESONANCE
        )) {

            damage +=
                    plugin.getPluginConfig()
                            .getSkillValue(
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

        if (!plugin.getBattleState()
                .canAttack(
                        entityUuid,
                        RANGED_ATTACK_INTERVAL_MS
                )) {

            return;
        }

        plugin.getBattleState()
                .markAttack(
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
