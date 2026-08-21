package mizukichou.nekonyume.cat;

import lombok.Getter;

/*
 * ============================================================
 * 装备（0.8.0 装备系统）
 * ============================================================
 *
 * 每只猫只有一个装备位：五型装备互斥。
 *
 * 数值定稿（低稀有度 = 小量数值，高稀有度 = 追加被动）：
 *
 * 项圈（战斗向）：
 *   平凡  +1 近战伤害
 *   精良  +2 近战伤害
 *   独特  +3 近战伤害，猫生命上限 +10
 *   卓越  +4 近战伤害，猫生命上限 +20，受伤 -10%
 *   至极  +6 近战伤害，猫生命上限 +30，受伤 -20%，猫攻击 15% 吸血自愈
 *
 * 铃铛（辅助向）：
 *   平凡  光环半径 +2
 *   精良  光环半径 +4
 *   独特  光环半径 +6，喵力概率 +2%
 *   卓越  光环半径 +8，喵力概率 +4%，光环内主人加速 I
 *   至极  光环半径 +10，喵力概率 +6%，光环内主人加速 I，喂食好感 +2
 *
 * 围巾（陪伴续航向）：
 *   平凡  饥饿衰减减缓 5%
 *   精良  饥饿衰减减缓 10%
 *   独特  饥饿衰减减缓 15%，猫生命上限 +10
 *   卓越  饥饿衰减减缓 20%，猫生命上限 +20，每日好感衰减 -1
 *   至极  饥饿衰减减缓 30%，猫生命上限 +30，每日好感衰减 -1，
 *         缓慢回血 +100%（每次 2 点）
 *
 * 名牌（成长向）：
 *   平凡  经验获取 +5%
 *   精良  经验获取 +10%
 *   独特  经验获取 +15%，喵力概率 +2%
 *   卓越  经验获取 +20%，喵力概率 +4%，技能冷却 -10%
 *   至极  经验获取 +30%，喵力概率 +6%，技能冷却 -20%
 *
 * 毛线球（战斗节奏向）：
 *   平凡  攻击间隔 -1 tick
 *   精良  攻击间隔 -2 tick
 *   独特  攻击间隔 -3 tick，近战伤害 +1
 *   卓越  攻击间隔 -4 tick，近战伤害 +2，喵力概率 +2%
 *   至极  攻击间隔 -6 tick，近战伤害 +3，喵力概率 +4%
 *
 * 本版本不提供任何获得途径：仅 /nekonyumeadmin give 管理面板发放。
 *
 * 品质与喵丹共用 MeowDanQuality（平凡/精良/独特/卓越/至极）。
 */
@Getter
public enum CatEquipItem {

    /*
     * ============================================================
     * 项圈
     * ============================================================
     */
    COLLAR_COMMON(
            "collar-common",
            CatEquipType.COLLAR,
            MeowDanQuality.COMMON,
            1, 0, 0, 0, 0, 0,
            false, 0,
            0, 0, 0, 0, 0, 0
    ),

    COLLAR_UNCOMMON(
            "collar-uncommon",
            CatEquipType.COLLAR,
            MeowDanQuality.UNCOMMON,
            2, 0, 0, 0, 0, 0,
            false, 0,
            0, 0, 0, 0, 0, 0
    ),

    COLLAR_RARE(
            "collar-rare",
            CatEquipType.COLLAR,
            MeowDanQuality.RARE,
            3, 0, 0, 10, 0, 0,
            false, 0,
            0, 0, 0, 0, 0, 0
    ),

    COLLAR_EPIC(
            "collar-epic",
            CatEquipType.COLLAR,
            MeowDanQuality.EPIC,
            4, 0, 0, 20, 10, 0,
            false, 0,
            0, 0, 0, 0, 0, 0
    ),

    COLLAR_LEGENDARY(
            "collar-legendary",
            CatEquipType.COLLAR,
            MeowDanQuality.LEGENDARY,
            6, 0, 0, 30, 20, 15,
            false, 0,
            0, 0, 0, 0, 0, 0
    ),

    /*
     * ============================================================
     * 铃铛
     * ============================================================
     */
    BELL_COMMON(
            "bell-common",
            CatEquipType.BELL,
            MeowDanQuality.COMMON,
            0, 2, 0, 0, 0, 0,
            false, 0,
            0, 0, 0, 0, 0, 0
    ),

    BELL_UNCOMMON(
            "bell-uncommon",
            CatEquipType.BELL,
            MeowDanQuality.UNCOMMON,
            0, 4, 0, 0, 0, 0,
            false, 0,
            0, 0, 0, 0, 0, 0
    ),

