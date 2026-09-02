package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
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


    @Test
    void exactFieldValues() {
        assertEquals(1.5, CatPersonality.GOURMAND.getHungerRate(), 1e-9);
        assertEquals(3, CatPersonality.GOURMAND.getFeedAffectionBonus());
        assertEquals(3, CatPersonality.GOURMAND.getFeedMeowChanceBonus());
        assertEquals(0.7, CatPersonality.LAZY.getHungerRate(), 1e-9);
        assertEquals(2, CatPersonality.CLINGY.getPetMeowChanceBonus());
        assertEquals(500L, CatPersonality.CLINGY.getPetCooldownMillis());
        assertEquals(-2, CatPersonality.INDEPENDENT.getPetMeowChanceBonus());
        assertEquals(0.9, CatPersonality.INDEPENDENT.getHungerRate(), 1e-9);
        assertEquals(-3, CatPersonality.PICKY.getFeedMeowChanceBonus());
        assertEquals(0.8, CatPersonality.PICKY.getFoodValueMultiplier(), 1e-9);
        assertEquals(1, CatPersonality.SUNNY.getFeedMeowChanceBonus());
        assertEquals(1, CatPersonality.SUNNY.getPetMeowChanceBonus());
        assertEquals(10, CatPersonality.SUNNY.getMoodBonus());
    }

    @Test
    void fromCatIdDeterministic() {
        UUID fixed = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        CatPersonality first = CatPersonality.fromCatId(fixed);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, CatPersonality.fromCatId(fixed));
        }
    }

    @Test
    void fromCatIdNullSafe() {
        assertNotNull(CatPersonality.fromCatId(null));
    }

    @Test
    void hungerRatesAllPositive() {
        for (CatPersonality p : CatPersonality.values()) {
            assertTrue(p.getHungerRate() > 0.0);
        }
    }

    @Test
    void cooldownsAllPositive() {
        for (CatPersonality p : CatPersonality.values()) {
            assertTrue(p.getPetCooldownMillis() > 0L);
        }
    }

    @Test
    void displayNamesNonBlank() {
        for (CatPersonality p : CatPersonality.values()) {
            assertNotNull(p.getDisplayName());
            assertFalse(p.getDisplayName().isBlank());
        }
    }

}
