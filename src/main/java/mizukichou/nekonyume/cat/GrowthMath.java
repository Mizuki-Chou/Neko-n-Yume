package mizukichou.nekonyume.cat;

/**
 * 猫咪成长曲线纯函数。
 *
 * <p>
 * 从 Cat 中抽取，避免 God Object：
 * 曲线数学与状态持有解耦，
 * 任何组件（命令 / GUI / 数据迁移）都可以直接使用。
 * </p>
 *
 * <p>
 * 两条曲线：
 * 升到 L 级所需累计经验 cumXp(L) = base × L × (L-1) / 2；
 * 升到第 N 阶所需累计喵力 cumMeow(N) = N × (N+offset) / 2。
 * </p>
 *
 * <p>
 * 所有方法都是无状态的纯函数，可单元测试。
 * </p>
 */
public final class GrowthMath {

    public static final int DEFAULT_XP_CURVE_BASE = 100;

    public static final int DEFAULT_MEOW_CURVE_OFFSET = 19;

    /*
     * 等级 / 喵阶的安全上限。
     * 与旧 Cat 实现一致，防止异常数据死循环。
     */
    private static final int MAX_LEVEL = 10000;

    private static final int MAX_RANK = 10000;

    private GrowthMath() {
        /*
         * 工具类，禁止实例化。
         */
    }

    public static int normalizeXpCurveBase(
            int curveBase
    ) {

        return curveBase <= 0
                ? DEFAULT_XP_CURVE_BASE
                : curveBase;
    }

    public static int normalizeMeowCurveOffset(
            int curveOffset
    ) {

        return curveOffset <= 0
                ? DEFAULT_MEOW_CURVE_OFFSET
                : curveOffset;
    }

    /**
     * 升到指定等级所需的累计经验。
     *
     * <p>
     * cumXp(1) = 0，cumXp(2) = base，cumXp(3) = 3×base。
     * </p>
     */
    public static long xpRequiredForLevel(
            int level,
            int curveBase
    ) {

        if (level <= 1) {
            return 0;
        }

        int base =
                normalizeXpCurveBase(curveBase);

        return (long) base
                * level
                * (level - 1L)
                / 2;
    }

    /**
     * 累计经验 → 等级。
     *
     * <p>
     * 与旧 Cat.levelFromExperience 逐行等价，
     * 含 10000 级安全上限。
     * </p>
     */
    public static int levelFromExperience(
            int totalExperience,
            int curveBase
    ) {

        int base =
                normalizeXpCurveBase(curveBase);

        int level = 1;

        int safety = 0;

        while (level < MAX_LEVEL &&
                safety < MAX_LEVEL) {

            long nextLevelRequired =
                    (long) base
                            * (level + 1L)
                            * level
                            / 2;

            if (totalExperience < nextLevelRequired) {
                break;
            }

            level++;
            safety++;
        }

        return level;
    }

    /**
     * 升到指定喵阶所需的累计喵力。
     *
     * <p>
     * cumMeow(0) = 0，cumMeow(1) = 10（offset 19），
     * cumMeow(2) = 21，cumMeow(3) = 33。
     * </p>
     */
    public static long meowRequiredForRank(
            int rank,
            int curveOffset
    ) {

        if (rank <= 0) {
            return 0;
        }

        int offset =
                normalizeMeowCurveOffset(curveOffset);

        return (long) rank
                * (rank + offset)
                / 2;
    }

    /**
     * 累计喵力 → 喵阶。
     *
     * <p>
     * 与旧 Cat.meowRankFromPower 逐行等价，
     * 含 10000 阶安全上限。
     * </p>
     */
    public static int meowRankFromPower(
            int totalMeowPower,
            int curveOffset
    ) {

        int offset =
                normalizeMeowCurveOffset(curveOffset);

        int rank = 0;

        int safety = 0;

        while (rank < MAX_RANK &&
                safety < MAX_RANK) {

            long nextRankRequired =
                    (long) (rank + 1)
                            * (rank + 1 + offset)
                            / 2;

            if (totalMeowPower < nextRankRequired) {
                break;
            }

            rank++;
            safety++;
        }

        return rank;
    }
}
