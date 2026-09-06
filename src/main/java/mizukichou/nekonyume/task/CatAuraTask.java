package mizukichou.nekonyume.task;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEquipItem;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.skill.CatBattleState;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * 猫咪光环任务捏。
 *
 * <p>
 * 每 2 秒刷新一次啦：
 * 跟随模式 + 光环范围内哒，
 * 主人获得增益（速度 / 力量 / 再生 / 月华）。
 * </p>
 *
 * <p>
 * 受伤恢复期内光环停摆。
 * 0.7.0：配置改走 ConfigManager 快照。
 * </p>
 */
public class CatAuraTask implements Runnable {

    /*
     * 光环持续时间（秒）。
     * 每 2 秒刷新一次，实现常驻。
     */
    private static final int AURA_DURATION_SECONDS = 8;

    private static final PotionEffectType[] AURA_EFFECT_TYPES = {
            PotionEffectType.SPEED,
            PotionEffectType.STRENGTH,
            PotionEffectType.REGENERATION
    };

    private final ConfigManager configManager;
    private final CatCache cache;
    private final CatBattleState battleState;
    private final Logger logger;

    /*
     * 0.9.0更新：光环效果的"记录-比较-恢复"——
     * 首次进入光环时记录玩家同类型原效果；离开光环时
     * 恢复（仅当原效果存在），绝不覆盖/永久夺走其它
     * 系统（插件/管理员/剧情）设置的效果。
     */
    private final Map<UUID, Map<PotionEffectType, PotionEffect>>
            auraPrevious =
            new java.util.HashMap<>();

    private final java.util.Set<UUID> activeAuraOwners =
            new java.util.HashSet<>();

    public CatAuraTask(
            ConfigManager configManager,
            CatCache cache,
            CatBattleState battleState,
            Logger logger
    ) {

        this.configManager = configManager;
        this.cache = cache;
        this.battleState = battleState;
        this.logger = logger;
    }

    @Override
    public void run() {

        ConfigSnapshot config =
                configManager.snapshot();

        if (!config.getAura()
                .isEnabled()) {

            return;
        }

        ConfigSnapshot.Aura auraConfig =
                config.getAura();

        activeAuraOwners.clear();

        for (Cat logicalCat :
                cache.getCats()) {

            /*
             * 单猫异常隔离。
             */
            try {

                applyAura(
                        auraConfig,
                        logicalCat
                );

            } catch (Exception exception) {

                logger.warning(
                        "Aura tick failed for cat "
                                + logicalCat.getId()
                                + ": "
                                + exception.getMessage()
                );
            }
        }

        /*
         * 0.9.0更新：离开光环的玩家恢复原效果。
         */
        for (UUID ownerUuid :
                new java.util.ArrayList<>(
                        auraPrevious.keySet()
                )) {

            if (activeAuraOwners.contains(
                    ownerUuid
            )) {

                continue;
            }

            Player leaving =
                    Bukkit.getPlayer(
                            ownerUuid
                    );

            Map<PotionEffectType, PotionEffect> previous =
                    auraPrevious.remove(
                            ownerUuid
                    );

            if (leaving == null ||
                    !leaving.isOnline() ||
                    previous == null) {

                continue;
            }

            for (PotionEffectType type :
                    AURA_EFFECT_TYPES) {

                leaving.removePotionEffect(
                        type
                );

                PotionEffect original =
                        previous.get(
                                type
                        );

                if (original != null &&
                        !leaving.hasPotionEffect(
                                type
                        )) {

                    leaving.addPotionEffect(
                            original
                    );
                }
            }
        }
    }

