package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatTierTest {

    @Test
    void checkpointBoundaries() {

        assertEquals(0, CatTier.checkpointsReached(0, 1));
        assertEquals(1, CatTier.checkpointsReached(1, 1));
        assertEquals(1, CatTier.checkpointsReached(9, 99));
        assertEquals(1, CatTier.checkpointsReached(10, 29));
        assertEquals(2, CatTier.checkpointsReached(10, 30));

        /*
         * 0.6.2：第三拐点由等级 80 下调至 60。
         */
        assertEquals(2, CatTier.checkpointsReached(29, 99));
        assertEquals(2, CatTier.checkpointsReached(30, 59));
        assertEquals(3, CatTier.checkpointsReached(30, 60));
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

        UUID catId = UUID.randomUUID();

        CatTier first =
                CatTier.fromCatId(catId);

        assertNotNull(first);

        assertEquals(
                first,
                CatTier.fromCatId(catId)
        );

        /*
         * 0.6.2：出生底蕴只有普通/稀有两档
         * （更高底蕴通过喵丹喂养升阶）。
         */
        assertTrue(
                first == CatTier.COMMON ||
                        first == CatTier.RARE
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

        /*
         * 0.6.2：出生分布只覆盖普通与稀有；
         * 独特与梦幻只能通过升阶获得。
         */
        assertTrue(
                seen.contains(CatTier.COMMON),
                "should see COMMON"
        );

        assertTrue(
                seen.contains(CatTier.RARE),
                "should see RARE"
        );

        assertFalse(
                seen.contains(CatTier.UNIQUE),
                "UNIQUE should not be born naturally"
        );

        assertFalse(
                seen.contains(CatTier.DREAM),
                "DREAM should not be born naturally"
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
