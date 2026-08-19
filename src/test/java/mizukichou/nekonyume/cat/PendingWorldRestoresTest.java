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
}

