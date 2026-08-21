package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 羁绊档位推导（0.8.0）：
 * 边界值 + 越界钳制 + 非法阈值回退。
 */
class BondTierTest {

    private static final List<Integer> THRESHOLDS =
            List.of(
                    20,
                    40,
                    60,
                    80,
                    100
            );

    @Test
    void deriveBoundaries() {

        assertEquals(
                BondTier.STRANGER,
                BondTier.derive(
                        0,
                        THRESHOLDS
                )
        );

        assertEquals(
                BondTier.STRANGER,
                BondTier.derive(
                        19,
                        THRESHOLDS
                )
        );

        assertEquals(
                BondTier.ACQUAINTANCE,
                BondTier.derive(
                        20,
                        THRESHOLDS
                )
        );

        assertEquals(
                BondTier.ACQUAINTANCE,
                BondTier.derive(
                        39,
                        THRESHOLDS
                )
        );

        assertEquals(
                BondTier.FRIEND,
                BondTier.derive(
                        40,
                        THRESHOLDS
                )
        );

        assertEquals(
                BondTier.FRIEND,
                BondTier.derive(
                        59,
                        THRESHOLDS
                )
        );

        assertEquals(
                BondTier.INTIMATE,
                BondTier.derive(
                        60,
                        THRESHOLDS
                )
        );

        assertEquals(
                BondTier.INTIMATE,
                BondTier.derive(
                        79,
                        THRESHOLDS
                )
        );

        assertEquals(
                BondTier.TRUSTED,
                BondTier.derive(
                        80,
                        THRESHOLDS
                )
        );

        assertEquals(
                BondTier.TRUSTED,
                BondTier.derive(
                        99,
                        THRESHOLDS
                )
        );

        assertEquals(
                BondTier.SOULMATE,
                BondTier.derive(
                        100,
                        THRESHOLDS
                )
        );
    }

    @Test
    void deriveClampsOutOfRange() {

        assertEquals(
                BondTier.STRANGER,
                BondTier.derive(
                        -5,
                        THRESHOLDS
                )
        );

        assertEquals(
                BondTier.SOULMATE,
                BondTier.derive(
                        250,
                        THRESHOLDS
                )
        );
    }

    @Test
    void deriveFallsBackForNullOrShortThresholds() {

        assertEquals(
                BondTier.SOULMATE,
                BondTier.derive(
                        100,
                        null
                )
        );

        assertEquals(
                BondTier.SOULMATE,
                BondTier.derive(
                        100,
                        List.of(
                                50
                        )
                )
        );
    }

    @Test
    void everyTierHasLangKey() {

        for (BondTier tier :
                BondTier.values()) {

            assertNotNull(
                    tier.langKey()
            );

            assertEquals(
                    "bond-name." + tier.getId(),
                    tier.langKey()
            );
        }
    }
}
