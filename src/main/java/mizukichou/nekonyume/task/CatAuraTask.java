package mizukichou.nekonyume.task;

import mizukichou.nekonyume.NekoNYume;
import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.cat.CatSkill;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * 猫咪光环任务。
 *
 * <p>
 * 每 2 秒刷新：
 * 跟随模式 + 光环范围内，
 * 主人获得增益（速度 / 力量 / 再生 / 月华）。
 * </p>
 */
public class CatAuraTask implements Runnable {

    /*
     * 光环持续时间（秒）。
     * 每 2 秒刷新一次，实现常驻。
     */
    private static final int AURA_DURATION_SECONDS = 8;

    private final NekoNYume plugin;

    public CatAuraTask(
            NekoNYume plugin
    ) {

        this.plugin = plugin;
    }

    @Override
    public void run() {

        if (!plugin.getPluginConfig()
                .isAuraEnabled()) {

            return;
        }

        for (Cat logicalCat :
                plugin.getCatManager()
                        .getCats()) {

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

            if (cat.getLocation()
                    .getWorld() == null ||
                    !cat.getLocation()
                            .getWorld()
                            .equals(
                                    owner.getWorld()
                            )) {

                continue;
            }

            /*
             * 光环范围：
             * 基础 10，警觉 12，狩猎直觉 15。
             */
            int radius =
                    plugin.getPluginConfig()
                            .getAuraBaseRadius();

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

            double distSq =
                    cat.getLocation()
                            .distanceSquared(
                                    owner.getLocation()
                            );

            if (distSq >
                    (double) radius * radius) {

                continue;
            }

            int durationTicks =
                    AURA_DURATION_SECONDS * 20;

            /*
             * 速度光环：
             * 等级达标 → +1 级；暖意 → 再 +1 级。
             */
            int speedAmp = 0;

            if (logicalCat.getLevel() >=
                    plugin.getPluginConfig()
                            .getAuraSpeedUnlockLevel()) {

                speedAmp++;
            }

            if (logicalCat.hasSkill(
                    CatSkill.WARMTH
            )) {

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
                    plugin.getPluginConfig()
                            .getAuraStrengthUnlockMeowRank()) {

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
                    plugin.getPluginConfig()
                            .getAuraRegenUnlockLevel() &&
                    logicalCat.getAffection() >=
                            plugin.getPluginConfig()
                                    .getAuraRegenAffection()) {

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
}