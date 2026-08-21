package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * 猫猫装备袋（0.8.0）概率纯函数测试：
 * 品质权重边界（40/30/20/7.5/2.5）、类型均匀抽取、
 * 配置驱动掉落（rollsChance / pickQualityByWeights）。
 */
class EquipBagOddsTest {

    /*
     * 权重总和恒为 1000（千分比口径）。
     */
    @Test
    void qualityWeightsSumToTotal() {

        int sum = 0;

        for (int weight :
                EquipBagOdds.QUALITY_WEIGHTS) {

            sum += weight;
        }

        assertEquals(
                EquipBagOdds.QUALITY_TOTAL_WEIGHT,
                sum
        );
    }

    /*
     * 品质边界（40% / 30% / 20% / 7.5% / 2.5%）：
     * 每个桶的上下边界逐一验证。
     */
    @Test
    void qualityBoundaries() {

        assertEquals(
                MeowDanQuality.COMMON,
                pickWith(
                        0
                )
        );

        assertEquals(
                MeowDanQuality.COMMON,
                pickWith(
                        399
                )
        );

        assertEquals(
                MeowDanQuality.UNCOMMON,
                pickWith(
                        400
                )
        );

        assertEquals(
                MeowDanQuality.UNCOMMON,
                pickWith(
                        699
                )
        );

        assertEquals(
                MeowDanQuality.RARE,
                pickWith(
                        700
                )
        );

        assertEquals(
                MeowDanQuality.RARE,
                pickWith(
                        899
                )
        );

        assertEquals(
                MeowDanQuality.EPIC,
                pickWith(
                        900
                )
        );

        assertEquals(
                MeowDanQuality.EPIC,
                pickWith(
                        974
                )
        );

        assertEquals(
                MeowDanQuality.LEGENDARY,
                pickWith(
                        975
                )
        );

        assertEquals(
                MeowDanQuality.LEGENDARY,
                pickWith(
                        999
                )
        );
    }

    /*
     * 梦魔夜权重（80/16/3/1/0）边界：
     * 平凡 [0,80)、精良 [80,96)、独特 [96,99)、卓越 99；
     * 至极权重 0 → 永不出现。
     */
    @Test
    void mumaWeightsBoundaries() {

        int[] weights =
                {80, 16, 3, 1, 0};

        assertEquals(
                MeowDanQuality.COMMON,
                pickByWeights(
                        weights,
                        0
                )
        );

        assertEquals(
                MeowDanQuality.COMMON,
                pickByWeights(
                        weights,
                        79
                )
        );

        assertEquals(
                MeowDanQuality.UNCOMMON,
                pickByWeights(
                        weights,
                        80
                )
        );

        assertEquals(
                MeowDanQuality.UNCOMMON,
                pickByWeights(
                        weights,
                        95
                )
        );

        assertEquals(
                MeowDanQuality.RARE,
                pickByWeights(
                        weights,
                        96
                )
        );

        assertEquals(
                MeowDanQuality.RARE,
                pickByWeights(
                        weights,
                        98
                )
        );

        assertEquals(
                MeowDanQuality.EPIC,
                pickByWeights(
                        weights,
                        99
                )
        );

        for (int roll = 0;
             roll < 100;
             roll++) {

            assertNotEquals(
                    MeowDanQuality.LEGENDARY,
                    pickByWeights(
                            weights,
                            roll
                    )
            );
        }
    }

    /*
     * 平时权重（80/16/3/0/0）：
     * 只可能出前三种，卓越/至极永不出现。
     */
    @Test
    void generalWeightsOnlyFirstThree() {

        int[] weights =
                {80, 16, 3, 0, 0};

        for (int roll = 0;
             roll < 99;
             roll++) {

            MeowDanQuality quality =
                    pickByWeights(
                            weights,
                            roll
                    );

            assertNotEquals(
                    MeowDanQuality.EPIC,
                    quality
            );

            assertNotEquals(
                    MeowDanQuality.LEGENDARY,
                    quality
            );
        }

        assertEquals(
                MeowDanQuality.RARE,
                pickByWeights(
                        weights,
                        98
                )
        );
    }

