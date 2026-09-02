package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.config.ConfigSnapshot;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void extremeExperienceSaturatesInsteadOfWrapping() {

        /*
         * 0.8.4 R21（社区上报 M-NEW-06）：
         * 20 亿经验 × 3.0 倍率若按 int 计算会溢出为负，
         * 再被保底 1 兜成 1 XP——必须饱和到上限。
         */
        assertEquals(
                Integer.MAX_VALUE,
                CareMath.applyExperience(
                        2_000_000_000,
                        3.0
                )
        );
    }

    @Test
    void extremeDamageSaturatesInsteadOfWrapping() {

        /*
         * 0.8.4 R21（社区上报 M-NEW-07）。
         */
        assertEquals(
                Integer.MAX_VALUE,
                CareMath.applyDamage(
                        Integer.MAX_VALUE,
                        3.0
                )
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


    @Test
    void applyExperienceSaturationMatrix() {
        assertEquals(0, CareMath.applyExperience(0, 1.0));
        assertEquals(0, CareMath.applyExperience(-10, 1.0));
        assertEquals(0, CareMath.applyExperience(Integer.MIN_VALUE, 1.0));
        assertEquals(50, CareMath.applyExperience(100, -5.0), "负倍率钳到 0.5");
        assertEquals(100, CareMath.applyExperience(100, 1.0));
        assertEquals(300, CareMath.applyExperience(100, 99.0), "大倍率钳到 3.0");
        assertEquals(Integer.MAX_VALUE, CareMath.applyExperience(Integer.MAX_VALUE, 3.0));
        assertEquals(Integer.MAX_VALUE, CareMath.applyExperience(1_500_000_000, 2.0));
        assertEquals(1, CareMath.applyExperience(1, 0.0));
    }

    @Test
    void applyDamageSaturationMatrix() {
        assertEquals(0, CareMath.applyDamage(0, 1.0));
        assertEquals(0, CareMath.applyDamage(-5, 1.0));
        assertEquals(1, CareMath.applyDamage(1, 0.0));
        assertEquals(Integer.MAX_VALUE, CareMath.applyDamage(Integer.MAX_VALUE, 3.0));
        assertEquals(Integer.MAX_VALUE, CareMath.applyDamage(1_200_000_000, 2.0));
    }

    @Test
    void applyCooldownReductionBoundaries() {
        assertEquals(1.0, CareMath.applyCooldownReduction(1.0, 0), 1e-9);
        assertEquals(0.5, CareMath.applyCooldownReduction(1.0, 50), 1e-9);
        assertEquals(1.0, CareMath.applyCooldownReduction(1.0, -10), 1e-9);
        assertEquals(0.5, CareMath.applyCooldownReduction(1.0, 150), 1e-9, "150 钳到 90 后结果被下限 0.5 兜住");
        assertEquals(0.7, CareMath.applyCooldownReduction(1.0, 30), 1e-9);
    }

    @Test
    void applyCooldownReductionPreservesFactorRange() {
        for (int percent = 0; percent <= 100; percent++) {
            double result = CareMath.applyCooldownReduction(2.4, percent);
            assertTrue(result >= 0.5 - 1e-9 && result <= 3.0 + 1e-9, "percent=" + percent + " -> " + result);
        }
    }

    @Test
    void applyExperienceRoundingStability() {
        assertEquals(3, CareMath.applyExperience(2, 1.5));
        assertEquals(5, CareMath.applyExperience(3, 1.5), "Math.round 半上取整");
        assertEquals(3, CareMath.applyExperience(5, 0.4), "0.4 先钳到下限 0.5，2.5 半上取整为 3");
    }

    @Test
    void applyDamageRoundingStability() {
        assertEquals(3, CareMath.applyDamage(2, 1.5));
        assertEquals(2, CareMath.applyDamage(5, 0.4), "applyDamage 不钳倍率（与 applyExperience 的钳制语义区分）");
    }

    @Test
    void multiplierClampLowerAndUpperBounds() {
        assertEquals(1.5, CareMath.applyExperience(100, 1.5) / 100.0, 1e-9);
        assertEquals(3.0, CareMath.applyExperience(100, 3.0) / 100.0, 1e-9);
        assertEquals(0.5, CareMath.applyExperience(100, 0.5) / 100.0, 1e-9);
    }

    @Test
    void healingMultiplierRequiresConfigOrFallsBack() {
        double plain = CareMath.healingMultiplier(CatMood.HAPPY, null);
        assertEquals(1.0, plain, 1e-9);
    }

    @Test
    void cooldownFactorRequiresConfigOrFallsBack() {
        double plain = CareMath.cooldownFactor(BondTier.SOULMATE, null);
        assertEquals(1.0, plain, 1e-9);
    }

}
