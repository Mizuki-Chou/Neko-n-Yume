package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeowDanQualityTest {

    @Test
    void parsesChineseNames() {

        assertEquals(
                MeowDanQuality.COMMON,
                MeowDanQuality.fromInput("平凡")
        );

        assertEquals(
                MeowDanQuality.UNCOMMON,
                MeowDanQuality.fromInput("精良")
        );

        assertEquals(
                MeowDanQuality.RARE,
                MeowDanQuality.fromInput("独特")
        );

        assertEquals(
                MeowDanQuality.EPIC,
                MeowDanQuality.fromInput("卓越")
        );

        assertEquals(
                MeowDanQuality.LEGENDARY,
                MeowDanQuality.fromInput("至极")
        );
    }

    @Test
    void parsesEnglishNamesIgnoringCase() {

        assertEquals(
                MeowDanQuality.COMMON,
                MeowDanQuality.fromInput("common")
        );

        assertEquals(
                MeowDanQuality.UNCOMMON,
                MeowDanQuality.fromInput("UNCOMMON")
        );

        assertEquals(
                MeowDanQuality.RARE,
                MeowDanQuality.fromInput("Rare")
        );

        assertEquals(
                MeowDanQuality.EPIC,
                MeowDanQuality.fromInput("epic")
        );

        assertEquals(
                MeowDanQuality.LEGENDARY,
                MeowDanQuality.fromInput("LEGENDARY")
        );
    }

    @Test
    void invalidInputReturnsNull() {

        assertNull(
                MeowDanQuality.fromInput(
                        null
                )
        );

        assertNull(
                MeowDanQuality.fromInput(
                        ""
                )
        );

        assertNull(
                MeowDanQuality.fromInput(
                        "   "
                )
        );

        assertNull(
                MeowDanQuality.fromInput(
                        "神话"
                )
        );
    }

    @Test
    void legendaryIsStrongest() {

        for (MeowDanQuality quality :
                MeowDanQuality.values()) {

            if (quality == MeowDanQuality.LEGENDARY) {
                continue;
            }

            assertTrue(
                    MeowDanQuality.LEGENDARY
                            .getMeowPowerGain()
                            > quality.getMeowPowerGain(),
                    "legendary should beat "
                            + quality
            );
        }
    }

    @Test
    void fullDisplayNameContainsChineseName() {

        assertTrue(
                MeowDanQuality.RARE
                        .getFullDisplayName()
                        .contains("喵丹")
        );
    }
}

