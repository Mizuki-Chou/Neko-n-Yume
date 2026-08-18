package mizukichou.nekonyume.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GiftTierTest {

    @Test
    void tierBoundariesEveryFiveRanks() {

        assertEquals(1, ConfigSnapshot.Gift.computeTier(0));
        assertEquals(1, ConfigSnapshot.Gift.computeTier(4));
        assertEquals(1, ConfigSnapshot.Gift.computeTier(5));

        assertEquals(2, ConfigSnapshot.Gift.computeTier(6));
        assertEquals(2, ConfigSnapshot.Gift.computeTier(10));

        assertEquals(3, ConfigSnapshot.Gift.computeTier(11));
        assertEquals(3, ConfigSnapshot.Gift.computeTier(15));

        assertEquals(4, ConfigSnapshot.Gift.computeTier(16));
        assertEquals(4, ConfigSnapshot.Gift.computeTier(20));

        assertEquals(5, ConfigSnapshot.Gift.computeTier(21));
        assertEquals(5, ConfigSnapshot.Gift.computeTier(25));

        assertEquals(6, ConfigSnapshot.Gift.computeTier(26));
    }

    @Test
    void negativeRankClampsToTierOne() {

        assertEquals(
                1,
                ConfigSnapshot.Gift.computeTier(
                        -100
                )
        );
    }

    @Test
    void highRankKeepsGrowing() {

        assertEquals(
                20,
                ConfigSnapshot.Gift.computeTier(
                        100
                )
        );
    }
}
