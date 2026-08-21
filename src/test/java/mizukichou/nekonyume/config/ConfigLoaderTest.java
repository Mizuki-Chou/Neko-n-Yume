package mizukichou.nekonyume.config;

import mizukichou.nekonyume.cat.CatMood;
import mizukichou.nekonyume.cat.MeowDanQuality;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置解析纯单元测试（无需 Bukkit 服务器实例）。
 *
 * <p>
 * 0.7.0：ConfigLoader 与 ConfigSnapshot 分离后，
 * 解析 / 钳制 / 回退逻辑全部可脱离 plugin 实例测试。
 * </p>
 */
class ConfigLoaderTest {

    private final Logger logger =
            Logger.getAnonymousLogger();

    private ConfigSnapshot load(String yaml) {

        YamlConfiguration config =
                new YamlConfiguration();

        try {

            config.loadFromString(
                    yaml
            );

        } catch (org.bukkit.configuration.InvalidConfigurationException e) {

            throw new RuntimeException(
                    e
            );
        }

        return ConfigLoader.load(
                config,
                logger
        );
    }

    @Test
    void defaultsFillMissingSections() {

        ConfigSnapshot config =
                load("");

        assertEquals(
                "zh_cn",
                config.getLanguage()
        );

        assertEquals(
                5,
                config.getGrowth().getPetXpMin()
        );

        assertEquals(
                30,
                config.getGrowth().getPetXpMax()
        );

        assertEquals(
                100,
                config.getGrowth().getLevelCurveBase()
        );

        assertTrue(
                config.getBattle().isEnabled()
        );

        assertEquals(
                120,
                config.getBattle().getRecoverySeconds()
        );

        assertEquals(
                1,
                config.getItems().getMeowdanGeneration()
        );

        assertTrue(
                config.getAchievements().isEnabled()
        );

        assertFalse(
                config.getFood().getValues().isEmpty()
        );

        /*
         * 0.7.4：经验丸默认值。
         */
        assertEquals(
                50,
                config.getXpPill().getNormalXp()
        );

        assertEquals(
                100,
                config.getXpPill().getEliteXp()
        );

        assertEquals(
                1,
                config.getBattle().getXpPerKillMin()
        );

        assertEquals(
                3,
                config.getBattle().getXpPerKillMax()
        );

        assertEquals(
                100,
                config.getBattle().getDragonXp()
        );

        assertEquals(
                30,
                config.getBattle().getWitherXpMin()
        );

        assertEquals(
                50,
                config.getBattle().getWitherXpMax()
        );

        /*
         * 0.8.0：掉落配置（drops 节）默认值。
         */
        ConfigSnapshot.Drops.DropSet mumaDrops =
                config.getDrops().getMumaNight();

        assertTrue(mumaDrops.isEnabled());

        assertEquals(
                0.15,
                mumaDrops.getMeowdanChance(),
                1e-9
        );

        assertEquals(
                0.03,
                mumaDrops.getXpPillChance(),
                1e-9
        );

        assertEquals(
                0.01,
                mumaDrops.getEliteXpPillChance(),
                1e-9
        );

        assertEquals(
                0.02,
                mumaDrops.getEquipBagChance(),
                1e-9
        );

        assertArrayEquals(
                new int[]{
                        80,
                        16,
                        3,
                        1,
                        0
                },
                mumaDrops.getMeowdanQualityWeights()
        );

        ConfigSnapshot.Drops.DropSet generalDrops =
                config.getDrops().getGeneral();

        assertFalse(generalDrops.isEnabled());

        assertEquals(
                0.05,
                generalDrops.getMeowdanChance(),
                1e-9
        );

        assertArrayEquals(
                new int[]{
                        80,
                        16,
                        3,
                        0,
                        0
                },
                generalDrops.getMeowdanQualityWeights()
        );

        assertEquals(
                0.0,
                generalDrops.getEquipBagChance(),
                1e-9
        );
    }

