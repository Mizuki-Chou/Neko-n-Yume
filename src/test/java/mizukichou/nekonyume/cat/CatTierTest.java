package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CatTierTest {

    @Test
    void checkpointBoundaries() {

        assertEquals(0, CatTier.checkpointsReached(0, 1));
        assertEquals(1, CatTier.checkpointsReached(1, 1));
        assertEquals(1, CatTier.checkpointsReached(9, 99));
        assertEquals(1, CatTier.checkpointsReached(10, 29));
        assertEquals(2, CatTier.checkpointsReached(10, 30));
        assertEquals(2, CatTier.checkpointsReached(29, 80));
        assertEquals(3, CatTier.checkpointsReached(30, 80));
    }

    @Test
    void commonCapsAtOneSlot() {

        assertEquals(0, CatTier.COMMON.slotCount(0));
        assertEquals(1, CatTier.COMMON.slotCount(1));
        assertEquals(1, CatTier.COMMON.slotCount(2));
        assertEquals(1, CatTier.COMMON.slotCount(3));
    }

    @Test
    void rareGrowsOnePerCheckpoint() {

        assertEquals(0, CatTier.RARE.slotCount(0));
        assertEquals(1, CatTier.RARE.slotCount(1));
        assertEquals(2, CatTier.RARE.slotCount(2));
        assertEquals(3, CatTier.RARE.slotCount(3));
    }

    @Test
    void uniqueGrowsTwoPerCheckpoint() {

        assertEquals(0, CatTier.UNIQUE.slotCount(0));
        assertEquals(2, CatTier.UNIQUE.slotCount(1));
        assertEquals(4, CatTier.UNIQUE.slotCount(2));
        assertEquals(6, CatTier.UNIQUE.slotCount(3));
    }

    @Test
    void dreamHasDreamSlotAndReachesTen() {

        assertEquals(1, CatTier.DREAM.slotCount(0));
        assertEquals(4, CatTier.DREAM.slotCount(1));
        assertEquals(7, CatTier.DREAM.slotCount(2));
        assertEquals(10, CatTier.DREAM.slotCount(3));
    }

    @Test
    void slotCountClampsOutOfRangeCheckpoints() {

        assertEquals(0, CatTier.COMMON.slotCount(-5));
        assertEquals(10, CatTier.DREAM.slotCount(99));
    }

    @Test
    void dreamSlotAllowsDreamSkillsOnlyForDreamCats() {

        assertEquals(
                CatTier.DREAM,
                CatTier.maxSkillTierForSlot(
                        CatTier.DREAM,
                        true
                )
        );

        assertEquals(
                CatTier.UNIQUE,
                CatTier.maxSkillTierForSlot(
                        CatTier.DREAM,
                        false
                )
        );

        assertEquals(
                CatTier.RARE,
                CatTier.maxSkillTierForSlot(
                        CatTier.RARE,
                        false
                )
        );
    }

    @Test
    void tierGenerationIsDeterministicAndInRange() {

        UUID id =
                UUID.fromString(
                        "8441445b-2aeb-45bd-a1f1-bde96df6d1eb"
                );

        assertEquals(
                CatTier.fromCatId(id),
                CatTier.fromCatId(id)
        );

        assertNotNull(
                CatTier.fromCatId(
                        null
                )
        );
    }

    @Test
    void weightDistributionCoversAllTiers() {

        Set<CatTier> seen =
                new HashSet<>();

        for (int i = 0;
             i < 5000;
             i++) {

            seen.add(
                    CatTier.fromCatId(
                            UUID.randomUUID()
                    )
            );
        }

        assertEquals(
                4,
                seen.size()
        );
    }

    @Test
    void nameParsing() {

        assertEquals(
                CatTier.DREAM,
                CatTier.fromName("DREAM")
        );

        assertEquals(
                CatTier.RARE,
                CatTier.fromName("rare")
        );

        assertNull(
                CatTier.fromName(
                        "神话"
                )
        );

        assertNull(
                CatTier.fromName(
                        null
                )
        );
    }
}
