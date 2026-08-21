package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.config.ConfigSnapshot;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 0.8.0 羁绊纪元纯数值核心：
 * 心情 × 羁绊的伤害/经验/冷却/治疗倍率组合与钳制。
 */
class CareMathTest {

    private static final UUID CAT_ID =
            UUID.fromString(
                    "8441445b-2aeb-45bd-a1f1-bde96df6d1eb"
            );

    private static final UUID OWNER_ID =
            UUID.fromString(
                    "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            );

    /*
     * 与 config 默认一致的 Care。
     */
    private ConfigSnapshot.Care defaultCare() {

        return new ConfigSnapshot.Care(
                moodTable(
                        15,
                        8,
                        0,
                        -10,
                        -20
                ),
                moodTable(
                        10,
                        5,
                        0,
                        -10,
                        -20
                ),
                2,
                8,
                2,
                20,
                0,
                20,
                List.of(
                        20,
                        40,
                        60,
                        80,
                        100
                ),
                List.of(
                        0,
                        0,
                        5,
                        10,
                        10,
                        10
                ),
                List.of(
                        0,
                        0,
                        0,
                        5,
                        5,
                        5
                ),
                List.of(
                        0,
                        0,
                        0,
                        0,
                        0,
                        10
                ),
                10,
                5,
                180
        );
    }

    private Map<CatMood, Double> moodTable(
            double ecstatic,
            double happy,
            double calm,
            double low,
            double sad
    ) {

        Map<CatMood, Double> table =
                new EnumMap<>(
                        CatMood.class
                );

        table.put(
                CatMood.ECSTATIC,
                ecstatic
        );

        table.put(
                CatMood.HAPPY,
                happy
        );

        table.put(
                CatMood.CALM,
                calm
        );

        table.put(
                CatMood.LOW,
                low
        );

        table.put(
                CatMood.SAD,
                sad
        );

        return table;
    }

    private Cat catWithAffection(
            int affection
    ) {

        return new Cat(
                CAT_ID,
                OWNER_ID,
                "Mikan",
                1,
                affection,
                100,
                100,
                null,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );
    }

    /*
     * ============================================================
     * 战斗伤害倍率
     * ============================================================
     */

    @Test
    void battleDamageNeutral() {

        assertEquals(
                1.0,
                CareMath.battleDamageMultiplier(
                        CatMood.CALM,
                        BondTier.FRIEND,
                        defaultCare()
                ),
                1e-9
        );
    }

    @Test
    void battleDamageHappyAndSoulmate() {

        assertEquals(
                1.25,
                CareMath.battleDamageMultiplier(
                        CatMood.ECSTATIC,
                        BondTier.SOULMATE,
                        defaultCare()
                ),
                1e-9
        );
    }

    @Test
    void battleDamageSadAndStranger() {

        assertEquals(
                0.8,
                CareMath.battleDamageMultiplier(
                        CatMood.SAD,
                        BondTier.STRANGER,
                        defaultCare()
                ),
                1e-9
        );
    }

    @Test
    void battleDamageClampedToFloor() {

        ConfigSnapshot.Care care =
                new ConfigSnapshot.Care(
                        moodTable(
                                0,
                                0,
                                0,
                                0,
                                -100
                        ),
                        moodTable(
                                0,
                                0,
                                0,
                                0,
                                0
                        ),
                        2,
                        8,
                        2,
                        20,
                        0,
                        20,
                        List.of(
                                20,
                                40,
                                60,
                                80,
                                100
                        ),
                        List.of(
                                0,
                                0,
                                0,
                                0,
                                0,
                                0
                        ),
                        List.of(
                                0,
                                0,
                                0,
                                0,
                                0,
                                0
                        ),
                        List.of(
                                0,
                                0,
                                0,
                                0,
                                0,
                                0
                        ),
                        10,
                        5,
                        180
                );

        assertEquals(
                0.5,
                CareMath.battleDamageMultiplier(
                        CatMood.SAD,
                        BondTier.STRANGER,
                        care
                ),
                1e-9
        );
    }

    /*
     * ============================================================
     * 经验倍率
     * ============================================================
     */

    @Test
    void experienceMultiplierCombination() {

        assertEquals(
                1.2,
                CareMath.experienceMultiplier(
                        CatMood.ECSTATIC,
                        BondTier.TRUSTED,
                        defaultCare()
                ),
                1e-9
        );

        assertEquals(
                0.95,
                CareMath.experienceMultiplier(
                        CatMood.LOW,
                        BondTier.FRIEND,
                        defaultCare()
                ),
                1e-9
        );
    }

    /*
     * ============================================================
     * 冷却系数
     * ============================================================
     */

    @Test
    void cooldownFactorByBond() {

        assertEquals(
                1.0,
                CareMath.cooldownFactor(
                        BondTier.STRANGER,
                        defaultCare()
                ),
                1e-9
        );

        assertEquals(
                0.95,
                CareMath.cooldownFactor(
                        BondTier.INTIMATE,
                        defaultCare()
                ),
                1e-9
        );

        /*
         * 0.8.0 羁绊冷却表 [0,0,0,5,5,5]：
         * 灵魂档减免 5% → 0.95。
         */
        assertEquals(
                0.95,
                CareMath.cooldownFactor(
                        BondTier.SOULMATE,
                        defaultCare()
                ),
                1e-9
        );
    }

    /*
     * ============================================================
     * 应用函数（保底语义）
     * ============================================================
     */

    @Test
    void applyExperienceNeverBelowOne() {

        assertEquals(
                0,
                CareMath.applyExperience(
                        0,
                        1.5
                )
        );

        assertEquals(
                1,
                CareMath.applyExperience(
                        1,
                        0.5
                )
        );

        assertEquals(
                6,
                CareMath.applyExperience(
                        5,
                        1.2
                )
        );
    }

    @Test
    void applyDamageNeverBelowOne() {

        assertEquals(
                1,
                CareMath.applyDamage(
                        10,
                        0.05
                )
        );

        assertEquals(
                13,
                CareMath.applyDamage(
                        10,
                        1.25
                )
        );
    }

    /*
     * ============================================================
     * 羁绊推导
     * ============================================================
     */

    @Test
    void bondForUsesAffectionAndThresholds() {

        assertEquals(
                BondTier.TRUSTED,
                CareMath.bondFor(
                        catWithAffection(
                                80
                        ),
                        defaultCare()
                )
        );

        assertEquals(
                BondTier.SOULMATE,
                CareMath.bondFor(
                        catWithAffection(
                                100
                        ),
                        defaultCare()
                )
        );

        assertEquals(
                BondTier.FRIEND,
                CareMath.bondFor(
                        null,
                        defaultCare()
                )
        );

        assertEquals(
                BondTier.FRIEND,
                CareMath.bondFor(
                        catWithAffection(
                                50
                        ),
                        null
                )
        );
    }

    /*
     * ============================================================
     * 装备冷却减免（0.8.0）
     * ============================================================
     */

    @Test
    void cooldownReductionZeroKeepsFactor() {

        assertEquals(
                1.0,
                CareMath.applyCooldownReduction(
                        1.0,
                        0
                ),
                1e-9
        );

        assertEquals(
                0.7,
                CareMath.applyCooldownReduction(
                        0.7,
                        -5
                ),
                1e-9
        );
    }

    @Test
    void cooldownReductionScalesDown() {

        assertEquals(
                0.8,
                CareMath.applyCooldownReduction(
                        1.0,
                        20
                ),
                1e-9
        );

        /*
         * 乘法语义：0.9 × (1 - 10%) = 0.81。
         */
        assertEquals(
                0.81,
                CareMath.applyCooldownReduction(
                        0.9,
                        10
                ),
                1e-9
        );
    }

    @Test
    void cooldownReductionNeverBelowFloor() {

        assertEquals(
                0.5,
                CareMath.applyCooldownReduction(
                        0.5,
                        20
                ),
                1e-9
        );

        assertEquals(
                0.5,
                CareMath.applyCooldownReduction(
                        1.0,
                        90
                ),
                1e-9
        );
    }

    @Test
    void cooldownReductionInvalidFactorFallsBack() {

        assertEquals(
                1.0,
                CareMath.applyCooldownReduction(
                        Double.NaN,
                        20
                ),
                1e-9
        );

        assertEquals(
                1.0,
                CareMath.applyCooldownReduction(
                        0.0,
                        20
                ),
                1e-9
        );
    }
}
