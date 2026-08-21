package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * 装备附加属性（0.8.0 至宝觉醒）测试：
 * 属性池完整性、抽取纯函数、炫彩渲染。
 */
class EquipBonusAttributeTest {

    /*
     * 代码全局唯一。
     */
    @Test
    void codesAreUnique() {

        Set<String> codes = new HashSet<>();

        for (EquipBonusAttribute bonus :
                EquipBonusAttribute.values()) {

            assertTrue(
                    codes.add(
                            bonus.getCode()
                    ),
                    "重复代码: " + bonus.getCode()
            );
        }
    }

    /*
     * 代码解析：全量往返 + 未知/空安全。
     */
    @Test
    void fromCodeRoundTripsAllItems() {

        for (EquipBonusAttribute bonus :
                EquipBonusAttribute.values()) {

            assertEquals(
                    bonus,
                    EquipBonusAttribute.fromCode(
                            bonus.getCode()
                    )
            );
        }

        assertNull(
                EquipBonusAttribute.fromCode(
                        null
                )
        );

        assertNull(
                EquipBonusAttribute.fromCode(
                        ""
                )
        );

        assertNull(
                EquipBonusAttribute.fromCode(
                        "ultra"
                )
        );
    }

    /*
     * 每个附加属性恰好激活一个维度，
     * 数值上限封顶（不强的离谱）。
     */
    @Test
    void eachBonusActivatesExactlyOneBoundedStat() {

        for (EquipBonusAttribute bonus :
                EquipBonusAttribute.values()) {

            assertEquals(
                    1,
                    activeDimensionCount(
                            bonus
                    ),
                    bonus.getCode()
            );

            assertTrue(
                    bonus.getDisplayValue() >= 1,
                    bonus.getCode()
            );

            assertTrue(
                    bonus.getDisplayValue() <= 30,
                    bonus.getCode()
            );
        }
    }

    /*
     * displayValue 与唯一激活维度一致。
     */
    @Test
    void displayValueMatchesActiveDimension() {

        assertEquals(
                4,
                EquipBonusAttribute.STARLIGHT
                        .getDisplayValue()
        );

        assertEquals(
                8,
                EquipBonusAttribute.BLOODMOON
                        .getDisplayValue()
        );

        assertEquals(
                8,
                EquipBonusAttribute.UNYIELDING
                        .getDisplayValue()
        );

        assertEquals(
                15,
                EquipBonusAttribute.TIMEFLOW
                        .getDisplayValue()
        );

        assertEquals(
                20,
                EquipBonusAttribute.AVARICE
                        .getDisplayValue()
        );

        assertEquals(
                5,
                EquipBonusAttribute.RESONANCE
                        .getDisplayValue()
        );

        assertEquals(
                20,
                EquipBonusAttribute.HEARTH
                        .getDisplayValue()
        );

        assertEquals(
                3,
                EquipBonusAttribute.SHADOWSTEP
                        .getDisplayValue()
        );
    }

    /*
     * 语言键与描述键格式。
     */
    @Test
    void langKeysFollowContract() {

        for (EquipBonusAttribute bonus :
                EquipBonusAttribute.values()) {

            assertEquals(
                    "equip-bonus-name."
                            + bonus.getCode(),
                    bonus.getLangKey()
            );

            assertEquals(
                    "equip-bonus-desc."
                            + bonus.getCode(),
                    bonus.getDescKey()
            );
        }
    }

    /*
     * rolls 纯函数边界。
     */
    @Test
    void rollsRespectsPercentBoundary() {

        Random hit =
                fixedRandom(
                        19
                );

        Random boundaryMiss =
                fixedRandom(
                        20
                );

        Random miss =
                fixedRandom(
                        50
                );

        assertTrue(
                EquipBonusAttribute.rolls(
                        hit,
                        20
                )
        );

        assertFalse(
                EquipBonusAttribute.rolls(
                        boundaryMiss,
                        20
                )
        );

        assertFalse(
                EquipBonusAttribute.rolls(
                        miss,
                        20
                )
        );
    }

    /*
     * roll：命中路径从池内均匀抽取；未命中返回 null；
     * null Random 安全返回 null。
     */
    @Test
    void rollHitsAndMisses() {

        Random hit =
                fixedRandom(
                        0
                );

        assertEquals(
                EquipBonusAttribute.STARLIGHT,
                EquipBonusAttribute.roll(
                        hit
                )
        );

        Random miss =
                fixedRandom(
                        50
                );

        assertNull(
                EquipBonusAttribute.roll(
                        miss
                )
        );

        assertNull(
                EquipBonusAttribute.roll(
                        null
                )
        );
    }

    /*
     * pick 永远落在池内。
     */
    @Test
    void pickStaysInPool() {

        for (int value = 0;
             value < EquipBonusAttribute.values().length
                     * 10;
             value++) {

            EquipBonusAttribute picked =
                    EquipBonusAttribute.pick(
                            fixedRandom(
                                    value
                                            % EquipBonusAttribute
                                            .values()
                                            .length
                            )
                    );

            assertNotNull(
                    picked
            );
        }
    }

    /*
     * 炫彩渲染：逐字符色码、字符保留、空/null 安全。
     */
    @Test
    void rainbowColorsEveryCharacter() {

        assertEquals(
                "§cA§6B§eC",
                EquipBonusAttribute.rainbow(
                        "ABC"
                )
        );

        assertEquals(
                "",
                EquipBonusAttribute.rainbow(
                        ""
                )
        );

        assertEquals(
                "",
                EquipBonusAttribute.rainbow(
                        null
                )
        );
    }

    private int activeDimensionCount(
            EquipBonusAttribute bonus
    ) {

        int count = 0;

        if (bonus.getDamageBonus() > 0) {
            count++;
        }

        if (bonus.getLifestealPercent() > 0) {
            count++;
        }

        if (bonus.getDamageReductionPercent() > 0) {
            count++;
        }

        if (bonus.getCooldownReductionPercent() > 0) {
            count++;
        }

        if (bonus.getXpBonusPercent() > 0) {
            count++;
        }

        if (bonus.getMeowBonus() > 0) {
            count++;
        }

        if (bonus.getHungerSlowPercent() > 0) {
            count++;
        }

        if (bonus.getAttackIntervalReductionTicks() > 0) {
            count++;
        }

        return count;
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
}
