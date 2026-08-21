package mizukichou.nekonyume.cat;

import java.util.Random;

/**
 * 猫猫装备袋（0.8.0）概率纯函数。
 *
 * <p>
 * 右键打开时按品质权重抽取一件装备：
 * 平凡 40% / 精良 30% / 独特 20% / 卓越 7.5% / 至极 2.5%
 * （千分比权重，总和 1000，与品质声明顺序对齐）。
 * 掉落概率由 config.yml 的 drops 节提供（0.8.0 起），
 * 由 {@link #rollsChance(Random, double)} 与
 * {@link #pickQualityByWeights(Random, int[])} 实现。
 * </p>
 *
 * <p>
 * 全部方法为纯函数（Random 注入、无副作用），供单元测试逐边界验证；
 * null Random 一律返回 null / false，绝不抛异常。
 * </p>
 */
public final class EquipBagOdds {

    /*
     * 品质权重（千分比，按 MeowDanQuality 声明顺序：
     * 平凡 → 精良 → 独特 → 卓越 → 至极）。
     */
    public static final int[] QUALITY_WEIGHTS =
            {
                    400,
                    300,
                    200,
                    75,
                    25
            };

    /*
     * 品质权重总和。
     */
    public static final int QUALITY_TOTAL_WEIGHT = 1000;

    private EquipBagOdds() {
    }

    /*
     * 概率判定（0~1 单位值，来自 config.yml 的 drops 节）：
     * nextDouble() < chance。
     * 防御：chance <= 0 或 null Random 一律 false。
     */
    public static boolean rollsChance(
            Random random,
            double chance
    ) {

        if (random == null ||
                !(chance > 0.0)) {

            return false;
        }

        return random.nextDouble() < chance;
    }

    /*
     * 按配置权重抽取品质（相对权重，自动归一）。
     *
     * 边界（权重 [80, 16, 3, 1, 0]）：
     * [0, 80) 平凡；[80, 96) 精良；[96, 99) 独特；
     * [99, 100) 卓越；至极权重为 0 永不命中。
     *
     * 防御：全零 / 空权重 / null 一律返回 null，绝不出界。
     */
    public static MeowDanQuality pickQualityByWeights(
            Random random,
            int[] weights
    ) {

        int index =
                pickIndex(
                        random,
                        weights
                );

        if (index < 0) {
            return null;
        }

        MeowDanQuality[] values =
                MeowDanQuality.values();

        return index < values.length
                ? values[index]
                : null;
    }

    /*
     * 加权索引抽取（纯函数，供测试）。
     */
    static int pickIndex(
            Random random,
            int[] weights
    ) {

        if (random == null ||
                weights == null ||
                weights.length == 0) {

            return -1;
        }

        int total = 0;

        for (int weight : weights) {

            if (weight > 0) {

                total += weight;
            }
        }

        if (total <= 0) {
            return -1;
        }

        int roll =
                random.nextInt(
                        total
                );

        int cumulative = 0;

        for (int i = 0;
             i < weights.length;
             i++) {

            cumulative += Math.max(
                    0,
                    weights[i]
            );

            if (roll < cumulative) {

                return i;
            }
        }

        return weights.length - 1;
    }

    /*
     * 按权重抽取品质。
     *
     * 边界（千分位）：
     * [0, 400) 平凡；[400, 700) 精良；[700, 900) 独特；
     * [900, 975) 卓越；[975, 1000) 至极。
     *
     * 防御：权重总和不足 / 声明数量不匹配时，
     * 溢出部分自然落入最高品质，绝不出界。
     */
    public static MeowDanQuality pickQuality(
            Random random
    ) {

        if (random == null) {
            return null;
        }

        MeowDanQuality[] values =
                MeowDanQuality.values();

        if (values.length == 0) {
            return null;
        }

        int total = 0;

        for (int i = 0;
             i < QUALITY_WEIGHTS.length;
             i++) {

            total += QUALITY_WEIGHTS[i];
        }

        if (total <= 0) {
            return null;
        }

        int roll =
                random.nextInt(
                        total
                );

        int cumulative = 0;

        for (int i = 0;
             i < values.length;
             i++) {

            int weight =
                    i < QUALITY_WEIGHTS.length
                            ? QUALITY_WEIGHTS[i]
                            : 0;

            cumulative += weight;

            if (roll < cumulative) {

                return values[i];
            }
        }

        return values[
                values.length - 1
                ];
    }

    /*
     * 均匀抽取装备类型（五型：项圈/铃铛/围巾/名牌/毛线球）。
     */
    public static CatEquipType pickType(
            Random random
    ) {

        if (random == null) {
            return null;
        }

        CatEquipType[] values =
                CatEquipType.values();

        if (values.length == 0) {
            return null;
        }

        return values[
                random.nextInt(
                        values.length
                )
                ];
    }
}
