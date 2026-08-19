package mizukichou.nekonyume.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * 配置解析器（纯函数，门面）。
 *
 * <p>
 * 从 FileConfiguration 解析构建 ConfigSnapshot。
 * 所有数值带默认值与钳制，
 * 管理员写错配置不会让插件崩溃。
 * 本类不持有任何状态，可单元测试。
 * </p>
 *
 * <p>
 * God Object 拆分（0.7.3）：
 * 复杂 Section 的解析已下沉到同包解析器：
 * </p>
 *
 * <ul>
 *   <li>{@link ItemConfigParser}：喵丹物品 / 食物表；</li>
 *   <li>{@link GiftConfigParser}：每日礼物档位；</li>
 *   <li>{@link AchievementConfigParser}：成就奖励表；</li>
 *   <li>{@link SkillConfigParser}：技能数值与刷新花费；</li>
 *   <li>{@link ConfigParseSupport}：钳制 / 安全材质解析等纯工具。</li>
 * </ul>
 *
 * <p>
 * 本类只保留对简单标量 Section 的直接解析
 * 与对上述解析器的编排。
 * </p>
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static ConfigSnapshot load(
            FileConfiguration config,
            Logger logger
    ) {

        String language =
                config.getString(
                        "language",
                        "zh_cn"
                );

        ConfigSnapshot.Storage storage =
                new ConfigSnapshot.Storage(
                        config.getBoolean(
                                "storage.backup.enabled",
                                true
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "storage.backup.keep",
                                        5
                                ),
                                1
                        )
                );

        ConfigSnapshot.Items items =
                ItemConfigParser.loadItems(
                        config
                );

        int petXpMin =
                ConfigParseSupport.positive(
                        config.getInt(
                                "growth.pet-xp-min",
                                5
                        ),
                        1
                );

        ConfigSnapshot.Growth growth =
                new ConfigSnapshot.Growth(
                        petXpMin,
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "growth.pet-xp-max",
                                        30
                                ),
                                petXpMin
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "growth.level-curve-base",
                                        100
                                ),
                                1
                        )
                );

        ConfigSnapshot.Affection affection =
                new ConfigSnapshot.Affection(
                        ConfigParseSupport.clamp(
                                config.getInt(
                                        "affection.feed-base",
                                        15
                                ),
                                0,
                                100
                        ),
                        ConfigParseSupport.clamp(
                                config.getInt(
                                        "affection.pet-base",
                                        3
                                ),
                                0,
                                100
                        )
                );

        ConfigSnapshot.Meow meow =
                new ConfigSnapshot.Meow(
                        ConfigParseSupport.clamp(
                                config.getInt(
                                        "meow.pet-chance",
                                        18
                                ),
                                0,
                                100
                        ),
                        ConfigParseSupport.clamp(
                                config.getInt(
                                        "meow.feed-chance",
                                        8
                                ),
                                0,
                                100
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "meow.feed-chance-limit",
                                        3
                                ),
                                1
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "meow.rank-curve-offset",
                                        19
                                ),
                                1
                        )
                );

        long hungerIntervalMillis =
                config.getLong(
                        "hunger.base-interval-seconds",
                        300
                ) * 1000L;

        if (hungerIntervalMillis <= 0) {

            hungerIntervalMillis =
                    300L * 1000L;
        }

        ConfigSnapshot.Hunger hunger =
                new ConfigSnapshot.Hunger(
                        hungerIntervalMillis
                );

        ConfigSnapshot.Daily daily =
                new ConfigSnapshot.Daily(
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "daily.pet-limit",
                                        3
                                ),
                                1
                        )
                );

        ConfigSnapshot.Food food =
                new ConfigSnapshot.Food(
                        ItemConfigParser.loadFoodValues(
                                config,
                                logger
                        )
                );

        ConfigSnapshot.Gift gift =
                GiftConfigParser.loadGift(
                        config,
                        logger
                );

        ConfigSnapshot.Achievements achievements =
                AchievementConfigParser.loadAchievements(
                        config
                );

        ConfigSnapshot.Skills skills =
                SkillConfigParser.loadSkills(
                        config
                );

        /*
         * 0.7.4：战斗掉落经验区间（预先归一化）。
         */
        int xpPerKillMin =
                ConfigParseSupport.positive(
                        config.getInt(
                                "battle.xp-per-kill-min",
                                1
                        ),
                        0
                );

        int xpPerKillMax =
                Math.max(
                        xpPerKillMin,
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "battle.xp-per-kill-max",
                                        3
                                ),
                                0
                        )
                );

        int dragonXp =
                ConfigParseSupport.positive(
                        config.getInt(
                                "battle.dragon-xp",
                                100
                        ),
                        0
                );

        int witherXpMin =
                ConfigParseSupport.positive(
                        config.getInt(
                                "battle.wither-xp-min",
                                30
                        ),
                        0
                );

        int witherXpMax =
                Math.max(
                        witherXpMin,
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "battle.wither-xp-max",
                                        50
                                ),
                                0
                        )
                );

        ConfigSnapshot.Battle battle =
                new ConfigSnapshot.Battle(
                        config.getBoolean(
                                "battle.enabled",
                                true
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "battle.base-damage",
                                        5
                                ),
                                1
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "battle.per-rank-damage",
                                        1
                                ),
                                0
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "battle.attack-interval-ticks",
                                        40
                                ),
                                1
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "battle.aggro-radius",
                                        12
                                ),
                                1
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "battle.weakness-seconds",
                                        10
                                ),
                                1
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "battle.recovery-seconds",
                                        120
                                ),
                                1
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "battle.regen-interval-seconds",
                                        4
                                ),
                                1
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "battle.eternity-rebirth-seconds",
                                        180
                                ),
                                1
                        ),
                        /*
                         * 0.7.4：战斗掉落经验（猫击杀时）。
                         * min/max 归一化：min > max 时以 min 为准，
                         * 绝不让随机区间出现下界 > 上界。
                         */
                        xpPerKillMin,
                        xpPerKillMax,
                        dragonXp,
                        witherXpMin,
                        witherXpMax
                );

        ConfigSnapshot.Aura aura =
                new ConfigSnapshot.Aura(
                        config.getBoolean(
                                "aura.enabled",
                                true
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "aura.base-radius",
                                        10
                                ),
                                1
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "aura.speed-unlock-level",
                                        5
                                ),
                                1
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "aura.strength-unlock-meow-rank",
                                        2
                                ),
                                0
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "aura.regen-unlock-level",
                                        15
                                ),
                                1
                        ),
                        ConfigParseSupport.clamp(
                                config.getInt(
                                        "aura.regen-affection",
                                        80
                                ),
                                0,
                                100
                        )
                );

        ConfigSnapshot.JoinMessage joinMessage =
                new ConfigSnapshot.JoinMessage(
                        config.getBoolean(
                                "join-message.enabled",
                                true
                        ),
                        new ArrayList<>(
                                config.getStringList(
                                        "join-message.messages"
                                )
                        )
                );

        ConfigSnapshot.MumaNight mumaNight =
                new ConfigSnapshot.MumaNight(
                        ConfigParseSupport.unit(
                                config.getDouble(
                                        "muma-night.chance",
                                        0.2
                                )
                        ),
                        config.getDouble(
                                "muma-night.health-multiplier",
                                4.0
                        ),
                        config.getDouble(
                                "muma-night.damage-multiplier",
                                2.5
                        ),
                        ConfigParseSupport.unit(
                                config.getDouble(
                                        "muma-night.meowdan-drop-chance",
                                        0.15
                                )
                        ),
                        ConfigParseSupport.unit(
                                config.getDouble(
                                        "muma-night.xp-pill-drop-chance",
                                        0.03
                                )
                        ),
                        ConfigParseSupport.unit(
                                config.getDouble(
                                        "muma-night.elite-xp-pill-drop-chance",
                                        0.01
                                )
                        )
                );

        ConfigSnapshot.XpPill xpPill =
                new ConfigSnapshot.XpPill(
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "xp-pill.normal-xp",
                                        50
                                ),
                                1
                        ),
                        ConfigParseSupport.positive(
                                config.getInt(
                                        "xp-pill.elite-xp",
                                        100
                                ),
                                1
                        )
                );

        return new ConfigSnapshot(
                language,
                storage,
                items,
                growth,
                affection,
                meow,
                hunger,
                daily,
                food,
                gift,
                achievements,
                skills,
                battle,
                aura,
                joinMessage,
                mumaNight,
                xpPill
        );
    }
}
