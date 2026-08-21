package mizukichou.nekonyume.cat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * 装备枚举测试（0.8.0）。
 */
class CatEquipItemTest {

    /*
     * 代码解析：全量往返 + 未知/空安全。
     */
    @Test
    void fromCodeRoundTripsAllItems() {

        for (CatEquipItem equip :
                CatEquipItem.values()) {

            assertEquals(
                    equip,
                    CatEquipItem.fromCode(
                            equip.getCode()
                    )
            );
        }

        assertNull(
                CatEquipItem.fromCode(
                        null
                )
        );

        assertNull(
                CatEquipItem.fromCode(
                        ""
                )
        );

        assertNull(
                CatEquipItem.fromCode(
                        "collar-ultra"
                )
        );
    }

    /*
     * 代码全局唯一。
     */
    @Test
    void codesAreUnique() {

        Set<String> codes = new HashSet<>();

        for (CatEquipItem equip :
                CatEquipItem.values()) {

            assertTrue(
                    codes.add(
                            equip.getCode()
                    ),
                    "重复代码: " + equip.getCode()
            );
        }
    }

    /*
     * 每个类型 × 每个品质恰好一件。
     */
    @Test
    void coversEveryTypeQualityPair() {

        Set<String> pairs = new HashSet<>();

        for (CatEquipItem equip :
                CatEquipItem.values()) {

            pairs.add(
                    equip.getType().getId()
                            + ":"
                            + equip.getQuality().name()
            );
        }

        assertEquals(
                CatEquipType.values().length
                        * MeowDanQuality.values().length,
                pairs.size()
        );
    }

    /*
     * 自定义模型数据全局唯一。
     */
    @Test
    void customModelDataUnique() {

        Set<Integer> cmds = new HashSet<>();

        for (CatEquipItem equip :
                CatEquipItem.values()) {

            assertTrue(
                    cmds.add(
                            equip.getCustomModelData()
                    )
            );
        }
    }

