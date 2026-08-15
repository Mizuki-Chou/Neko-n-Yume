package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrowthMathTest {

    @Test
    void xpCurveBase100Boundaries() {

        assertEquals(0L, GrowthMath.xpRequiredForLevel(1, 100));
        assertEquals(100L, GrowthMath.xpRequiredForLevel(2, 100));
        assertEquals(300L, GrowthMath.xpRequiredForLevel(3, 100));

        assertEquals(1, GrowthMath.levelFromExperience(99, 100));
        assertEquals(2, GrowthMath.levelFromExperience(100, 100));
        assertEquals(2, GrowthMath.levelFromExperience(299, 100));
        assertEquals(3, GrowthMath.levelFromExperience(300, 100));
    }

    @Test
    void meowCurveOffset19Boundaries() {

        assertEquals(0L, GrowthMath.meowRequiredForRank(0, 19));
        assertEquals(10L, GrowthMath.meowRequiredForRank(1, 19));
        assertEquals(21L, GrowthMath.meowRequiredForRank(2, 19));
        assertEquals(33L, GrowthMath.meowRequiredForRank(3, 19));

        assertEquals(0, GrowthMath.meowRankFromPower(9, 19));
        assertEquals(1, GrowthMath.meowRankFromPower(10, 19));
        assertEquals(2, GrowthMath.meowRankFromPower(21, 19));
        assertEquals(3, GrowthMath.meowRankFromPower(33, 19));
    }

    @Test
    void invalidCurveParamsFallBackToDefaults() {

        assertEquals(1, GrowthMath.levelFromExperience(500, 0));
        assertEquals(2, GrowthMath.levelFromExperience(100, -10));
        assertEquals(1, GrowthMath.meowRankFromPower(10, 0));
        assertEquals(1, GrowthMath.meowRankFromPower(10, -19));
    }

    @Test
    void catDelegatesToGrowthMath() {

        Cat cat =
                Cat.createNew(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Test"
                );

        assertEquals(1, cat.addExperience(100));
        assertEquals(2, cat.getLevel());

        assertEquals(0, cat.addExperience(50));
        assertEquals(150, cat.getExperience());

        assertEquals(1, cat.addMeowPower(10));
        assertEquals(1, cat.getMeowRank());
    }
}