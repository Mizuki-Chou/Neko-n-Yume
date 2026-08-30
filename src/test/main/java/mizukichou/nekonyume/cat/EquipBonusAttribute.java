package mizukichou.nekonyume.cat;

import lombok.Getter;

import java.util.Random;

/*
 * ============================================================
 * 装备附加属性（0.8.0，至极装备的至宝觉醒）
 * ============================================================
 *
 * 至极品质的装备在「获得的一瞬间」（发放面板 / 未来任何获取途径，
 * 不含扔出后捡起）有 20% 概率觉醒一个附加属性：
 * 从本属性池均匀抽取一个，显示为一行炫彩色 lore。
 *
 * 每个附加属性只激活一个维度，数值力求强大但不过分：
 *   星辉  近战伤害 +4
 *   血月  吸血 8%（治疗猫自身）
 *   不朽  受伤减免 8%
 *   时流  技能冷却 -15%
 *   贪婪  经验获取 +20%
 *   共鸣  喵力概率 +5%
 *   暖炉  饥饿衰减减缓 20%
 *   迅影  攻击间隔 -3 tick
 *
 * 附加属性随装备位持久化（equipment-bonus 字段）：
 * 穿戴生效、替换/卸下时随物品归还，扔出捡回不会重roll。
 */
@Getter
public enum EquipBonusAttribute {

    STARLIGHT(
            "starlight",
            4, 0, 0, 0, 0, 0, 0, 0
    ),

    BLOODMOON(
            "bloodmoon",
            0, 8, 0, 0, 0, 0, 0, 0
    ),

    UNYIELDING(
            "unyielding",
            0, 0, 8, 0, 0, 0, 0, 0
    ),

    TIMEFLOW(
            "timeflow",
            0, 0, 0, 15, 0, 0, 0, 0
    ),

    AVARICE(
            "avarice",
            0, 0, 0, 0, 20, 0, 0, 0
    ),

    RESONANCE(
            "resonance",
            0, 0, 0, 0, 0, 5, 0, 0
    ),

    HEARTH(
            "hearth",
            0, 0, 0, 0, 0, 0, 20, 0
    ),

    SHADOWSTEP(
            "shadowstep",
            0, 0, 0, 0, 0, 0, 0, 3
    );

    /*
     * 觉醒概率（百分）。
     */
    public static final int ROLL_PERCENT = 20;

    /*
     * 持久化/PDC 代码，例如 "starlight"。
     */
    private final String code;

    /*
     * 近战伤害加成。
     */
    private final int damageBonus;

    /*
     * 吸血（百分点）。
     */
    private final int lifestealPercent;

    /*
     * 受伤减免（百分点，与技能/装备减免相乘）。
     */
    private final int damageReductionPercent;

    /*
     * 技能冷却减免（百分点，与羁绊/装备减免相乘后钳制）。
     */
    private final int cooldownReductionPercent;

    /*
     * 经验获取加成（百分点）。
     */
    private final int xpBonusPercent;

    /*
     * 喵力触发概率加成（百分点）。
     */
    private final int meowBonus;

    /*
     * 饥饿衰减减缓（百分点）。
     */
    private final int hungerSlowPercent;

    /*
     * 攻击间隔缩减（tick）。
     */
    private final int attackIntervalReductionTicks;

    EquipBonusAttribute(
            String code,
            int damageBonus,
            int lifestealPercent,
            int damageReductionPercent,
            int cooldownReductionPercent,
            int xpBonusPercent,
            int meowBonus,
            int hungerSlowPercent,
            int attackIntervalReductionTicks
    ) {

        this.code = code;
        this.damageBonus = damageBonus;
        this.lifestealPercent = lifestealPercent;
        this.damageReductionPercent = damageReductionPercent;
        this.cooldownReductionPercent = cooldownReductionPercent;
        this.xpBonusPercent = xpBonusPercent;
        this.meowBonus = meowBonus;
        this.hungerSlowPercent = hungerSlowPercent;
        this.attackIntervalReductionTicks = attackIntervalReductionTicks;
    }

    /*
     * 语言键：equip-bonus-name.<code>（名称）。
     */
    public String getLangKey() {

        return "equip-bonus-name." + code;
    }

    /*
     * 语言键：equip-bonus-desc.<code>（效果描述，含 {0} 数值位）。
     */
    public String getDescKey() {

        return "equip-bonus-desc." + code;
    }

    /*
     * 唯一激活维度的数值（描述模板的 {0} 参数）。
     */
    public int getDisplayValue() {

        if (damageBonus > 0) {
            return damageBonus;
        }

        if (lifestealPercent > 0) {
            return lifestealPercent;
        }

        if (damageReductionPercent > 0) {
            return damageReductionPercent;
        }

        if (cooldownReductionPercent > 0) {
            return cooldownReductionPercent;
        }

        if (xpBonusPercent > 0) {
            return xpBonusPercent;
        }

        if (meowBonus > 0) {
            return meowBonus;
        }

        if (hungerSlowPercent > 0) {
            return hungerSlowPercent;
        }

        return attackIntervalReductionTicks;
    }

    /*
     * 按代码解析；未知/空返回 null。
     */
    public static EquipBonusAttribute fromCode(
            String code
    ) {

        if (code == null ||
                code.isBlank()) {

            return null;
        }

        for (EquipBonusAttribute bonus :
                values()) {

            if (bonus.code.equalsIgnoreCase(
                    code
            )) {

                return bonus;
            }
        }

        return null;
    }

    /*
     * ============================================================
     * 觉醒抽取
     * ============================================================
     */

    /*
     * 觉醒判定：percent 概率返回 true（纯函数，供单测）。
     */
    static boolean rolls(
            Random random,
            int percent
    ) {

        return random.nextInt(100) < percent;
    }

    /*
     * 从属性池均匀抽取一个。
     */
    static EquipBonusAttribute pick(
            Random random
    ) {

        EquipBonusAttribute[] all =
                values();

        return all[random.nextInt(all.length)];
    }

    /*
     * 觉醒 roll：ROLL_PERCENT 概率命中；
     * 命中后从池中均匀抽取，未命中返回 null。
     */
    public static EquipBonusAttribute roll(
            Random random
    ) {

        if (random == null) {
            return null;
        }

        if (!rolls(
                random,
                ROLL_PERCENT
        )) {

            return null;
        }

        return pick(
                random
        );
    }

    /*
     * ============================================================
     * 炫彩渲染
     * ============================================================
     */

    private static final String[] RAINBOW_PALETTE = {
            "§c", "§6", "§e", "§a", "§b", "§9", "§d"
    };

    /*
     * 逐字符循环色码的炫彩文本（纯函数，供单测）。
     */
    public static String rainbow(
            String text
    ) {

        if (text == null ||
                text.isEmpty()) {

            return "";
        }

        StringBuilder builder =
                new StringBuilder(
                        text.length() * 4
                );

        int index = 0;

        for (int i = 0;
             i < text.length();
             i++) {

            builder.append(
                    RAINBOW_PALETTE[index
                            % RAINBOW_PALETTE.length]
            );

            builder.append(
                    text.charAt(i)
            );

            index++;
        }

        return builder.toString();
    }
}
