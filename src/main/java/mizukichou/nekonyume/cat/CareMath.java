package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.config.ConfigSnapshot;

import java.util.Map;

/*
 * ============================================================
 * 0.8.0 羁绊纪元：纯数值核心
 * ============================================================
 *
 * 心情 / 羁绊对战斗、经验、技能冷却的全部倍率都集中在这里。
 *
 * 设计纪律：
 *  - 全部 static 纯函数：相同输入恒得相同输出，无副作用，可单测；
 *  - 对非法配置（null / 越界 / 负值）全部静默钳制，绝不在运行时抛异常；
 *  - 倍率下限 0.5（再差的照顾也保留一半战力，避免"废猫"体验）。
 */

public final class CareMath {

    private CareMath() {
    }

    /*
     * 羁绊档位。
     */

    public static BondTier bondFor(
            Cat cat,
            ConfigSnapshot.Care care
    ) {

        if (cat == null) {
            return BondTier.FRIEND;
        }

        return BondTier.derive(
                cat.getAffection(),
                care == null
                        ? null
                        : care.getBondTierThresholds()
        );
    }

    /*
     * 战斗伤害倍率 = 1 + 心情加成 + 羁绊加成。
     */

    public static double battleDamageMultiplier(
            CatMood mood,
            BondTier bond,
            ConfigSnapshot.Care care
    ) {

        if (care == null) {
            return 1.0;
        }

        double moodBonus =
                moodPercent(
                        care.getMoodDamagePercent(),
                        mood
                );

        double bondBonus =
                indexedPercent(
                        care.getBondDamagePercent(),
                        bond
                );

        return clampMultiplier(
                1.0
                        + moodBonus
                        + bondBonus
        );
    }

    /*
     * 经验倍率 = 1 + 心情加成 + 羁绊加成。
     */

    public static double experienceMultiplier(
            CatMood mood,
            BondTier bond,
            ConfigSnapshot.Care care
    ) {

        if (care == null) {
            return 1.0;
        }

        double moodBonus =
                moodPercent(
                        care.getMoodXpPercent(),
                        mood
                );

        double bondBonus =
                indexedPercent(
                        care.getBondXpPercent(),
                        bond
                );

        return clampMultiplier(
                1.0
                        + moodBonus
                        + bondBonus
        );
    }

    /*
     * 技能冷却系数 = 1 - 羁绊减免（下限 0.5）。
     */

    public static double cooldownFactor(
            BondTier bond,
            ConfigSnapshot.Care care
    ) {

        if (care == null) {
            return 1.0;
        }

        return Math.max(
                0.5,
                1.0
                        - indexedPercent(
                        care.getBondCooldownPercent(),
                        bond
                )
        );
    }

    /*
     * 装备冷却减免（0.8.0）：与羁绊系数相乘后钳制（下限 0.5）。
     * 纯函数（供单测）：reductionPercent 防御钳制到 [0, 90]。
     */

    public static double applyCooldownReduction(
            double factor,
            int reductionPercent
    ) {

        if (!Double.isFinite(factor) ||
                factor <= 0.0) {

            return 1.0;
        }

        int clamped =
                Math.max(
                        0,
                        Math.min(
                                90,
                                reductionPercent
                        )
                );

        if (clamped <= 0) {
            return factor;
        }

        return Math.max(
                0.5,
                factor
                        * (1.0
                        - clamped
                        / 100.0)
        );
    }

    /*
     * 治疗量缩放 = 1 + 心情伤害加成的一半（治疗对心情半敏感）。
     */

    public static double healingMultiplier(
            CatMood mood,
            ConfigSnapshot.Care care
    ) {

        if (care == null) {
            return 1.0;
        }

        double moodBonus =
                moodPercent(
                        care.getMoodDamagePercent(),
                        mood
                );

        return clampMultiplier(
                1.0
                        + moodBonus
                        * 0.5
        );
    }

    /*
     * 应用经验倍率（四舍五入，保底 1）。
     */

    public static int applyExperience(
            int amount,
            double multiplier
    ) {

        if (amount <= 0) {
            return 0;
        }

        /*
         * 0.8.4 R21（社区上报 M-NEW-06）：
         * long 数学 + 饱和钳制——int 计算在极端经验/倍率下
         * 溢出为负，Math.max(1,负值) 会把巨额经验错误地变成 1。
         */
        long scaled =
                Math.round(
                        amount
                                * clampMultiplier(
                                multiplier
                        )
                );

        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(
                        1L,
                        scaled
                )
        );
    }

    /*
     * 应用伤害倍率（四舍五入，保底 1）。
     *
     * <p>
     * 注意：这里不再二次锤制倍率——调用方传入的
     * battleDamageMultiplier 已锤制到 [0.5, 3.0]；
     * 极端输入（NaN/负值）由保底 1 兜底。
     * </p>
     */

    public static int applyDamage(
            int damage,
            double multiplier
    ) {

        if (damage <= 0) {
            return 0;
        }

        /*
         * 0.8.4 R21（社区上报 M-NEW-07）：
         * long 数学 + 饱和钳制（同 applyExperience）。
         */
        long scaled =
                Math.round(
                        damage * multiplier
                );

        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(
                        1L,
                        scaled
                )
        );
    }

    /*
     * 百分比表取值（档位下标 = 羁绊枚举下标）。
     */

    private static double indexedPercent(
            java.util.List<Integer> table,
            BondTier bond
    ) {

        if (table == null ||
                table.isEmpty() ||
                bond == null) {

            return 0.0;
        }

        int index = bond.ordinal();

        if (index >= table.size()) {
            return 0.0;
        }

        Integer value = table.get(index);

        return percent(
                value == null
                        ? 0.0
                        : value.doubleValue()
        );
    }

    /*
     * 心情表取值：表/心情为 null 时视为 0（防御 EnumMap 对 null 键抛异常）。
     */

    private static double moodPercent(
            Map<CatMood, Double> table,
            CatMood mood
    ) {

        if (table == null ||
                mood == null) {

            return 0.0;
        }

        Double value =
                table.get(
                        mood
                );

        return percent(
                value == null
                        ? 0.0
                        : value
        );
    }

    /*
     * 整数百分比 → 小数（钳制 ±100%）。
     */

    private static double percent(
            double value
    ) {

        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return Math.max(
                -1.0,
                Math.min(
                        1.0,
                        value
                                / 100.0
                )
        );
    }

    /*
     * 倍率钳制：最低 0.5、最高 3.0。
     */

    private static double clampMultiplier(
            double multiplier
    ) {

        if (!Double.isFinite(multiplier)) {
            return 1.0;
        }

        return Math.max(
                0.5,
                Math.min(
                        3.0,
                        multiplier
                )
        );
    }
}