    /*
     * 非法权重安全：null / 空 / 全零 / null Random 一律 null。
     */
    @Test
    void invalidWeightsSafe() {

        assertNull(
                EquipBagOdds.pickQualityByWeights(
                        fixedRandom(
                                0
                        ),
                        null
                )
        );

        assertNull(
                EquipBagOdds.pickQualityByWeights(
                        fixedRandom(
                                0
                        ),
                        new int[0]
                )
        );

        assertNull(
                EquipBagOdds.pickQualityByWeights(
                        fixedRandom(
                                0
                        ),
                        new int[]{
                                0,
                                0,
                                0,
                                0,
                                0
                        }
                )
        );

        assertNull(
                EquipBagOdds.pickQualityByWeights(
                        null,
                        new int[]{
                                1
                        }
                )
        );
    }

    /*
     * rollsChance 边界：nextDouble() < chance 才命中；
     * chance <= 0 或 null Random 恒为 false。
     */
    @Test
    void rollsChanceBoundaries() {

        assertTrue(
                EquipBagOdds.rollsChance(
                        doubleRandom(
                                0.0
                        ),
                        0.02
                )
        );

        assertTrue(
                EquipBagOdds.rollsChance(
                        doubleRandom(
                                0.019999
                        ),
                        0.02
                )
        );

        assertFalse(
                EquipBagOdds.rollsChance(
                        doubleRandom(
                                0.02
                        ),
                        0.02
                )
        );

        assertFalse(
                EquipBagOdds.rollsChance(
                        doubleRandom(
                                0.5
                        ),
                        0.02
                )
        );

        assertFalse(
                EquipBagOdds.rollsChance(
                        doubleRandom(
                                0.0
                        ),
                        0.0
                )
        );

        assertFalse(
                EquipBagOdds.rollsChance(
                        null,
                        0.02
                )
        );
    }

    /*
     * 类型均匀抽取：首/尾边界 + 始终在池内。
     */
    @Test
    void typePickBoundaries() {

        assertEquals(
                CatEquipType.COLLAR,
                EquipBagOdds.pickType(
                        fixedRandom(
                                0
                        )
                )
        );

        assertEquals(
                CatEquipType.YARN_BALL,
                EquipBagOdds.pickType(
                        fixedRandom(
                                CatEquipType.values().length - 1
                        )
                )
        );

        for (int value = 0;
             value < CatEquipType.values().length * 10;
             value++) {

            assertNotNull(
                    EquipBagOdds.pickType(
                            fixedRandom(
                                    value
                                            % CatEquipType.values().length
                            )
                    )
            );
        }
    }

    /*
     * null Random 安全：全部返回 null / false。
     */
    @Test
    void nullRandomSafe() {

        assertNull(
                EquipBagOdds.pickQuality(
                        null
                )
        );

        assertNull(
                EquipBagOdds.pickType(
                        null
                )
        );

        assertFalse(
                EquipBagOdds.rollsChance(
                        null,
                        0.02
                )
        );

        assertNull(
                EquipBagOdds.pickQualityByWeights(
                        null,
                        new int[]{
                                1
                        }
                )
        );
    }

    /*
     * 装备袋抽取的（类型，品质）组合必然命中唯一条目。
     */
    @Test
    void everyDrawResolvesToEquipment() {

        for (CatEquipType type :
                CatEquipType.values()) {

            for (MeowDanQuality quality :
                    MeowDanQuality.values()) {

                CatEquipItem equip =
                        CatEquipItem.of(
                                type,
                                quality
                        );

                assertNotNull(
                        equip,
                        type + "/" + quality
                );

                assertEquals(
                        type,
                        equip.getType()
                );

                assertEquals(
                        quality,
                        equip.getQuality()
                );
            }
        }
    }

    /*
     * of() 非法输入安全。
     */
    @Test
    void ofRejectsInvalidInput() {

        assertNull(
                CatEquipItem.of(
                        null,
                        MeowDanQuality.COMMON
                )
        );

        assertNull(
                CatEquipItem.of(
                        CatEquipType.COLLAR,
                        null
                )
        );

        assertNull(
                CatEquipItem.of(
                        null,
                        null
                )
        );
    }

    private MeowDanQuality pickWith(
            int roll
    ) {

        return EquipBagOdds.pickQuality(
                fixedRandom(
                        roll
                )
        );
    }

    private MeowDanQuality pickByWeights(
            int[] weights,
            int roll
    ) {

        return EquipBagOdds.pickQualityByWeights(
                fixedRandom(
                        roll
                ),
                weights
        );
    }

    private Random fixedRandom(
            int value
    ) {

        return new Random() {

            @Override
            public int nextInt(
                    int bound
            ) {

                return value;
            }
        };
    }

    private Random doubleRandom(
            double value
    ) {

        return new Random() {

            @Override
            public double nextDouble() {

                return value;
            }
        };
    }
}