    /*
     * 数值与设计定稿一致：
     * 五型装备各司其职——
     * 项圈只加战斗侧、铃铛只加辅助侧、围巾只加续航侧、
     * 名牌只加成长侧、毛线球只加节奏侧。
     */
    @Test
    void statsMatchDesign() {

        for (CatEquipItem equip :
                CatEquipItem.values()) {

            assertNotNull(
                    equip.getLangKey()
            );

            switch (equip.getType()) {

                case COLLAR -> {

                    assertTrue(
                            equip.getDamageBonus() > 0,
                            equip.getCode()
                    );

                    assertEquals(0, equip.getAuraBonus());
                    assertEquals(0, equip.getMeowBonus());
                    assertEquals(0, equip.getFeedAffectionBonus());
                    assertFalse(equip.isAuraSpeed());
                    assertEquals(0, equip.getHungerSlowPercent());
                    assertEquals(0, equip.getXpBonusPercent());
                    assertEquals(0, equip.getCooldownReductionPercent());
                    assertEquals(0, equip.getAttackIntervalReductionTicks());
                    assertEquals(0, equip.getAffectionDecayReduce());
                    assertEquals(0, equip.getRegenBoostPercent());
                }

                case BELL -> {

                    assertTrue(
                            equip.getAuraBonus() > 0,
                            equip.getCode()
                    );

                    assertEquals(0, equip.getDamageBonus());
                    assertEquals(0, equip.getDamageReductionPercent());
                    assertEquals(0, equip.getLifestealPercent());
                    assertEquals(0, equip.getCatHealthBonus());
                    assertEquals(0, equip.getHungerSlowPercent());
                    assertEquals(0, equip.getXpBonusPercent());
                    assertEquals(0, equip.getCooldownReductionPercent());
                    assertEquals(0, equip.getAttackIntervalReductionTicks());
                    assertEquals(0, equip.getAffectionDecayReduce());
                    assertEquals(0, equip.getRegenBoostPercent());
                }

                case SCARF -> {

                    assertTrue(
                            equip.getHungerSlowPercent() > 0,
                            equip.getCode()
                    );

                    assertEquals(0, equip.getDamageBonus());
                    assertEquals(0, equip.getAuraBonus());
                    assertEquals(0, equip.getMeowBonus());
                    assertEquals(0, equip.getDamageReductionPercent());
                    assertEquals(0, equip.getLifestealPercent());
                    assertFalse(equip.isAuraSpeed());
                    assertEquals(0, equip.getFeedAffectionBonus());
                    assertEquals(0, equip.getXpBonusPercent());
                    assertEquals(0, equip.getCooldownReductionPercent());
                    assertEquals(0, equip.getAttackIntervalReductionTicks());
                }

                case NAME_TAG -> {

                    assertTrue(
                            equip.getXpBonusPercent() > 0,
                            equip.getCode()
                    );

                    assertEquals(0, equip.getDamageBonus());
                    assertEquals(0, equip.getAuraBonus());
                    assertEquals(0, equip.getCatHealthBonus());
                    assertEquals(0, equip.getDamageReductionPercent());
                    assertEquals(0, equip.getLifestealPercent());
                    assertFalse(equip.isAuraSpeed());
                    assertEquals(0, equip.getFeedAffectionBonus());
                    assertEquals(0, equip.getHungerSlowPercent());
                    assertEquals(0, equip.getAttackIntervalReductionTicks());
                    assertEquals(0, equip.getAffectionDecayReduce());
                    assertEquals(0, equip.getRegenBoostPercent());
                }

                case YARN_BALL -> {

                    assertTrue(
                            equip.getAttackIntervalReductionTicks() > 0,
                            equip.getCode()
                    );

                    assertEquals(0, equip.getAuraBonus());
                    assertEquals(0, equip.getCatHealthBonus());
                    assertEquals(0, equip.getDamageReductionPercent());
                    assertEquals(0, equip.getLifestealPercent());
                    assertFalse(equip.isAuraSpeed());
                    assertEquals(0, equip.getFeedAffectionBonus());
                    assertEquals(0, equip.getHungerSlowPercent());
                    assertEquals(0, equip.getXpBonusPercent());
                    assertEquals(0, equip.getCooldownReductionPercent());
                    assertEquals(0, equip.getAffectionDecayReduce());
                    assertEquals(0, equip.getRegenBoostPercent());
                }
            }
        }

        /*
         * 关键数值抽查。
         */
        assertEquals(
                6,
                CatEquipItem.COLLAR_LEGENDARY
                        .getDamageBonus()
        );

        assertEquals(
                20,
                CatEquipItem.COLLAR_LEGENDARY
                        .getDamageReductionPercent()
        );

        assertEquals(
                15,
                CatEquipItem.COLLAR_LEGENDARY
                        .getLifestealPercent()
        );

        assertEquals(
                10,
                CatEquipItem.BELL_LEGENDARY
                        .getAuraBonus()
        );

        assertEquals(
                6,
                CatEquipItem.BELL_LEGENDARY
                        .getMeowBonus()
        );

        assertTrue(
                CatEquipItem.BELL_LEGENDARY
                        .isAuraSpeed()
        );

        assertEquals(
                2,
                CatEquipItem.BELL_LEGENDARY
                        .getFeedAffectionBonus()
        );

        /*
         * 新装备（0.8.0）抽查。
         */
        assertEquals(
                30,
                CatEquipItem.SCARF_LEGENDARY
                        .getHungerSlowPercent()
        );

        assertEquals(
                30,
                CatEquipItem.SCARF_LEGENDARY
                        .getCatHealthBonus()
        );

        assertEquals(
                1,
                CatEquipItem.SCARF_LEGENDARY
                        .getAffectionDecayReduce()
        );

        assertEquals(
                100,
                CatEquipItem.SCARF_LEGENDARY
                        .getRegenBoostPercent()
        );

        assertEquals(
                30,
                CatEquipItem.NAME_TAG_LEGENDARY
                        .getXpBonusPercent()
        );

        assertEquals(
                6,
                CatEquipItem.NAME_TAG_LEGENDARY
                        .getMeowBonus()
        );

        assertEquals(
                20,
                CatEquipItem.NAME_TAG_LEGENDARY
                        .getCooldownReductionPercent()
        );

        assertEquals(
                6,
                CatEquipItem.YARN_BALL_LEGENDARY
                        .getAttackIntervalReductionTicks()
        );

        assertEquals(
                3,
                CatEquipItem.YARN_BALL_LEGENDARY
                        .getDamageBonus()
        );

        assertEquals(
                4,
                CatEquipItem.YARN_BALL_LEGENDARY
                        .getMeowBonus()
        );
    }
}