    @Test
    void xpPillAndBattleXpAreParsedAndNormalized() {

        ConfigSnapshot config =
                load("""
                        xp-pill:
                          normal-xp: -10
                          elite-xp: 500
                        battle:
                          xp-per-kill-min: 7
                          xp-per-kill-max: 2
                          dragon-xp: -3
                          wither-xp-min: 40
                          wither-xp-max: 10
                        muma-night:
                          xp-pill-drop-chance: 1.5
                          elite-xp-pill-drop-chance: -0.2
                        """);

        /*
         * 负值钳到 ≥1；min > max 时以 min 为准。
         */
        assertEquals(
                1,
                config.getXpPill().getNormalXp()
        );

        assertEquals(
                500,
                config.getXpPill().getEliteXp()
        );

        assertEquals(
                7,
                config.getBattle().getXpPerKillMin()
        );

        assertEquals(
                7,
                config.getBattle().getXpPerKillMax()
        );

        assertEquals(
                0,
                config.getBattle().getDragonXp()
        );

        assertEquals(
                40,
                config.getBattle().getWitherXpMin()
        );

        assertEquals(
                40,
                config.getBattle().getWitherXpMax()
        );

        /*
         * 概率钳到 [0,1]；缺新键时回退旧键
         * （muma-night.xp-pill-drop-chance，0.7.x 兼容）。
         */
        assertEquals(
                1.0,
                config.getDrops()
                        .getMumaNight()
                        .getXpPillChance(),
                1e-9
        );

        assertEquals(
                0.0,
                config.getDrops()
                        .getMumaNight()
                        .getEliteXpPillChance(),
                1e-9
        );
    }

    @Test
    void invalidValuesAreClamped() {

        ConfigSnapshot config =
                load("""
                        growth:
                          pet-xp-min: -5
                          pet-xp-max: 1
                        daily:
                          pet-limit: 0
                        """);

        assertEquals(
                1,
                config.getGrowth().getPetXpMin()
        );

        /*
         * pet-xp-max 低于 pet-xp-min 时钳制到 min。
         */
        assertEquals(
                1,
                config.getGrowth().getPetXpMax()
        );

        assertEquals(
                1,
                config.getDaily().getPetLimit()
        );
    }

    @Test
    void foodValuesParseAndFallback() {

        ConfigSnapshot config =
                load("""
                        food:
                          values:
                            COD: 25
                            BREAD: 0
                            NOT_A_MATERIAL: 10
                        """);

        /*
         * 只有合法条目生效：COD=25；
         * BREAD 值非法被跳过；未知材质被跳过；
         * 但整体非空，不回退默认表。
         */
        assertEquals(
                25,
                config.getFood().getValues().get(
                        Material.COD
                )
        );

        assertEquals(
                1,
                config.getFood().getValues().size()
        );
    }

    @Test
    void giftTiersParse() {

        ConfigSnapshot config =
                load("""
                        gift:
                          enabled: false
                          mood-min: HAPPY
                          tiers:
                            tier-1:
                              - material: COD
                                amount: 2
                                weight: 50
                              - meowdan: EPIC
                                min: 1
                                max: 2
                                weight: 10
                        """);

        ConfigSnapshot.Gift gift =
                config.getGift();

        assertFalse(gift.isEnabled());

        assertEquals(
                CatMood.HAPPY,
                gift.getMoodMin()
        );

        assertEquals(
                1,
                gift.getMaxTier()
        );

        assertEquals(
                2,
                gift.tierExact(1).size()
        );

        GiftItemEntry entry =
                gift.tierExact(1).get(1);

        assertTrue(entry.isMeowDan());

        assertEquals(
                MeowDanQuality.EPIC,
                entry.getMeowDanQuality()
        );

        assertEquals(
                1,
                entry.getMinAmount()
        );

        assertEquals(
                2,
                entry.getMaxAmount()
        );
    }

