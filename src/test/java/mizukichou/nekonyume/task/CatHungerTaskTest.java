package mizukichou.nekonyume.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 饥饿好感衰减节流判定（0.8.0）：
 * 纯函数，与饥饿 tick 解耦的节奏语义。
 */
class CatHungerTaskTest {

    private static final long INTERVAL =
            180L * 60_000L;

    @Test
    void appliesWhenEnoughTimeElapsed() {

        assertTrue(
                CatHungerTask.shouldApplyStarveLoss(
                        0L,
                        0L,
                        INTERVAL
                )
        );

        assertTrue(
                CatHungerTask.shouldApplyStarveLoss(
                        INTERVAL,
                        0L,
                        INTERVAL
                )
        );
    }

    @Test
    void skipsWithinInterval() {

        assertFalse(
                CatHungerTask.shouldApplyStarveLoss(
                        INTERVAL - 1,
                        0L,
                        INTERVAL
                )
        );
    }

    @Test
    void intervalZeroDisablesPacing() {

        assertFalse(
                CatHungerTask.shouldApplyStarveLoss(
                        1_000_000L,
                        0L,
                        0L
                )
        );
    }

    /*
     * ============================================================
     * 围巾的饥饿衰减减缓（0.8.0）
     * ============================================================
     */

    @Test
    void hungerSlowScalesIntervalUp() {

        assertEquals(
                600_000L,
                CatHungerTask.applyHungerSlow(
                        300_000L,
                        50
                )
        );

        assertEquals(
                428_571L,
                CatHungerTask.applyHungerSlow(
                        300_000L,
                        30
                )
        );

        assertEquals(
                333_333L,
                CatHungerTask.applyHungerSlow(
                        300_000L,
                        10
                )
        );
    }

    @Test
    void hungerSlowZeroOrNegativeKeepsInterval() {

        assertEquals(
                300_000L,
                CatHungerTask.applyHungerSlow(
                        300_000L,
                        0
                )
        );

        assertEquals(
                300_000L,
                CatHungerTask.applyHungerSlow(
                        300_000L,
                        -20
                )
        );
    }

    @Test
    void hungerSlowClampsExtremes() {

        /*
         * 零间隔原样返回（防御）。
         */
        assertEquals(
                0L,
                CatHungerTask.applyHungerSlow(
                        0L,
                        50
                )
        );

        /*
         * 超过 90 钳到 90：300000 / 0.1 = 3000000。
         */
        assertEquals(
                3_000_000L,
                CatHungerTask.applyHungerSlow(
                        300_000L,
                        200
                )
        );

        assertEquals(
                3_000_000L,
                CatHungerTask.applyHungerSlow(
                        300_000L,
                        90
                )
        );
    }
}