    private void applyAura(
            ConfigSnapshot.Aura auraConfig,
            Cat logicalCat
    ) {

        if (logicalCat.getBehaviorMode()
                != CatBehaviorMode.FOLLOW) {

            return;
        }

        Player owner =
                Bukkit.getPlayer(
                        logicalCat.getOwnerUuid()
                );

        if (owner == null ||
                !owner.isOnline()) {

            return;
        }

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
         * 受伤恢复期内光环停摆。
         */
        if (battleState.isRecovering(
                cat.getUniqueId()
        )) {

            return;
        }

        if (cat.getLocation()
                .getWorld() == null ||
                !cat.getLocation()
                        .getWorld()
                        .equals(
                                owner.getWorld()
                        )) {

            return;
        }

        /*
         * 光环范围：
         * 基础 10，警觉 12，狩猎直觉 15。
         */
        int radius =
                auraConfig.getBaseRadius();

        if (logicalCat.hasSkill(
                CatSkill.ALERT
        )) {

            radius = 12;
        }

        if (logicalCat.hasSkill(
                CatSkill.HUNTER_SENSE
        )) {

            radius = 15;
        }

        /*
         * 装备（0.8.0）：铃铛的光环半径加成。
         */
        CatEquipItem equip =
                logicalCat.getEquippedItem();

        if (equip != null &&
                equip.getAuraBonus() > 0) {

            radius +=
                    equip.getAuraBonus();
        }

        double distSq =
                cat.getLocation()
                        .distanceSquared(
                                owner.getLocation()
                        );

        if (distSq >
                (double) radius * radius) {

            return;
        }

        int durationTicks =
                AURA_DURATION_SECONDS * 20;

        /*
         * 首次进入光环（本 tick 首次登记）：记录原效果。
         */
        UUID ownerUuid =
                owner.getUniqueId();

        if (activeAuraOwners.add(
                ownerUuid
        ) &&
                !auraPrevious.containsKey(
                        ownerUuid
                )) {

            Map<PotionEffectType, PotionEffect> previous =
                    new java.util.HashMap<>();

            for (PotionEffectType type :
                    AURA_EFFECT_TYPES) {

                previous.put(
                        type,
                        owner.getPotionEffect(
                                type
                        )
                );
            }

            auraPrevious.put(
                    ownerUuid,
                    previous
            );
        }

        /*
         * 速度光环：
         * 等级达标 → +1 级；暖意 → 再 +1 级。
         */
        int speedAmp = 0;

        if (logicalCat.getLevel() >=
                auraConfig.getSpeedUnlockLevel()) {

            speedAmp++;
        }

        if (logicalCat.hasSkill(
                CatSkill.WARMTH
        )) {

            speedAmp++;
        }

        /*
         * 装备（0.8.0）：卓越/至极铃铛的光环加速。
         */
        if (equip != null &&
                equip.isAuraSpeed()) {

            speedAmp++;
        }

        if (speedAmp > 0) {

            owner.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.SPEED,
                            durationTicks,
                            speedAmp - 1
                    )
            );
        }

        /*
         * 力量光环：喵阶达标。
         */
        if (logicalCat.getMeowRank() >=
                auraConfig.getStrengthUnlockMeowRank()) {

            owner.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.STRENGTH,
                            durationTicks,
                            0
                    )
            );
        }

        /*
         * 再生光环：等级 + 好感达标。
         */
        if (logicalCat.getLevel() >=
                auraConfig.getRegenUnlockLevel() &&
                logicalCat.getAffection() >=
                        auraConfig.getRegenAffection()) {

            owner.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.REGENERATION,
                            durationTicks,
                            0
                    )
            );
        }

        /*
         * 月华：
         * 夜晚 → 主人力量 I + 速度 I
         * 白昼 → 主人常驻再生 I
         */
        if (logicalCat.hasSkill(
                CatSkill.MOONLIGHT
        )) {

            World world =
                    owner.getWorld();

            boolean night =
                    world != null &&
                            world.getTime() >= 13000 &&
                            world.getTime() <= 23000;

            if (night) {

                owner.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.STRENGTH,
                                durationTicks,
                                0
                        )
                );

                owner.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.SPEED,
                                durationTicks,
                                0
                        )
                );

            } else {

                owner.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.REGENERATION,
                                durationTicks,
                                0
                        )
                );
            }
        }
    }
}