    @Test
    void achievementsRewardsOnlyOverrideConfigured() {

        ConfigSnapshot config =
                load("""
                        achievements:
                          enabled: false
                          rewards:
                            first-claim:
                              xp: 999
                            monster-kill-50:
                              meow-power: 7
                        """);

        ConfigSnapshot.Achievements achievements =
                config.getAchievements();

        assertFalse(achievements.isEnabled());

        assertEquals(
                999,
                achievements.rewardXp(
                        mizukichou.nekonyume.achievement.CatAchievement
                                .FIRST_CLAIM,
                        50
                )
        );

        assertEquals(
                7,
                achievements.rewardMeowPower(
                        mizukichou.nekonyume.achievement.CatAchievement
                                .MONSTER_KILL_50,
                        5
                )
        );

        /*
         * 未配置的成就回退默认值。
         */
        assertEquals(
                80,
                achievements.rewardXp(
                        mizukichou.nekonyume.achievement.CatAchievement
                                .GIFT_1,
                        80
                )
        );
    }

    @Test
    void skillValuesParseAsDoubles() {

        ConfigSnapshot config =
                load("""
                        skills:
                          refresh:
                            cost-type: player-points
                            cost: 15
                            dream-slot-cost-multiplier: 4
                          values:
                            sharp_claw:
                              power: 3
                              chance: 0.5
                        """);

        ConfigSnapshot.Skills skills =
                config.getSkills();

        assertEquals(
                "player-points",
                skills.getRefreshCostType()
        );

        assertEquals(
                15,
                skills.getRefreshCost()
        );

        assertEquals(
                4,
                skills.getDreamSlotCostMultiplier()
        );

        assertEquals(
                3,
                skills.valueInt(
                        mizukichou.nekonyume.cat.CatSkill.SHARP_CLAW,
                        "power",
                        2
                )
        );

        assertEquals(
                0.5,
                skills.value(
                        mizukichou.nekonyume.cat.CatSkill.SHARP_CLAW,
                        "chance",
                        0.0
                ),
                0.0001
        );
    }

    @Test
    void battleSectionParsesAllFields() {

        ConfigSnapshot config =
                load("""
                        battle:
                          enabled: false
                          recovery-seconds: 45
                          regen-interval-seconds: 2
                          eternity-rebirth-seconds: 240
                        """);

        ConfigSnapshot.Battle battle =
                config.getBattle();

        assertFalse(battle.isEnabled());

        assertEquals(
                45,
                battle.getRecoverySeconds()
        );

        assertEquals(
                2,
                battle.getRegenIntervalSeconds()
        );

        assertEquals(
                240,
                battle.getEternityRebirthSeconds()
        );

        /*
         * 未配置的字段保持默认。
         */
        assertEquals(
                5,
                battle.getBaseDamage()
        );

        assertEquals(
                40,
                battle.getAttackIntervalTicks()
        );
    }

    @Test
    void meowdanCustomModelDataParses() {

        ConfigSnapshot config =
                load("""
                        items:
                          meowdan:
                            generation: 2
                            custom-model-data:
                              legendary: 92005
                        """);

        assertEquals(
                2,
                config.getItems().getMeowdanGeneration()
        );

        assertEquals(
                92005,
                config.getItems().meowdanCustomModelData(
                        MeowDanQuality.LEGENDARY
                )
        );

        /*
         * 未配置的品质回退枚举默认 CMD。
         */
        assertEquals(
                MeowDanQuality.COMMON.getDefaultModelData(),
                config.getItems().meowdanCustomModelData(
                        MeowDanQuality.COMMON
                )
        );
    }

    @Test
    void storageAndJoinMessageParse() {

        ConfigSnapshot config =
                load("""
                        storage:
                          backup:
                            enabled: false
                            keep: 9
                        join-message:
                          enabled: true
                          messages:
                            - "<gold>你好 {0}</gold>"
                        """);

        assertFalse(
                config.getStorage().isBackupEnabled()
        );

        assertEquals(
                9,
                config.getStorage().getBackupKeep()
        );

        assertTrue(
                config.getJoinMessage().isEnabled()
        );

        assertEquals(
                1,
                config.getJoinMessage().getMessages().size()
        );

        assertNotNull(
                config.getMumaNight()
        );

        assertEquals(
                0.2,
                config.getMumaNight().getChance(),
                0.0001
        );
    }

