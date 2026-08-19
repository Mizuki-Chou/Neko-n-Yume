package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 经验丸档位解析测试（0.7.4）。
 */
class XpPillTierTest {

    @Test
    void parsesKnownIds() {

        assertEquals(
                XpPillTier.NORMAL,
                XpPillTier.fromId("normal")
        );

        assertEquals(
                XpPillTier.ELITE,
                XpPillTier.fromId("elite")
        );

        assertEquals(
                XpPillTier.ELITE,
                XpPillTier.fromId("ELITE")
        );
    }

    @Test
    void unknownIdsReturnNull() {

        assertNull(XpPillTier.fromId(null));
        assertNull(XpPillTier.fromId(""));
        assertNull(XpPillTier.fromId("   "));
        assertNull(XpPillTier.fromId("legendary"));
    }
}