    BELL_RARE(
            "bell-rare",
            CatEquipType.BELL,
            MeowDanQuality.RARE,
            0, 6, 2, 0, 0, 0,
            false, 0,
            0, 0, 0, 0, 0, 0
    ),

    BELL_EPIC(
            "bell-epic",
            CatEquipType.BELL,
            MeowDanQuality.EPIC,
            0, 8, 4, 0, 0, 0,
            true, 0,
            0, 0, 0, 0, 0, 0
    ),

    BELL_LEGENDARY(
            "bell-legendary",
            CatEquipType.BELL,
            MeowDanQuality.LEGENDARY,
            0, 10, 6, 0, 0, 0,
            true, 2,
            0, 0, 0, 0, 0, 0
    ),

    /*
     * ============================================================
     * 围巾
     * ============================================================
     */
    SCARF_COMMON(
            "scarf-common",
            CatEquipType.SCARF,
            MeowDanQuality.COMMON,
            0, 0, 0, 0, 0, 0,
            false, 0,
            5, 0, 0, 0, 0, 0
    ),

    SCARF_UNCOMMON(
            "scarf-uncommon",
            CatEquipType.SCARF,
            MeowDanQuality.UNCOMMON,
            0, 0, 0, 0, 0, 0,
            false, 0,
            10, 0, 0, 0, 0, 0
    ),

    SCARF_RARE(
            "scarf-rare",
            CatEquipType.SCARF,
            MeowDanQuality.RARE,
            0, 0, 0, 10, 0, 0,
            false, 0,
            15, 0, 0, 0, 0, 0
    ),

    SCARF_EPIC(
            "scarf-epic",
            CatEquipType.SCARF,
            MeowDanQuality.EPIC,
            0, 0, 0, 20, 0, 0,
            false, 0,
            20, 0, 0, 0, 1, 0
    ),

    SCARF_LEGENDARY(
            "scarf-legendary",
            CatEquipType.SCARF,
            MeowDanQuality.LEGENDARY,
            0, 0, 0, 30, 0, 0,
            false, 0,
            30, 0, 0, 0, 1, 100
    ),

    /*
     * ============================================================
     * 名牌
     * ============================================================
     */
    NAME_TAG_COMMON(
            "name-tag-common",
            CatEquipType.NAME_TAG,
            MeowDanQuality.COMMON,
            0, 0, 0, 0, 0, 0,
            false, 0,
            0, 5, 0, 0, 0, 0
    ),

    NAME_TAG_UNCOMMON(
            "name-tag-uncommon",
            CatEquipType.NAME_TAG,
            MeowDanQuality.UNCOMMON,
            0, 0, 0, 0, 0, 0,
            false, 0,
            0, 10, 0, 0, 0, 0
    ),

    NAME_TAG_RARE(
            "name-tag-rare",
            CatEquipType.NAME_TAG,
            MeowDanQuality.RARE,
            0, 0, 2, 0, 0, 0,
            false, 0,
            0, 15, 0, 0, 0, 0
    ),

    NAME_TAG_EPIC(
            "name-tag-epic",
            CatEquipType.NAME_TAG,
            MeowDanQuality.EPIC,
            0, 0, 4, 0, 0, 0,
            false, 0,
            0, 20, 10, 0, 0, 0
    ),

    NAME_TAG_LEGENDARY(
            "name-tag-legendary",
            CatEquipType.NAME_TAG,
            MeowDanQuality.LEGENDARY,
            0, 0, 6, 0, 0, 0,
            false, 0,
            0, 30, 20, 0, 0, 0
    ),

    /*
     * ============================================================
     * 毛线球
     * ============================================================
     */
    YARN_BALL_COMMON(
            "yarn-ball-common",
            CatEquipType.YARN_BALL,
            MeowDanQuality.COMMON,
            0, 0, 0, 0, 0, 0,
            false, 0,
            0, 0, 0, 1, 0, 0
    ),

    YARN_BALL_UNCOMMON(
            "yarn-ball-uncommon",
            CatEquipType.YARN_BALL,
            MeowDanQuality.UNCOMMON,
            0, 0, 0, 0, 0, 0,
            false, 0,
            0, 0, 0, 2, 0, 0
    ),

    YARN_BALL_RARE(
            "yarn-ball-rare",
            CatEquipType.YARN_BALL,
            MeowDanQuality.RARE,
            1, 0, 0, 0, 0, 0,
            false, 0,
            0, 0, 0, 3, 0, 0
    ),

