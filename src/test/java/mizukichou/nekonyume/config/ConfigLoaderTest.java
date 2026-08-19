package mizukichou.nekonyume.config;

import mizukichou.nekonyume.cat.CatMood;
import mizukichou.nekonyume.cat.MeowDanQuality;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

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

        assertEquals(
                0.03,
                config.getMumaNight().getXpPillDropChance(),
                1e-9
        );

        assertEquals(
                0.01,
                config.getMumaNight().getEliteXpPillDropChance(),
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
         * 概率钳到 [0,1]。
         */
        assertEquals(
                1.0,
                config.getMumaNight().getXpPillDropChance(),
                1e-9
        );

        assertEquals(
                0.0,
                config.getMumaNight().getEliteXpPillDropChance(),
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
                        affection:
                          feed-base: 999
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
                100,
                config.getAffection().getFeedBase()
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
}
