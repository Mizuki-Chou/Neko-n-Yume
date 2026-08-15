package mizukichou.nekonyume.cat;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能定义（元数据）。
 *
 * <p>
 * 数值全部在 config.yml 的 skills.values.&lt;技能ID&gt; 节，
 * 代码只承载名称 / 品质 / 类型 / 图标 / 描述。
 * </p>
 */
public enum CatSkill {

    /*
     * ============================================================
     * 普通品质
     * ============================================================
     */
    SHARP_CLAW(
            "锐爪",
            CatTier.COMMON,
            SkillType.PASSIVE,
            Material.IRON_SWORD,
            "猫的近战伤害 +2"
    ),

    LIGHT_STEP(
            "灵步",
            CatTier.COMMON,
            SkillType.PASSIVE,
            Material.FEATHER,
            "猫的攻击间隔 -20%"
    ),

    LIGHT_FUR(
            "轻毛",
            CatTier.COMMON,
            SkillType.PASSIVE,
            Material.WHITE_WOOL,
            "猫受到的伤害 -10%"
    ),

    ALERT(
            "警觉",
            CatTier.COMMON,
            SkillType.PASSIVE,
            Material.OAK_SIGN,
            "光环范围扩大到 12 格"
    ),

    WARMTH(
            "暖意",
            CatTier.COMMON,
            SkillType.PASSIVE,
            Material.CAMPFIRE,
            "光环额外给予速度 I"
    ),

    SMALL_APPETITE(
            "小胃口",
            CatTier.COMMON,
            SkillType.PASSIVE,
            Material.BREAD,
            "饥饿下降 -10%"
    ),

    /*
     * ============================================================
     * 稀有品质
     * ============================================================
     */
    IRON_WALL(
            "铁壁",
            CatTier.RARE,
            SkillType.PASSIVE,
            Material.IRON_CHESTPLATE,
            "猫受到的伤害 -25%"
    ),

    HUNTER_SENSE(
            "狩猎直觉",
            CatTier.RARE,
            SkillType.PASSIVE,
            Material.SPYGLASS,
            "光环范围扩大到 15 格"
    ),

    GUARDIAN(
            "守护者",
            CatTier.RARE,
            SkillType.PASSIVE,
            Material.SHIELD,
            "主人受伤时猫分担 20%"
    ),

    FORAGER(
            "觅食",
            CatTier.RARE,
            SkillType.PASSIVE,
            Material.SWEET_BERRIES,
            "礼物判定概率 +10%"
    ),

    HEALING_PURR(
            "治愈呼噜",
            CatTier.RARE,
            SkillType.ACTIVE,
            Material.GOLDEN_APPLE,
            "立即为主人治疗生命值"
    ),

    SWIFT_PAWS(
            "灵猫迅捷",
            CatTier.RARE,
            SkillType.ACTIVE,
            Material.RABBIT_FOOT,
            "主人获得速度 II 20 秒"
    ),

    HUNTING_INSTINCT(
            "狩猎觉醒",
            CatTier.RARE,
            SkillType.ACTIVE,
            Material.STONE_SWORD,
            "主人获得力量 II 30 秒"
    ),

    /*
     * ============================================================
     * 独特品质
     * ============================================================
     */
    SPIRIT_SHOT(
            "灵弹",
            CatTier.UNIQUE,
            SkillType.PASSIVE,
            Material.ENDER_PEARL,
            "猫改用远程魔法弹攻击"
    ),

    SHADOW_STRIKE(
            "影袭",
            CatTier.UNIQUE,
            SkillType.PASSIVE,
            Material.BLACK_DYE,
            "猫每 5 次攻击必定 3 倍暴击"
    ),

    DRAIN(
            "汲取",
            CatTier.UNIQUE,
            SkillType.PASSIVE,
            Material.REDSTONE,
            "猫造成的伤害 20% 治疗主人"
    ),

    STAR_DUST(
            "星屑",
            CatTier.UNIQUE,
            SkillType.PASSIVE,
            Material.GLOWSTONE_DUST,
            "猫攻击 20% 概率溅射 50% 范围伤害"
    ),