    @Test
    void careSectionParsesWithDefaultsAndClamps() {

        ConfigSnapshot config =
                load("");

        ConfigSnapshot.Care care =
                config.getCare();

        /*
         * 缺失节时全部默认值。
         */
        assertEquals(
                2,
                care.getAffectionDailyDecay()
        );

        assertEquals(
                8,
                care.getFeedHungryAffection()
        );

        assertEquals(
                2,
                care.getFeedNormalAffection()
        );

        assertEquals(
                20,
                care.getHungrySkillThreshold()
        );

        assertEquals(
                0,
                care.getStarvingFightThreshold()
        );

        assertEquals(
                20,
                care.getHungryFeedThreshold()
        );

        assertEquals(
                15,
                care.getMoodDamagePercent()
                        .get(CatMood.ECSTATIC),
                0.0001
        );

        assertEquals(
                -20,
                care.getMoodDamagePercent()
                        .get(CatMood.SAD),
                0.0001
        );

        assertEquals(
                10,
                care.getMoodXpPercent()
                        .get(CatMood.ECSTATIC),
                0.0001
        );

        assertEquals(
                java.util.List.of(
                        20,
                        40,
                        60,
                        80,
                        100
                ),
                care.getBondTierThresholds()
        );

        assertEquals(
                6,
                care.getBondXpPercent().size()
        );

        assertEquals(
                10,
                care.getDefeatHealthLoss()
        );

        assertEquals(
                5,
                care.getFeedHealthRestore()
        );

        /*
         * 饥饿好感衰减节流间隔（0.8.0）：默认 180 分钟。
         */
        assertEquals(
                180,
                care.getHungerAffectionLossMinutes()
        );

        /*
         * 覆盖 + 钳制：
         * 百分比 200 → 100；衰减 50 → 10；门槛 -1 保留（关闭）。
         */
        ConfigSnapshot overridden =
                load("""
                        care:
                          mood-damage-percent:
                            ECSTATIC: 200
                          affection-daily-decay: 50
                          hungry-skill-threshold: -1
                          hunger-affection-loss-minutes: 3000
                        """);

        ConfigSnapshot.Care oc =
                overridden.getCare();

        assertEquals(
                100,
                oc.getMoodDamagePercent()
                        .get(CatMood.ECSTATIC),
                0.0001
        );

        assertEquals(
                10,
                oc.getAffectionDailyDecay()
        );

        assertEquals(
                -1,
                oc.getHungrySkillThreshold()
        );

        /*
         * 其余心情键保持默认（覆盖只影响指定键）。
         */
        assertEquals(
                -20,
                oc.getMoodDamagePercent()
                        .get(CatMood.SAD),
                0.0001
        );

        /*
         * 节流间隔钳制：3000 → 1440（上限一天）。
         */
        assertEquals(
                1440,
                oc.getHungerAffectionLossMinutes()
        );
    }

    @Test
    void careInvalidThresholdsFallBackToDefaults() {

        ConfigSnapshot config =
                load("""
                        care:
                          bond-tier-thresholds: [100, 80, 60]
                          bond-xp-percent: [1, 2]
                        """);

        ConfigSnapshot.Care care =
                config.getCare();

        /*
         * 非严格递增阈值 → 回退默认；
         * 与阈值数量不匹配的增益数组 → 回退默认。
         */
        assertEquals(
                java.util.List.of(
                        20,
                        40,
                        60,
                        80,
                        100
                ),
                care.getBondTierThresholds()
        );

        assertEquals(
                java.util.List.of(
                        0,
                        0,
                        5,
                        10,
                        10,
                        10
                ),
                care.getBondXpPercent()
        );
    }
}
