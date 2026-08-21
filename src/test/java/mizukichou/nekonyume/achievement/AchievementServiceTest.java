package mizukichou.nekonyume.achievement;

import mizukichou.nekonyume.cat.Cat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementServiceTest {

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
            int level,
            int meowRank,
            long createdAt
    ) {

        Cat cat =
                new Cat(
                        CAT_ID,
                        OWNER_ID,
                        "Mikan",
                        level,
                        80,
                        100,
                        100,
                        null,
                        createdAt,
                        now,
                        now
                );

        cat.setMeowRank(
                meowRank
        );

        return cat;
    }

    @Test
    void derivedAchievementsEvaluateFromCatState() {

        long now =
                System.currentTimeMillis();

        Cat cat =
                buildCat(
                        now,
                        10,
                        5,
                        now - 3 * DAY
                );

        List<CatAchievement> newly =
                AchievementService.evaluateUnlocks(
                        Set.of(),
                        cat,
                        now,
                        key -> 0
                );

        assertTrue(
                newly.contains(
                        CatAchievement.FIRST_CLAIM
                )
        );

        assertTrue(
                newly.contains(
                        CatAchievement.COMPANION_DAYS_3
                )
        );

        assertTrue(
                newly.contains(
                        CatAchievement.LEVEL_10
                )
        );

        assertTrue(
                newly.contains(
                        CatAchievement.MEOW_RANK_5
                )
        );

        assertFalse(
                newly.contains(
                        CatAchievement.COMPANION_DAYS_100
                )
        );

        assertFalse(
                newly.contains(
                        CatAchievement.LEVEL_60
                )
        );

        assertFalse(
                newly.contains(
                        CatAchievement.MEOW_RANK_30
                )
        );

        /*
         * 羁绊纪元（0.8.0）：好感 80 → 亲密无间已达标，灵魂羁绊未达标。
         */
        assertTrue(
                newly.contains(
                        CatAchievement.AFFECTION_60
                )
        );

        assertFalse(
                newly.contains(
                        CatAchievement.AFFECTION_100
                )
        );
    }

    @Test
    void countersUnlockExactlyAtThreshold() {

        long now =
                System.currentTimeMillis();

        Cat cat =
                buildCat(
                        now,
                        1,
                        0,
                        now
                );

        List<CatAchievement> newly =
                AchievementService.evaluateUnlocks(
                        Set.of(),
                        cat,
                        now,
                        key -> switch (key) {

                            case CatAchievement.KEY_FEED -> 10;

                            case CatAchievement.KEY_PET -> 199;

                            case CatAchievement.KEY_MONSTER_KILL -> 49;

                            default -> 0;
                        }
                );

        assertTrue(
                newly.contains(
                        CatAchievement.FEED_TOTAL_10
                )
        );

        assertFalse(
                newly.contains(
                        CatAchievement.FEED_TOTAL_100
                )
        );

        assertFalse(
                newly.contains(
                        CatAchievement.PET_TOTAL_200
                )
        );

        assertFalse(
                newly.contains(
                        CatAchievement.MONSTER_KILL_50
                )
        );
    }

    @Test
    void alreadyUnlockedAchievementsAreExcluded() {

        long now =
                System.currentTimeMillis();

        Cat cat =
                buildCat(
                        now,
                        10,
                        5,
                        now - 3 * DAY
                );

        List<CatAchievement> newly =
                AchievementService.evaluateUnlocks(
                        Set.of(
                                CatAchievement.FIRST_CLAIM
                                        .name()
                        ),
                        cat,
                        now,
                        key -> 0
                );

        assertFalse(
                newly.contains(
                        CatAchievement.FIRST_CLAIM
                )
        );

        assertTrue(
                newly.contains(
                        CatAchievement.LEVEL_10
                )
        );
    }

    @Test
    void nullCatProducesNoUnlocks() {

        assertTrue(
                AchievementService.evaluateUnlocks(
                        Set.of(),
                        null,
                        System.currentTimeMillis(),
                        key -> 999
                ).isEmpty()
        );
    }

    @Test
    void valueOfReadsMetrics() {

        long now =
                System.currentTimeMillis();

        Cat cat =
                buildCat(
                        now,
                        12,
                        7,
                        now - 5 * DAY
                );

        assertEquals(
                1,
                AchievementService.valueOf(
                        CatAchievement.FIRST_CLAIM,
                        cat,
                        now,
                        key -> 0
                )
        );

        /*
         * 5 天前创建 → 陪伴第 6 天。
         */
        assertEquals(
                6,
                AchievementService.valueOf(
                        CatAchievement.COMPANION_DAYS_3,
                        cat,
                        now,
                        key -> 0
                )
        );

        assertEquals(
                12,
                AchievementService.valueOf(
                        CatAchievement.LEVEL_10,
                        cat,
                        now,
                        key -> 0
                )
        );

        assertEquals(
                7,
                AchievementService.valueOf(
                        CatAchievement.MEOW_RANK_5,
                        cat,
                        now,
                        key -> 0
                )
        );

        assertEquals(
                42,
                AchievementService.valueOf(
                        CatAchievement.FEED_TOTAL_10,
                        cat,
                        now,
                        key -> 42
                )
        );

        /*
         * 羁绊纪元（0.8.0）：好感度直接从 Cat 状态读取。
         */
        assertEquals(
                80,
                AchievementService.valueOf(
                        CatAchievement.AFFECTION_60,
                        cat,
                        now,
                        key -> 0
                )
        );
    }
}