    NINE_LIVES(
            "九命",
            CatTier.UNIQUE,
            SkillType.PASSIVE,
            Material.EMERALD,
            "致死保护后虚弱更短、恢复更快"
    ),

    MEOW_GUARD(
            "喵之守护",
            CatTier.UNIQUE,
            SkillType.ACTIVE,
            Material.AMETHYST_SHARD,
            "主人获得抗性 II 10 秒"
    ),

    FLASH_OF_SPIRIT(
            "灵光一现",
            CatTier.UNIQUE,
            SkillType.PASSIVE,
            Material.EXPERIENCE_BOTTLE,
            "抚摸与喂食的喵力概率 +5%"
    ),

    /*
     * ============================================================
     * 梦幻品质（梦槽专属）
     * ============================================================
     */
    DREAM_AWAKEN(
            "梦醒",
            CatTier.DREAM,
            SkillType.ACTIVE,
            Material.ENDER_EYE,
            "梦醒时分：大范围伤害并减速敌人"
    ),

    STARFALL(
            "星坠",
            CatTier.DREAM,
            SkillType.ACTIVE,
            Material.NETHER_STAR,
            "群星坠落：对超大范围造成巨大伤害"
    ),

    MOONLIGHT(
            "月华",
            CatTier.DREAM,
            SkillType.PASSIVE,
            Material.CLOCK,
            "日月交替：夜晚强化攻击，白昼持续再生"
    ),

    ETERNITY(
            "永恒",
            CatTier.DREAM,
            SkillType.PASSIVE,
            Material.TOTEM_OF_UNDYING,
            "致死保护升级为满血重生"
    ),

    TIME_ECHO(
            "时间回响",
            CatTier.DREAM,
            SkillType.ACTIVE,
            Material.DIAMOND,
            "时间倒流：主人与猫同时获得强大增益"
    ),

    RESONANCE(
            "共鸣",
            CatTier.DREAM,
            SkillType.PASSIVE,
            Material.HEART_OF_THE_SEA,
            "心意相通：光环内双方攻击提升"
    ),

    DREAM_WEAVER(
            "梦境编织",
            CatTier.DREAM,
            SkillType.PASSIVE,
            Material.STRING,
            "梦会保护你：濒死时自动救援"
    );

    private final String displayName;
    private final CatTier tier;
    private final SkillType type;
    private final Material icon;
    private final String description;

    CatSkill(
            String displayName,
            CatTier tier,
            SkillType type,
            Material icon,
            String description
    ) {

        this.displayName = displayName;
        this.tier = tier;
        this.type = type;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public CatTier getTier() {
        return tier;
    }

    public SkillType getType() {
        return type;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return type == SkillType.ACTIVE;
    }

    /*
     * 从存档 / 配置字符串恢复。
     * 未知值返回 null。
     */

    public static CatSkill fromName(
            String name
    ) {

        if (name == null ||
                name.isBlank()) {

            return null;
        }

        for (CatSkill skill :
                values()) {

            if (skill.name()
                    .equalsIgnoreCase(name)) {

                return skill;
            }
        }

        return null;
    }

    /*
     * ============================================================
     * 抽取池
     * ============================================================
     *
     * 返回品质不超过 maxTier 的全部技能。
     */

    public static List<CatSkill> poolFor(
            CatTier maxTier
    ) {

        List<CatSkill> pool =
                new ArrayList<>();

        if (maxTier == null) {
            return pool;
        }

        for (CatSkill skill :
                values()) {

            if (skill.tier.ordinal()
                    <= maxTier.ordinal()) {

                pool.add(skill);
            }
        }

        return pool;
    }

    /*
     * ============================================================
     * 精确品质抽取池
     * ============================================================
     *
     * 返回品质恰好等于 tier 的全部技能。
     *
     * 梦槽抽取专用：
     * 梦幻猫的梦槽只允许出现梦幻级技能，
     * 因此必须使用精确池而非"不超过上限"的全池。
     */
    public static List<CatSkill> poolOfTierExact(
            CatTier tier
    ) {

        List<CatSkill> pool =
                new ArrayList<>();

        if (tier == null) {
            return pool;
        }

        for (CatSkill skill :
                values()) {

            if (skill.tier == tier) {
                pool.add(skill);
            }
        }

        return pool;
    }

}