    YARN_BALL_EPIC(
            "yarn-ball-epic",
            CatEquipType.YARN_BALL,
            MeowDanQuality.EPIC,
            2, 0, 2, 0, 0, 0,
            false, 0,
            0, 0, 0, 4, 0, 0
    ),

    YARN_BALL_LEGENDARY(
            "yarn-ball-legendary",
            CatEquipType.YARN_BALL,
            MeowDanQuality.LEGENDARY,
            3, 0, 4, 0, 0, 0,
            false, 0,
            0, 0, 0, 6, 0, 0
    );

    /*
     * 持久化/PDC 代码，例如 "collar-epic"。
     */
    private final String code;

    private final CatEquipType type;

    private final MeowDanQuality quality;

    /*
     * 近战伤害加成。
     */
    private final int damageBonus;

    /*
     * 光环半径加成（格）。
     */
    private final int auraBonus;

    /*
     * 喵力触发概率加成（百分点）。
     */
    private final int meowBonus;

    /*
     * 猫生命上限加成。
     */
    private final int catHealthBonus;

    /*
     * 受伤减免（百分点，与技能减免相乘）。
     */
    private final int damageReductionPercent;

    /*
     * 吸血（百分点）：猫造成伤害的该比例治疗猫自身。
     */
    private final int lifestealPercent;

    /*
     * 光环内主人加速 I。
     */
    private final boolean auraSpeed;

    /*
     * 每次喂食额外好感。
     */
    private final int feedAffectionBonus;

    /*
     * 饥饿衰减减缓（百分点）：放慢饥饿结算节奏。
     */
    private final int hungerSlowPercent;

    /*
     * 经验获取加成（百分点）。
     */
    private final int xpBonusPercent;

    /*
     * 技能冷却减免（百分点，与羁绊减免相乘后钳制）。
     */
    private final int cooldownReductionPercent;

    /*
     * 攻击间隔缩减（tick）。
     */
    private final int attackIntervalReductionTicks;

    /*
     * 每日好感衰减减免（点）。
     */
    private final int affectionDecayReduce;

    /*
     * 缓慢回血加成（百分点）。
     */
    private final int regenBoostPercent;

    CatEquipItem(
            String code,
            CatEquipType type,
            MeowDanQuality quality,
            int damageBonus,
            int auraBonus,
            int meowBonus,
            int catHealthBonus,
            int damageReductionPercent,
            int lifestealPercent,
            boolean auraSpeed,
            int feedAffectionBonus,
            int hungerSlowPercent,
            int xpBonusPercent,
            int cooldownReductionPercent,
            int attackIntervalReductionTicks,
            int affectionDecayReduce,
            int regenBoostPercent
    ) {

        this.code = code;
        this.type = type;
        this.quality = quality;
        this.damageBonus = damageBonus;
        this.auraBonus = auraBonus;
        this.meowBonus = meowBonus;
        this.catHealthBonus = catHealthBonus;
        this.damageReductionPercent = damageReductionPercent;
        this.lifestealPercent = lifestealPercent;
        this.auraSpeed = auraSpeed;
        this.feedAffectionBonus = feedAffectionBonus;
        this.hungerSlowPercent = hungerSlowPercent;
        this.xpBonusPercent = xpBonusPercent;
        this.cooldownReductionPercent = cooldownReductionPercent;
        this.attackIntervalReductionTicks = attackIntervalReductionTicks;
        this.affectionDecayReduce = affectionDecayReduce;
        this.regenBoostPercent = regenBoostPercent;
    }

    /*
     * 自定义模型数据。
     */
    public int getCustomModelData() {

        return type.getModelDataBase()
                + quality.ordinal()
                + 1;
    }

    /*
     * 语言键：equip-name.<code>。
     */
    public String getLangKey() {

        return "equip-name." + code;
    }

    /*
     * 按代码解析；未知/空返回 null。
     */
    public static CatEquipItem fromCode(
            String code
    ) {

        if (code == null ||
                code.isBlank()) {

            return null;
        }

        for (CatEquipItem item :
                values()) {

            if (item.code.equalsIgnoreCase(
                    code
            )) {

                return item;
            }
        }

        return null;
    }

    /*
     * 按（类型，品质）查找唯一条目（装备袋抽取用）；
     * 非法输入 / 无匹配返回 null。
     */
    public static CatEquipItem of(
            CatEquipType type,
            MeowDanQuality quality
    ) {

        if (type == null ||
                quality == null) {

            return null;
        }

        for (CatEquipItem item :
                values()) {

            if (item.type == type &&
                    item.quality == quality) {

                return item;
            }
        }

        return null;
    }
}
