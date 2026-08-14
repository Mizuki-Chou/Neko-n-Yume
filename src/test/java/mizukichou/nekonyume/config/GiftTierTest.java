package mizukichou.nekonyume.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GiftTierTest {

    @Test
    void tierBoundariesEveryFiveRanks() {

        assertEquals(1, PluginConfig.computeGiftTier(0));
        assertEquals(1, PluginConfig.computeGiftTier(4));
        assertEquals(1, PluginConfig.computeGiftTier(5));

        assertEquals(2, PluginConfig.computeGiftTier(6));
        assertEquals(2, PluginConfig.computeGiftTier(10));

        assertEquals(3, PluginConfig.computeGiftTier(11));
        assertEquals(3, PluginConfig.computeGiftTier(15));

        assertEquals(4, PluginConfig.computeGiftTier(16));
        assertEquals(4, PluginConfig.computeGiftTier(20));

        assertEquals(5, PluginConfig.computeGiftTier(21));
        assertEquals(5, PluginConfig.computeGiftTier(25));

        assertEquals(6, PluginConfig.computeGiftTier(26));
    }

    @Test
    void negativeRankClampsToTierOne() {

        assertEquals(
                1,
                PluginConfig.computeGiftTier(
                        -100
                )
        );
    }

    @Test
    void highRankKeepsGrowing() {

        assertEquals(
                20,
                PluginConfig.computeGiftTier(
                        100
                )
        );
    }
}
