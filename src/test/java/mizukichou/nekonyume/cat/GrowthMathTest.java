package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
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

        /*
         * 非法曲线参数必须回退到默认值
         * （经验曲线 100 / 喵阶曲线 19），
         * 结果与显式使用默认参数完全一致。
         *
         * 500 经验 / 曲线 100：
         *   L2 需 100，L3 需 300，L4 需 600
         *   → 500 经验 = 3 级。
         */
        assertEquals(3, GrowthMath.levelFromExperience(500, 0));
        assertEquals(
                GrowthMath.levelFromExperience(500, 100),
                GrowthMath.levelFromExperience(500, 0)
        );

        assertEquals(2, GrowthMath.levelFromExperience(100, -10));

        /*
         * 10 喵力 / 曲线 19：
         *   第 1 阶需 10 → 喵阶 1。
         */
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


    /*
     * 二分实现与线性参照的逐值等价（多基数随机扫描）。
     */
    static int referenceLevel(int totalExperience, int base) {
        int normalized = GrowthMath.normalizeXpCurveBase(base);
        int level = 1;
        while (level < GrowthMath.MAX_LEVEL) {
            long next = GrowthMath.xpRequiredForLevel(level + 1, normalized);
            if (totalExperience < next) break;
            level++;
        }
        return level;
    }

    static int referenceMeowRank(int totalMeowPower, int offset) {
        int normalized = GrowthMath.normalizeMeowCurveOffset(offset);
        int rank = 0;
        while (rank < GrowthMath.MAX_LEVEL) {
            long next = GrowthMath.meowRequiredForRank(rank + 1, normalized);
            if (totalMeowPower < next) break;
            rank++;
        }
        return rank;
    }

    @Test
    void levelBinarySearchMatchesReferenceAcrossRandomBases() {
        java.util.Random rnd = new java.util.Random(101);
        int[] bases = {1, 2, 3, 5, 7, 10, 17, 100, 256, 999, 1000, 10000};
        for (int base : bases) {
            for (int exp : new int[]{0, 1, base - 1, base, base * 2, 999, 10000, 1000000, 100000000, Integer.MAX_VALUE}) {
                assertEquals(referenceLevel(exp, base), GrowthMath.levelFromExperience(exp, base), "base=" + base + " exp=" + exp);
            }
            for (int i = 0; i < 300; i++) {
                int exp = rnd.nextInt(Integer.MAX_VALUE);
                assertEquals(referenceLevel(exp, base), GrowthMath.levelFromExperience(exp, base), "base=" + base + " exp=" + exp);
            }
        }
    }

    @Test
    void meowRankBinarySearchMatchesReferenceAcrossRandomOffsets() {
        java.util.Random rnd = new java.util.Random(202);
        int[] offsets = {1, 2, 5, 10, 19, 50, 100};
        for (int offset : offsets) {
            for (int power : new int[]{0, 1, 9, 10, 20, 21, 100, 1000, 100000, 100000000, Integer.MAX_VALUE}) {
                assertEquals(referenceMeowRank(power, offset), GrowthMath.meowRankFromPower(power, offset), "offset=" + offset + " power=" + power);
            }
            for (int i = 0; i < 300; i++) {
                int power = rnd.nextInt(Integer.MAX_VALUE);
                assertEquals(referenceMeowRank(power, offset), GrowthMath.meowRankFromPower(power, offset), "offset=" + offset + " power=" + power);
            }
        }
    }

    @Test
    void levelEdges() {
        assertEquals(1, GrowthMath.levelFromExperience(0, 100));
        assertEquals(1, GrowthMath.levelFromExperience(-1, 100));
        assertEquals(1, GrowthMath.levelFromExperience(Integer.MIN_VALUE, 100));
        assertEquals(GrowthMath.MAX_LEVEL, GrowthMath.levelFromExperience(Integer.MAX_VALUE, 1));
        assertEquals(1, GrowthMath.levelFromExperience(99, 100));
        assertEquals(2, GrowthMath.levelFromExperience(100, 100));
        assertEquals(2, GrowthMath.levelFromExperience(199, 100));
        assertEquals(2, GrowthMath.levelFromExperience(200, 100), "累计 300 才到 3 级");
    }

    @Test
    void meowRankEdges() {
        assertEquals(0, GrowthMath.meowRankFromPower(0, 19));
        assertEquals(0, GrowthMath.meowRankFromPower(-5, 19));
        assertEquals(0, GrowthMath.meowRankFromPower(9, 19));
        assertEquals(1, GrowthMath.meowRankFromPower(10, 19));
        assertEquals(1, GrowthMath.meowRankFromPower(20, 19));
        assertEquals(2, GrowthMath.meowRankFromPower(21, 19));
    }

    @Test
    void normalizeXpCurveBaseRules() {
        assertEquals(100, GrowthMath.normalizeXpCurveBase(0), "非法值回退默认 100");
        assertEquals(100, GrowthMath.normalizeXpCurveBase(-10));
        assertEquals(100, GrowthMath.normalizeXpCurveBase(Integer.MIN_VALUE));
        assertEquals(100, GrowthMath.normalizeXpCurveBase(100));
        assertEquals(Integer.MAX_VALUE, GrowthMath.normalizeXpCurveBase(Integer.MAX_VALUE));
    }

    @Test
    void normalizeMeowCurveOffsetRules() {
        assertEquals(19, GrowthMath.normalizeMeowCurveOffset(0), "非法值回退默认 19");
        assertEquals(19, GrowthMath.normalizeMeowCurveOffset(-3));
        assertEquals(19, GrowthMath.normalizeMeowCurveOffset(19));
        assertEquals(Integer.MAX_VALUE, GrowthMath.normalizeMeowCurveOffset(Integer.MAX_VALUE));
    }

    @Test
    void xpRequiredMonotonicStrictlyIncreasing() {
        for (int base : new int[]{1, 5, 100, 999}) {
            long prev = GrowthMath.xpRequiredForLevel(1, base);
            for (int level = 2; level <= 100; level++) {
                long cur = GrowthMath.xpRequiredForLevel(level, base);
                assertTrue(cur > prev, "base=" + base + " level=" + level);
                prev = cur;
            }
        }
    }

    @Test
    void meowRequiredMonotonicStrictlyIncreasing() {
        for (int offset : new int[]{1, 19, 50}) {
            long prev = GrowthMath.meowRequiredForRank(1, offset);
            for (int rank = 2; rank <= 100; rank++) {
                long cur = GrowthMath.meowRequiredForRank(rank, offset);
                assertTrue(cur > prev, "offset=" + offset + " rank=" + rank);
                prev = cur;
            }
        }
    }

    @Test
    void levelInverseConsistency() {
        for (int base : new int[]{2, 10, 100, 1000}) {
            for (int level : new int[]{1, 2, 3, 5, 10, 50, 100, 1000}) {
                long total = GrowthMath.xpRequiredForLevel(level, base);
                assertEquals(level, GrowthMath.levelFromExperience((int) Math.min(total, Integer.MAX_VALUE), base), "base=" + base + " level=" + level);
            }
        }
    }

    @Test
    void maxLevelCaps() {
        int base = 1;
        long hugeTotal = GrowthMath.xpRequiredForLevel(GrowthMath.MAX_LEVEL, base);
        assertEquals(GrowthMath.MAX_LEVEL, GrowthMath.levelFromExperience(Integer.MAX_VALUE, base));
        assertTrue(hugeTotal > 0);
    }

}
