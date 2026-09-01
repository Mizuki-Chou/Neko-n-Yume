package mizukichou.nekonyume.achievement;

import mizukichou.nekonyume.testutil.PipelineHarness;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.8.4 R17（社区上报）：
 * 成就奖励逐币种幂等协议回归测试。
 *
 * <p>
 * 验证"先记台账后发奖励"时代的两类失败：
 * 1. 台账已记 + pending 尚存 → 补发跳过 → 奖励永久丢失；
 * 2. 补发时币种已发放（标记在）→ 再次发放 → 重复奖励。
 * </p>
 */
class AchievementRewardIdempotencyTest {

    private static final CatAchievement ACHIEVEMENT =
            CatAchievement.COMPANION_DAYS_100;

    @Test
    void partialApplicationDoesNotGrantTwice() {

        PipelineHarness h =
                PipelineHarness.create();

        h.createLogicalCat();

        UUID playerUuid =
                h.player.getUniqueId();

        /*
         * 模拟"崩溃于发放后、台账落盘前"的现场：
         * pending 尚存、喵力已发放标记在、台账未记。
         * 补发必须跳过已发放币种——绝不重复。
         */
        h.store.addAchievementPending(
                playerUuid,
                ACHIEVEMENT.name()
        );

        h.store.addAchievementRewardMeowApplied(
                playerUuid,
                ACHIEVEMENT.name()
        );

        int meowBefore =
                h.store.getCatMeowPower(
                        playerUuid
                );

        h.achievementService.checkAll(
                h.player
        );

        /*
         * 已发放币种绝不重发：喵力分毫不动。
         */
        assertEquals(
                meowBefore,
                h.store.getCatMeowPower(
                        playerUuid
                ),
                "已发放币种不得重复发放"
        );

        /*
         * 台账补齐、pending 清空、逐币种标记清理。
         */
        assertTrue(
                h.store.isAchievementRewarded(
                        playerUuid,
                        ACHIEVEMENT.name()
                ),
                "补发完成后台账必须已记"
        );

        assertTrue(
                h.store.getAchievementsPendingList(
                        playerUuid
                ).isEmpty(),
                "补发完成后 pending 必须清空"
        );

        assertFalse(
                h.store.isAchievementRewardMeowApplied(
                        playerUuid,
                        ACHIEVEMENT.name()
                ),
                "补发完成后逐币种标记必须清理"
        );
    }

    @Test
    void interruptedGrantIsRetriedInsteadOfLost() {

        PipelineHarness h =
                PipelineHarness.create();

        h.createLogicalCat();

        UUID playerUuid =
                h.player.getUniqueId();

        /*
         * 模拟"发放环节异常中断"的现场：
         * pending 尚存、无任何已发放标记、台账未记。
         * 补发必须把奖励实际发出去——绝不永久少发。
         */
        h.store.addAchievementPending(
                playerUuid,
                ACHIEVEMENT.name()
        );

        int meowBefore =
                h.store.getCatMeowPower(
                        playerUuid
                );

        h.achievementService.checkAll(
                h.player
        );

        assertTrue(
                h.store.getCatMeowPower(
                        playerUuid
                ) > meowBefore,
                "未发放的奖励必须实际补发（不再永久少发）"
        );

        assertTrue(
                h.store.isAchievementRewarded(
                        playerUuid,
                        ACHIEVEMENT.name()
                ),
                "补发完成后台账必须已记"
        );

        assertTrue(
                h.store.getAchievementsPendingList(
                        playerUuid
                ).isEmpty(),
                "补发完成后 pending 必须清空"
        );
    }
}
