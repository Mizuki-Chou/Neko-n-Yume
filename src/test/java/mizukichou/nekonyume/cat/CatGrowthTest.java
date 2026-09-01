package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatGrowthTest {

    private static final UUID CAT_ID =
            UUID.fromString(
                    "8441445b-2aeb-45bd-a1f1-bde96df6d1eb"
            );

    private static final UUID OWNER_ID =
            UUID.fromString(
                    "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            );

    private Cat newCat() {

        return new Cat(
                CAT_ID,
                OWNER_ID,
                "Mikan",
                1,
                50,
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
     * 经验 / 等级
     * ============================================================
     */

    @Test
    void defaultCurveReachesLevel2At100() {

        Cat cat = newCat();

        assertEquals(
                1,
                cat.addExperience(100)
        );

        assertEquals(
                2,
                cat.getLevel()
        );
    }

    @Test
    void belowThresholdDoesNotLevelUp() {

        Cat cat = newCat();

        assertEquals(
                0,
                cat.addExperience(99)
        );

        assertEquals(
                1,
                cat.getLevel()
        );
    }

    @Test
    void level3Requires300Total() {

        Cat cat = newCat();

        /*
         * 升 2 级只需 100 经验，
         * 299 经验时已是 2 级。
         */
        cat.addExperience(299);

        assertEquals(
                2,
                cat.getLevel()
        );

        /*
         * 300 经验达到 3 级。
         */
        assertEquals(
                1,
                cat.addExperience(1)
        );

        assertEquals(
                3,
                cat.getLevel()
        );
    }


    @Test
    void customCurveBaseIsRespected() {

        Cat cat = newCat();

        /*
         * base = 200：
         * cumXp(2) = 200
         */
        assertEquals(
                0,
                cat.addExperience(199, 200)
        );

        assertEquals(
                1,
                cat.addExperience(1, 200)
        );
    }

    @Test
    void negativeExperienceIsIgnored() {

        Cat cat = newCat();

        assertEquals(
                0,
                cat.addExperience(-50)
        );

        assertEquals(
                0,
                cat.getExperience()
        );
    }

    /*
     * ============================================================
     * 喵力 / 喵阶
     * ============================================================
     */

    @Test
    void meowRankStartsAtZero() {

        Cat cat = newCat();

        assertEquals(
                0,
                cat.getMeowRank()
        );
    }

    @Test
    void meowRank1Requires10Power() {

        Cat cat = newCat();

        assertEquals(
                0,
                cat.addMeowPower(9)
        );

        assertEquals(
                1,
                cat.addMeowPower(1)
        );

        assertEquals(
                1,
                cat.getMeowRank()
        );
    }

    @Test
    void meowRank2Requires21Power() {

        Cat cat = newCat();

        cat.addMeowPower(20);

        assertEquals(
                1,
                cat.getMeowRank()
        );

        cat.addMeowPower(1);

        assertEquals(
                2,
                cat.getMeowRank()
        );
    }

    @Test
    void customMeowCurveOffsetIsRespected() {

        Cat cat = newCat();

        /*
         * offset = 10：
         * 升 1 阶需要 (1 × (1+10)) / 2 = 5 点（整数除法）
         */
        assertEquals(
                0,
                cat.addMeowPower(4, 10)
        );

        assertEquals(
                1,
                cat.addMeowPower(1, 10)
        );
    }


    /*
     * ============================================================
     * 默认值 / 钳制
     * ============================================================
     */

    @Test
    void defaultsAreAppliedForNewCat() {

        Cat cat = newCat();

        assertEquals(1, cat.getLevel());
        assertEquals(50, cat.getAffection());
        assertEquals(100, cat.getHunger());
        assertEquals(100, cat.getHealth());
        assertEquals(0, cat.getExperience());
        assertEquals(0, cat.getMeowPower());
        assertEquals(0, cat.getMeowRank());
        assertEquals(CatBehaviorMode.FOLLOW, cat.getBehaviorMode());
    }

    @Test
    void valuesAreClampedTo0To100() {

        Cat cat = newCat();

        cat.setHunger(500);
        cat.setAffection(-30);
        cat.setHealth(9999);

        assertEquals(100, cat.getHunger());
        assertEquals(0, cat.getAffection());
        assertEquals(100, cat.getHealth());
    }

    @Test
    void levelCanNeverGoBelowOne() {

        Cat cat = newCat();

        cat.setLevel(-5);

        assertEquals(
                1,
                cat.getLevel()
        );
    }

    @Test
    void personalityIsDeterministic() {

        Cat a = newCat();
        Cat b = newCat();

        assertEquals(
                a.getPersonality(),
                b.getPersonality()
        );

        assertTrue(
                a.getPersonality()
                        .getHungerRate() > 0
        );
    }


    private static final long TEST_DAY =
            24L * 60 * 60 * 1000;

    /*
     * ============================================================
     * 实体最大生命（0.8.1 统一公式）
     * ============================================================
     */

    @Test
    void entityMaxHealthBaseFormula() {

        Cat cat = newCat();

        /*
         * 等级 1、无装备：10 + 1/4 = 10.25。
         */
        assertEquals(
                10.25,
                cat.entityMaxHealth(),
                0.0001
        );
    }

    @Test
    void entityMaxHealthIncludesLevelGrowth() {

        Cat cat = newCat();

        cat.setLevel(41);

        /*
         * 10 + 41/4 = 20.25。
         */
        assertEquals(
                20.25,
                cat.entityMaxHealth(),
                0.0001
        );
    }

    @Test
    void entityMaxHealthIncludesEquipBonus() {

        /*
         * 0.8.1 回归（P1）：装备生命加成必须计入。
         * 至极项圈：生命 +30。
         */
        Cat cat = newCat();

        cat.setEquippedItem(
                CatEquipItem.COLLAR_LEGENDARY
        );

        assertEquals(
                10.25 + 30.0,
                cat.entityMaxHealth(),
                0.0001
        );
    }

    @Test
    void entityMaxHealthIgnoresNullEquip() {

        Cat cat = newCat();

        cat.setEquippedItem(
                null
        );

        assertEquals(
                10.25,
                cat.entityMaxHealth(),
                0.0001
        );
    }

    @Test
    void companionDaysCountsFromCreationInclusive() {

        long now =
                System.currentTimeMillis();

        Cat fresh =
                new Cat(
                        CAT_ID,
                        OWNER_ID,
                        "Mikan",
                        1,
                        50,
                        100,
                        100,
                        null,
                        now,
                        now,
                        now
                );

        assertEquals(
                1,
                fresh.getCompanionDays(now)
        );

        Cat old =
                new Cat(
                        CAT_ID,
                        OWNER_ID,
                        "Mikan",
                        1,
                        50,
                        100,
                        100,
                        null,
                        now - 5 * TEST_DAY,
                        now,
                        now
                );

        assertEquals(
                6,
                old.getCompanionDays(now)
        );
    }

    @Test
    void companionDaysNeverBelowOne() {

        long now =
                System.currentTimeMillis();

        Cat future =
                new Cat(
                        CAT_ID,
                        OWNER_ID,
                        "Mikan",
                        1,
                        50,
                        100,
                        100,
                        null,
                        now + 10 * TEST_DAY,
                        now,
                        now
                );

        assertEquals(
                1,
                future.getCompanionDays(now)
        );
    }

    @Test
    void companionDaysNeverOverflowsInt() {

        /*
         * 极端时间差（时钟回拨 / 损坏数据）：
         * 天数超出 int 范围时必须钳制在
         * Integer.MAX_VALUE，绝不溢出成负数。
         */
        Cat ancient =
                new Cat(
                        CAT_ID,
                        OWNER_ID,
                        "Mikan",
                        1,
                        50,
                        100,
                        100,
                        null,
                        1L,
                        System.currentTimeMillis(),
                        System.currentTimeMillis()
                );

        int days =
                ancient.getCompanionDays(
                        Long.MAX_VALUE
                );

        assertTrue(
                days >= 1,
                "companion days must never be negative: "
                        + days
        );

        assertEquals(
                Integer.MAX_VALUE,
                days
        );
    }

    /*
     * 0.8.1 R8（效率）：
     * 二分版 levelFromExperience / meowRankFromPower 与旧线性实现
     * 在广泛取值与边界上逐值等价（测试内保留线性参照实现）。
     */
    @Test
    void binarySearchMatchesLegacyLinearScan() {

        int[] bases = {1, 2, 3, 7, 100, 999};

        int[] offsets = {1, 2, 19, 100};

        for (int base : bases) {

            int[] experienceSamples = {
                    0,
                    1,
                    base - 1,
                    base,
                    base + 1,
                    base * 10,
                    base * 100,
                    base * 1000,
                    base * 10000,
                    base * 500000,
                    Integer.MAX_VALUE
            };

            for (int e : experienceSamples) {

                assertEquals(
                        legacyLevelFromExperience(
                                e,
                                base
                        ),
                        GrowthMath.levelFromExperience(
                                e,
                                base
                        ),
                        "level mismatch for exp="
                                + e
                                + " base="
                                + base
                );
            }
        }

        for (int offset : offsets) {

            int[] powerSamples = {
                    0,
                    1,
                    offset,
                    offset + 1,
                    offset * 10,
                    offset * 100,
                    offset * 1000,
                    offset * 10000,
                    offset * 500000,
                    Integer.MAX_VALUE
            };

            for (int p : powerSamples) {

                assertEquals(
                        legacyMeowRankFromPower(
                                p,
                                offset
                        ),
                        GrowthMath.meowRankFromPower(
                                p,
                                offset
                        ),
                        "rank mismatch for power="
                                + p
                                + " offset="
                                + offset
                );
            }
        }
    }

    private int legacyLevelFromExperience(
            int totalExperience,
            int curveBase
    ) {

        int base =
                GrowthMath.normalizeXpCurveBase(
                        curveBase
                );

        int level = 1;

        int safety = 0;

        while (level < 10000 &&
                safety < 10000) {

            long nextLevelRequired =
                    (long) base
                            * (level + 1L)
                            * level
                            / 2;

            if (totalExperience < nextLevelRequired) {
                break;
            }

            level++;
            safety++;
        }

        return level;
    }

    private int legacyMeowRankFromPower(
            int totalMeowPower,
            int curveOffset
    ) {

        int offset =
                GrowthMath.normalizeMeowCurveOffset(
                        curveOffset
                );

        int rank = 0;

        int safety = 0;

        while (rank < 10000 &&
                safety < 10000) {

            long nextRankRequired =
                    (long) (rank + 1)
                            * (rank + 1 + offset)
                            / 2;

            if (totalMeowPower < nextRankRequired) {
                break;
            }

            rank++;
            safety++;
        }

        return rank;
    }


    @Test
    void setLevelClampsToDomainContract() {

        /*
         * 0.8.4 R21（社区上报 L-NEW-09/10）：
         * 等级必须被钳制到 [1, MAX_LEVEL]——
         * 损坏数据既不能抬到 10000 以上，也不能溢出为负。
         */
        Cat cat = newCat();

        cat.setLevel(
                GrowthMath.MAX_LEVEL
                        + 500_000
        );

        assertEquals(
                GrowthMath.MAX_LEVEL,
                cat.getLevel()
        );
    }

    @Test
    void addLevelSaturatesInsteadOfWrapping() {

        Cat cat = newCat();

        cat.setLevel(
                Integer.MAX_VALUE
                        - 10
        );

        cat.addLevel(
                100
        );

        assertEquals(
                GrowthMath.MAX_LEVEL,
                cat.getLevel()
        );

        Cat low = newCat();

        low.setLevel(
                2
        );

        low.addLevel(
                -100
        );

        assertEquals(
                1,
                low.getLevel()
        );
    }

}
