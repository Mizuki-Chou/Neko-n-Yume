package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatMoodTest {

    private static final long DAY =
            24L * 60 * 60 * 1000;

    private static final UUID CAT_ID =
            UUID.fromString(
                    "8441445b-2aeb-45bd-a1f1-bde96df6d1eb"
            );

    private static final UUID OWNER_ID =
            UUID.fromString(
                    "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            );

    private Cat buildCat(
            long now,
            int affection,
            int hunger,
            int health,
            long lastFedAt,
            long lastInteractionAt
    ) {

        return new Cat(
                CAT_ID,
                OWNER_ID,
                "Mikan",
                1,
                affection,
                hunger,
                health,
                null,
                now,
                lastFedAt,
                lastInteractionAt
        );
    }

    @Test
    void ecstaticWhenEverythingIsPerfect() {

        long now =
                System.currentTimeMillis();

        Cat cat =
                buildCat(
                        now,
                        80,
                        100,
                        100,
                        now,
                        now
                );

        int bonus =
                cat.getPersonality()
                        .getMoodBonus();

        /*
         * 100 + 20(好感) + 10(刚喂食) = 130
         */
        assertEquals(
                130 + bonus,
                CatMood.calculateScore(
                        cat,
                        now
                )
        );
    }

    @Test
    void calmWhenIdleAndMediumState() {

        long now =
                System.currentTimeMillis();

        Cat cat =
                buildCat(
                        now,
                        50,
                        40,
                        100,
                        now - 10 * DAY,
                        now - 10 * DAY
                );

        int bonus =
                cat.getPersonality()
                        .getMoodBonus();

        /*
         * 100 - 10(饥饿<=40) + 10(好感>=50) - 30(>72h无互动) = 70
         */
        assertEquals(
                70 + bonus,
                CatMood.calculateScore(
                        cat,
                        now
                )
        );
    }

    @Test
    void lowWhenHungryAndDistant() {

        long now =
                System.currentTimeMillis();

        Cat cat =
                buildCat(
                        now,
                        10,
                        10,
                        50,
                        now - 2 * DAY,
                        now - 2 * DAY
                );

        int bonus =
                cat.getPersonality()
                        .getMoodBonus();

        /*
         * 100 - 20(饥饿<=20) - 20(好感<=10) - 10(>24h无互动) = 50
         */
        assertEquals(
                50 + bonus,
                CatMood.calculateScore(
                        cat,
                        now
                )
        );
    }

    @Test
    void sadWhenNeglected() {

        long now =
                System.currentTimeMillis();

        Cat cat =
                buildCat(
                        now,
                        0,
                        0,
                        0,
                        now - 10 * DAY,
                        now - 10 * DAY
                );

        int bonus =
                cat.getPersonality()
                        .getMoodBonus();

        /*
         * 100 - 50(饥饿=0) - 20(好感<=10) - 30(健康<=30) - 30(>72h) = -30
         */
        assertEquals(
                -30 + bonus,
                CatMood.calculateScore(
                        cat,
                        now
                )
        );
    }

    /*
     * ============================================================
     * 得分边界映射
     * ============================================================
     */

    @Test
    void scoreBoundariesMapToMoods() {

        assertEquals(
                CatMood.ECSTATIC,
                CatMood.fromScore(130)
        );

        assertEquals(
                CatMood.HAPPY,
                CatMood.fromScore(100)
        );

        assertEquals(
                CatMood.CALM,
                CatMood.fromScore(70)
        );

        assertEquals(
                CatMood.LOW,
                CatMood.fromScore(40)
        );

        assertEquals(
                CatMood.SAD,
                CatMood.fromScore(39)
        );

        assertEquals(
                CatMood.SAD,
                CatMood.fromScore(
                        Integer.MIN_VALUE
                )
        );

        assertEquals(
                CatMood.ECSTATIC,
                CatMood.fromScore(
                        Integer.MAX_VALUE
                )
        );
    }

    @Test
    void nullCatScoresBaseline() {

        assertEquals(
                100,
                CatMood.calculateScore(
                        null,
                        System.currentTimeMillis()
                )
        );
    }
}
