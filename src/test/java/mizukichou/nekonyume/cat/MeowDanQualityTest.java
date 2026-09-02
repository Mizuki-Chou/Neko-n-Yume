package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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


    @Test
    void fromInputFlexible() {
        assertEquals(MeowDanQuality.COMMON, MeowDanQuality.fromInput("COMMON"));
        assertEquals(MeowDanQuality.COMMON, MeowDanQuality.fromInput("common"));
        assertEquals(MeowDanQuality.COMMON, MeowDanQuality.fromInput("平凡"));
        assertEquals(MeowDanQuality.LEGENDARY, MeowDanQuality.fromInput("至极"));
        assertEquals(MeowDanQuality.EPIC, MeowDanQuality.fromInput("epic"));
        assertNull(MeowDanQuality.fromInput(null));
        assertNull(MeowDanQuality.fromInput(""));
        assertNull(MeowDanQuality.fromInput("  "));
        assertNull(MeowDanQuality.fromInput("神话"));
    }

    @Test
    void gainsIncreaseWithQuality() {
        MeowDanQuality[] q = MeowDanQuality.values();
        for (int i = 1; i < q.length; i++) {
            assertTrue(q[i].getMeowPowerGain() > q[i - 1].getMeowPowerGain());
            assertTrue(q[i].getXpGain() > q[i - 1].getXpGain());
            assertTrue(q[i].getAffectionGain() >= q[i - 1].getAffectionGain());
        }
    }

    @Test
    void modelDataUnique() {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (MeowDanQuality q : MeowDanQuality.values()) {
            assertTrue(seen.add(q.getDefaultModelData()), "modelData 重复: " + q);
        }
    }

    @Test
    void gainsPositive() {
        for (MeowDanQuality q : MeowDanQuality.values()) {
            assertTrue(q.getMeowPowerGain() > 0);
            assertTrue(q.getXpGain() > 0);
            assertTrue(q.getAffectionGain() > 0);
        }
    }

    @Test
    void fiveQualitiesOrdered() {
        assertEquals(5, MeowDanQuality.values().length);
        assertEquals(MeowDanQuality.COMMON, MeowDanQuality.values()[0]);
        assertEquals(MeowDanQuality.LEGENDARY, MeowDanQuality.values()[4]);
    }

    @Test
    void displayNameAndColorPresent() {
        for (MeowDanQuality q : MeowDanQuality.values()) {
            assertNotNull(q.getDisplayName());
            assertFalse(q.getDisplayName().isBlank());
            assertNotNull(q.getColorCode());
        }
    }

    @Test
    void fullDisplayNameWellFormed() {
        for (MeowDanQuality q : MeowDanQuality.values()) {
            String full = q.getFullDisplayName();
            assertTrue(full.contains(q.getDisplayName()));
            assertTrue(full.contains("喵丹"));
        }
    }

}
