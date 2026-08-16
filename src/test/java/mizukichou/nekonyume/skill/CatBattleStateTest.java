package mizukichou.nekonyume.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CatBattleStateTest {

    private CatBattleState state;

    @BeforeEach
    void setUp() {

        state = new CatBattleState();
    }

    @Test
    void cleanupRemovesStaleEntityStates() {

        UUID live = UUID.randomUUID();
        UUID stale = UUID.randomUUID();

        state.setChasing(live, true);
        state.setChasing(stale, true);

        state.markRecovering(stale, 120_000L);
        state.markPounce(stale);
        state.markAttack(stale);

        state.retainOnly(
                List.of(live),
                List.of()
        );

        assertTrue(state.isChasing(live));
        assertFalse(state.isChasing(stale));
        assertFalse(state.isRecovering(stale));
        assertNull(
                state.getRecoveryRemainingMillis(stale)
        );
    }

    @Test
    void cleanupRemovesStaleOwnerTargets() {

        UUID liveOwner = UUID.randomUUID();
        UUID staleOwner = UUID.randomUUID();

        state.markAssistTarget(
                liveOwner,
                UUID.randomUUID()
        );

        state.markAssistTarget(
                staleOwner,
                UUID.randomUUID()
        );

        state.retainOnly(
                List.of(),
                List.of(liveOwner)
        );

        assertNotNull(
                state.getAssistTarget(liveOwner)
        );

        assertNull(
                state.getAssistTarget(staleOwner)
        );
    }

    @Test
    void recoveryCountdownExpires() throws InterruptedException {

        UUID cat = UUID.randomUUID();

        state.markRecovering(cat, 120L);

        assertTrue(state.isRecovering(cat));
        assertTrue(
                state.getRecoveryRemainingMillis(cat) > 0
        );

        Thread.sleep(200L);

        assertFalse(state.isRecovering(cat));

        /*
         * 到期后记录仍在队列中：
         * 剩余毫秒 = 0（等待战斗任务复活并 clearRecovery）。
         * 只有从未标记或已清除时才返回 null。
         */
        assertEquals(
                0L,
                state.getRecoveryRemainingMillis(cat)
        );

        /*
         * 复活流程：clearRecovery 之后为 null。
         */
        state.clearRecovery(cat);

        assertNull(
                state.getRecoveryRemainingMillis(cat)
        );
    }

    @Test
    void chaseGraceAllowsFollowDelay() {

        UUID cat = UUID.randomUUID();

        state.setChasing(cat, true);

        assertTrue(
                state.isChasingOrRecentlyEnded(
                        cat,
                        3000L
                )
        );

        state.setChasing(cat, false);

        assertFalse(state.isChasing(cat));

        /*
         * 刚结束追击：仍处于收势宽限内。
         */
        assertTrue(
                state.isChasingOrRecentlyEnded(
                        cat,
                        3000L
                )
        );
    }

    @Test
    void pounceAndAttackThrottle() {

        UUID cat = UUID.randomUUID();

        assertTrue(
                state.canPounce(cat, 1000L)
        );

        state.markPounce(cat);

        assertFalse(
                state.canPounce(cat, 1000L)
        );

        assertTrue(
                state.canAttack(cat, 500L)
        );

        state.markAttack(cat);

        assertFalse(
                state.canAttack(cat, 500L)
        );
    }
}