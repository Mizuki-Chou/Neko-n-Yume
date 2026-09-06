package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingWorldRestoresTest {

    @Test
    void addThenConsumeReturnsPlayers() {

        PendingWorldRestores queue =
                new PendingWorldRestores();

        UUID world = UUID.randomUUID();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        queue.add(world, playerA);
        queue.add(world, playerB);

        assertEquals(
                Set.of(playerA, playerB),
                queue.consumeForWorld(world)
        );

        assertTrue(queue.isEmpty());
    }

    @Test
    void quitBeforeWorldLoadRemovesPlayer() {

        PendingWorldRestores queue =
                new PendingWorldRestores();

        UUID world = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        queue.add(world, player);

        /*
         * 玩家退出（竞态）：
         * 世界加载时不应再为该玩家恢复实体。
         */
        queue.removePlayer(player);

        assertTrue(
                queue.consumeForWorld(world)
                        .isEmpty()
        );
    }

    @Test
    void multipleWorldsAreIndependent() {

        PendingWorldRestores queue =
                new PendingWorldRestores();

        UUID worldA = UUID.randomUUID();
        UUID worldB = UUID.randomUUID();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        queue.add(worldA, playerA);
        queue.add(worldB, playerB);

        assertEquals(
                Set.of(playerA),
                queue.consumeForWorld(worldA)
        );

        assertEquals(
                Set.of(playerB),
                queue.consumeForWorld(worldB)
        );
    }

    @Test
    void duplicateAddIsIdempotent() {

        PendingWorldRestores queue =
                new PendingWorldRestores();

        UUID world = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        queue.add(world, player);
        queue.add(world, player);

        assertEquals(
                1,
                queue.consumeForWorld(world)
                        .size()
        );
    }

    @Test
    void forgetWorldDropsPendingEntries() {

        /*
         * 
         * 世界卸载 → 该世界的待恢复记录作废。
         */
        PendingWorldRestores queue =
                new PendingWorldRestores();

        UUID worldA = UUID.randomUUID();
        UUID worldB = UUID.randomUUID();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        queue.add(worldA, playerA);
        queue.add(worldB, playerB);

        queue.forgetWorld(worldA);

        assertTrue(
                queue.consumeForWorld(worldA)
                        .isEmpty()
        );

        assertEquals(
                Set.of(playerB),
                queue.consumeForWorld(worldB)
        );

        assertTrue(queue.isEmpty());
    }

    @Test
    void unknownWorldReturnsEmpty() {

        PendingWorldRestores queue =
                new PendingWorldRestores();

        assertTrue(
                queue.consumeForWorld(
                                UUID.randomUUID()
                        )
                        .isEmpty()
        );
    }

    @Test
    void removePlayerDropsEmptyWorldEntry() {

        /*
         * 动态世界场景：世界从未加载的等待记录，
         * 玩家退出后空世界键必须同步移除，
         * 不得长期残留导致无界增长。
         */
        PendingWorldRestores queue =
                new PendingWorldRestores();

        UUID world = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        queue.add(world, player);
        queue.removePlayer(player);

        assertTrue(queue.isEmpty());
    }

    @Test
    void removePlayerKeepsEntryWhileOthersWait() {

        PendingWorldRestores queue =
                new PendingWorldRestores();

        UUID world = UUID.randomUUID();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        queue.add(world, playerA);
        queue.add(world, playerB);

        queue.removePlayer(playerA);

        /*
         * B 仍在等待：世界键必须保留。
         */
        assertEquals(
                Set.of(playerB),
                queue.consumeForWorld(world)
        );

        assertTrue(queue.isEmpty());
    }
}

