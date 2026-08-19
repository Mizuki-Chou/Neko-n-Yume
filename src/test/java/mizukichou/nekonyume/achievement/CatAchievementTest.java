package mizukichou.nekonyume.achievement;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatAchievementTest {

    @Test
    void everyAchievementHasCompleteMetadata() {

        for (CatAchievement achievement :
                CatAchievement.values()) {

            assertNotNull(
                    achievement.getDisplayName()
            );

            assertFalse(
                    achievement.getDisplayName()
                            .isBlank()
            );

            assertNotNull(
                    achievement.getDescription()
            );

            assertFalse(
                    achievement.getDescription()
                            .isBlank()
            );

            assertNotNull(
                    achievement.getIcon()
            );

            assertTrue(
                    achievement.getThreshold() > 0
            );

            if (achievement.isCounterBased()) {

                assertNotNull(
                        achievement.getCounterKey()
                );

                assertFalse(
                        achievement.getCounterKey()
                                .isBlank()
                );

            } else {

                assertNull(
                        achievement.getCounterKey()
                );
            }
        }
    }

    @Test
    void meowPowerRewardsMatchDesign() {

        assertEquals(
                50,
                CatAchievement.COMPANION_DAYS_100
                        .getDefaultRewardMeowPower()
        );

        assertEquals(
                10,
                CatAchievement.PET_TOTAL_200
                        .getDefaultRewardMeowPower()
        );

        assertEquals(
                10,
                CatAchievement.FEED_TOTAL_100
                        .getDefaultRewardMeowPower()
        );

        assertEquals(
                30,
                CatAchievement.LEVEL_60
                        .getDefaultRewardMeowPower()
        );

        assertEquals(
                30,
                CatAchievement.MEOW_RANK_30
                        .getDefaultRewardMeowPower()
        );

        assertEquals(
                30,
                CatAchievement.SKILL_REFRESH_10
                        .getDefaultRewardMeowPower()
        );

        assertEquals(
                5,
                CatAchievement.MONSTER_KILL_50
                        .getDefaultRewardMeowPower()
        );

        /*
         * 奖励二选一：要么纯经验，要么纯喵力，
         * 且每个成就必有奖励。
         */
        for (CatAchievement achievement :
                CatAchievement.values()) {

            boolean meowReward =
                    achievement.getDefaultRewardMeowPower()
                            > 0;

            boolean xpReward =
                    achievement.getDefaultRewardXp()
                            > 0;

            assertFalse(
                    meowReward && xpReward,
                    achievement
                            + " should have a single reward type"
            );

            assertTrue(
                    meowReward || xpReward,
                    achievement + " should have a reward"
            );
        }
    }

    @Test
    void sharedCounterKeysHaveIncreasingThresholds() {

        /*
         * 同一计数器的阶梯成就（如 10 次 / 100 次）
         * 共享同一个进度键，且阈值严格递增。
         */
        Map<String, List<CatAchievement>> byKey =
                new HashMap<>();

        for (CatAchievement achievement :
                CatAchievement.values()) {

            if (achievement.isCounterBased()) {

                byKey.computeIfAbsent(
                                achievement.getCounterKey(),
                                key -> new ArrayList<>()
                        )
                        .add(achievement);
            }
        }

        for (Map.Entry<String, List<CatAchievement>> entry :
                byKey.entrySet()) {

            List<CatAchievement> achievements =
                    entry.getValue();

            assertFalse(
                    achievements.isEmpty(),
                    entry.getKey()
                            + " should have achievements"
            );

            for (int i = 1;
                 i < achievements.size();
                 i++) {

                assertTrue(
                        achievements.get(i - 1)
                                .getThreshold()
                                < achievements.get(i)
                                .getThreshold(),
                        entry.getKey()
                                + " thresholds must increase"
                );
            }
        }
    }

    @Test
    void thresholdsAreAsDesigned() {

        assertEquals(
                1,
                CatAchievement.FIRST_CLAIM
                        .getThreshold()
        );

        assertEquals(
                100,
                CatAchievement.COMPANION_DAYS_100
                        .getThreshold()
        );

        assertEquals(
                200,
                CatAchievement.PET_TOTAL_200
                        .getThreshold()
        );

        assertEquals(
                100,
                CatAchievement.FEED_TOTAL_100
                        .getThreshold()
        );

        assertEquals(
                60,
                CatAchievement.LEVEL_60
                        .getThreshold()
        );

        assertEquals(
                30,
                CatAchievement.MEOW_RANK_30
                        .getThreshold()
        );

        assertEquals(
                50,
                CatAchievement.MONSTER_KILL_50
                        .getThreshold()
        );
    }

    @Test
    void configIdsAreKebabCase() {

        assertEquals(
                "first-claim",
                CatAchievement.FIRST_CLAIM
                        .getConfigId()
        );

        assertEquals(
                "companion-days-100",
                CatAchievement.COMPANION_DAYS_100
                        .getConfigId()
        );

        assertEquals(
                "monster-kill-50",
                CatAchievement.MONSTER_KILL_50
                        .getConfigId()
        );
    }
}
