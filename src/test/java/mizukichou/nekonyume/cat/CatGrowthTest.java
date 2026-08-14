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

}
