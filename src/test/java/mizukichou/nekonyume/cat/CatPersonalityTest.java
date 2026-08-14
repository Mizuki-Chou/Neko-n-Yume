package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatPersonalityTest {

    @Test
    void nullIdFallsBackToLazy() {

        assertEquals(
                CatPersonality.LAZY,
                CatPersonality.fromCatId(
                        null
                )
        );
    }

    @Test
    void sameIdAlwaysGivesSamePersonality() {

        UUID id =
                UUID.fromString(
                        "8441445b-2aeb-45bd-a1f1-bde96df6d1eb"
                );

        assertEquals(
                CatPersonality.fromCatId(id),
                CatPersonality.fromCatId(id)
        );
    }

    @Test
    void everyPersonalityHasValidParameters() {

        for (CatPersonality personality :
                CatPersonality.values()) {

            assertNotNull(
                    personality.getDisplayName()
            );

            assertTrue(
                    personality.getHungerRate() > 0,
                    "hungerRate must be positive"
            );

            assertTrue(
                    personality.getPetCooldownMillis() > 0,
                    "petCooldownMillis must be positive"
            );

            assertTrue(
                    personality.getFoodValueMultiplier() > 0,
                    "foodValueMultiplier must be positive"
            );
        }
    }

    @Test
    void distributionCoversAllPersonalities() {

        Set<CatPersonality> seen =
                new HashSet<>();

        for (int i = 0;
             i < 5000;
             i++) {

            seen.add(
                    CatPersonality.fromCatId(
                            UUID.randomUUID()
                    )
            );
        }

        /*
         * 5000 个样本应当覆盖全部 6 种性格。
         */
        assertEquals(
                CatPersonality.values().length,
                seen.size()
        );
    }
}
