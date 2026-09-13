package mizukichou.nekonyume.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 跟随寻路决策器测试（0.9.0更新：传送式 → 寻路式）。
 */
class FollowControllerTest {

    @Test
    void stopWhenWithinStopDistance() {

        FollowController controller =
                new FollowController();

        FollowController.Action action =
                controller.tick(
                        2.0,
                        false
                );

        assertSame(
                FollowController.Action.STOP,
                action
        );
    }

    @Test
    void stopStaysStopAcrossRounds() {

        FollowController controller =
                new FollowController();

        controller.tick(
                2.5,
                false
        );

        FollowController.Action action =
                controller.tick(
                        2.5,
                        false
                );

        assertSame(
                FollowController.Action.STOP,
                action
        );
    }

    @Test
    void teleportImmediatelyWhenFarAway() {

        FollowController controller =
                new FollowController();

        FollowController.Action action =
                controller.tick(
                        40.0,
                        false
                );

        assertSame(
                FollowController.Action.TELEPORT,
                action
        );
    }

    @Test
    void navigateOnFirstRoundInRange() {

        FollowController controller =
                new FollowController();

        FollowController.Action action =
                controller.tick(
                        10.0,
                        false
                );

        assertSame(
                FollowController.Action.NAVIGATE,
                action
        );
    }

    @Test
    void progressKeepsNavigatingWithoutFallback() {

        FollowController controller =
                new FollowController();

        double distance = 30.0;

        for (int round = 0;
                round < 12;
                round++) {

            FollowController.Action action =
                    controller.tick(
                            distance,
                            true
                    );

            assertEquals(
                    FollowController.Action.NAVIGATE,
                    action,
                    "第 " + round + " 轮应保持寻路"
            );

            distance -= 2.0;
        }

        /*
         * 验证终点仍大于停止距离（距离递减未越界）。
         */
        assertEquals(
                6.0,
                distance,
                0.001
        );
    }

    @Test
    void noProgressLeadsToTeleportFallback() {

        FollowController controller =
                new FollowController();

        FollowController.Action action =
                FollowController.Action.NONE;

        /*
         * 前 2 轮是最少起步轮，之后连续无进展
         * 4 轮（第 5 轮）→ 兜底传送。
         */
        for (int round = 0;
                round < 5;
                round++) {

            action =
                    controller.tick(
                            15.0,
                            true
                    );
        }

        assertSame(
                FollowController.Action.TELEPORT,
                action
        );
    }

    @Test
    void failedMoveToCountsAsNoProgress() {

        FollowController controller =
                new FollowController();

        FollowController.Action action =
                FollowController.Action.NONE;

        /*
         * moveTo 一直失败（无可用路径）：
         * 与无进展同口径，第 4 轮兜底传送。
         */
        for (int round = 0;
                round < 4;
                round++) {

            action =
                    controller.tick(
                            15.0,
                            false
                    );
        }

        assertSame(
                FollowController.Action.TELEPORT,
                action
        );
    }

    @Test
    void resetAfterTeleportRestartsSession() {

        FollowController controller =
                new FollowController();

        /*
         * 走到兜底传送（第 5 轮）。
         */
        for (int round = 0;
                round < 5;
                round++) {

            controller.tick(
                    15.0,
                    true
            );
        }

        /*
         * 传送后（任务会重新进入区间）：
         * 新会话必须从寻路开始，而不是延续旧计数。
         */
        FollowController.Action action =
                controller.tick(
                        15.0,
                        false
                );

        assertSame(
                FollowController.Action.NAVIGATE,
                action
        );
    }

    @Test
    void invalidDistanceIsIgnored() {

        FollowController controller =
                new FollowController();

        assertSame(
                FollowController.Action.NONE,
                controller.tick(
                        Double.NaN,
                        false
                )
        );

        assertSame(
                FollowController.Action.NONE,
                controller.tick(
                        -1.0,
                        false
                )
        );
    }

    @Test
    void progressEpsilonToleratesJitter() {

        FollowController controller =
                new FollowController();

        double distance = 10.0;

        /*
         * 距离在容差内来回抖动（主人原地微动）：
         * 前 3 轮绝不过早兜底（起步轮 + 容差保护），
         * 之后仍无实质接近才兜底——行为由
         * noProgressLeadsToTeleportFallback 覆盖。
         */
        for (int round = 0;
                round < 3;
                round++) {

            distance =
                    round % 2 == 0
                            ? 10.0
                            : 9.8;

            assertEquals(
                    FollowController.Action.NAVIGATE,
                    controller.tick(
                            distance,
                            true
                    ),
                    "第 " + round + " 轮"
            );
        }
    }
}
