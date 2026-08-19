package mizukichou.nekonyume.achievement;

import org.bukkit.Material;

import java.util.Locale;

/**
 * 成就定义（元数据）。
 *
 * <p>
 * 仿 CatSkill 的元数据驱动模式：
 * 代码只承载名称 / 描述 / 图标 / 度量方式 / 阈值 / 默认奖励，
 * 奖励数值可通过 config 的
 * achievements.rewards.&lt;成就ID&gt; 节覆盖。
 * </p>
 *
 * <p>
 * 度量方式（Metric）：
 * CLAIM           = 拥有猫咪（领取即达成）；
 * COMPANION_DAYS  = 陪伴天数（含当天）；
 * LEVEL           = 猫咪等级；
 * MEOW_RANK       = 喵阶；
 * COUNTER         = 持久化计数器
 *                   （进度键见本类的 KEY_* 常量）。
 * </p>
 *
 * <p>
 * 奖励设计（0.7.0）：
 * 大部分成就只奖励猫咪经验；
 * 七个里程碑成就奖励喵力：
 * 护主骑士 5 / 撸猫大师 10 / 干饭王 10 /
 * 刷新狂魔 30 / 喵力全开 30 / 登峰造极 30 /
 * 百日同行 50。
 * </p>
 */
public enum CatAchievement {

    /* ============================================================
     * 初遇
     * ============================================================ */

    FIRST_CLAIM(
            "相遇即是缘",
            "领取你的第一只猫咪",
            Material.CAT_SPAWN_EGG,
            Metric.CLAIM,
            null,
            1,
            50,
            0
    ),

    /* ============================================================
     * 陪伴
     * ============================================================ */

    COMPANION_DAYS_3(
            "三日之约",
            "与猫咪互相陪伴 3 天",
            Material.CLOCK,
            Metric.COMPANION_DAYS,
            null,
            3,
            50,
            0
    ),

    COMPANION_DAYS_100(
            "百日同行",
            "与猫咪互相陪伴 100 天",
            Material.DIAMOND,
            Metric.COMPANION_DAYS,
            null,
            100,
            0,
            50
    ),

    /* ============================================================
     * 互动
     * ============================================================ */

    PET_TOTAL_10(
            "摸摸头",
            "累计抚摸猫咪 10 次",
            Material.RED_DYE,
            Metric.COUNTER,
            "pet-total",
            10,
            30,
            0
    ),

    PET_TOTAL_200(
            "撸猫大师",
            "累计抚摸猫咪 200 次",
            Material.RED_BED,
            Metric.COUNTER,
            "pet-total",
            200,
            0,
            10
    ),

    /* ============================================================
     * 喂食
     * ============================================================ */

    FEED_TOTAL_10(
            "投喂员",
            "累计喂食猫咪 10 次",
            Material.COD,
            Metric.COUNTER,
            "feed-total",
            10,
            30,
            0
    ),

    FEED_TOTAL_100(
            "干饭王",
            "累计喂食猫咪 100 次",
            Material.COOKED_BEEF,
            Metric.COUNTER,
            "feed-total",
            100,
            0,
            10
    ),

    /* ============================================================
     * 成长
     * ============================================================ */

    LEVEL_10(
            "初露锋芒",
            "猫咪达到 10 级",
            Material.EXPERIENCE_BOTTLE,
            Metric.LEVEL,
            null,
            10,
            100,
            0
    ),

    LEVEL_60(
            "登峰造极",
            "猫咪达到 60 级",
            Material.ENCHANTED_BOOK,
            Metric.LEVEL,
            null,
            60,
            0,
            30
    ),

    /* ============================================================
     * 喵力
     * ============================================================ */

    MEOW_RANK_5(
            "喵光初现",
            "猫咪达到喵阶 5",
            Material.AMETHYST_SHARD,
            Metric.MEOW_RANK,
            null,
            5,
            100,
            0
    ),

    MEOW_RANK_30(
            "喵力全开",
            "猫咪达到喵阶 30",
            Material.END_CRYSTAL,
            Metric.MEOW_RANK,
            null,
            30,
            0,
            30
    ),

    /* ============================================================
     * 技能
     * ============================================================ */

    SKILL_ACTIVATE_20(
            "技惊四座",
            "累计施放主动技能 20 次",
            Material.ENDER_EYE,
            Metric.COUNTER,
            "skill-activate-total",
            20,
            100,
            0
    ),

    SKILL_REFRESH_10(
            "刷新狂魔",
            "累计刷新技能槽 10 次",
            Material.BOOK,
            Metric.COUNTER,
            "skill-refresh-total",
            10,
            0,
            30
    ),

    /* ============================================================
     * 底蕴
     * ============================================================ */

    TIER_UPGRADE_1(
            "蜕变之日",
            "猫咪底蕴升阶 1 次",
            Material.NETHER_STAR,
            Metric.COUNTER,
            "tier-upgrade-total",
            1,
            150,
            0
    ),

    /* ============================================================
     * 礼物
     * ============================================================ */

    GIFT_1(
            "小惊喜",
            "收到猫咪的礼物 1 次",
            Material.CHEST,
            Metric.COUNTER,
            "gift-total",
            1,
            80,
            0
    ),

    /* ============================================================
     * 战斗
     * ============================================================ */

    MONSTER_KILL_50(
            "护主骑士",
            "猫咪击杀 50 只怪物",
            Material.SHIELD,
            Metric.COUNTER,
            "monster-kill-total",
            50,
            0,
            5
    );

    /*
     * ============================================================
     * 进度键常量
     * ============================================================
     *
     * 注意：枚举常量参数中不能引用本类后置声明的静态字段
     * （非法前向引用），因此枚举参数内使用字面量，
     * 本常量供服务层与测试使用；
     * CatAchievementTest 会校验两者一致。
     */

    public static final String KEY_FEED = "feed-total";

    public static final String KEY_PET = "pet-total";

    public static final String KEY_SKILL_ACTIVATE =
            "skill-activate-total";

    public static final String KEY_SKILL_REFRESH =
            "skill-refresh-total";

    public static final String KEY_TIER_UPGRADE =
            "tier-upgrade-total";

    public static final String KEY_GIFT = "gift-total";

    public static final String KEY_MONSTER_KILL =
            "monster-kill-total";

    /*
     * ============================================================
     * 度量方式
     * ============================================================
     */

    public enum Metric {

        CLAIM,

        COMPANION_DAYS,

        LEVEL,

        MEOW_RANK,

        COUNTER
    }

    private final String displayName;
    private final String description;
    private final Material icon;
    private final Metric metric;
    private final String counterKey;
    private final int threshold;
    private final int defaultRewardXp;
    private final int defaultRewardMeowPower;

    CatAchievement(
            String displayName,
            String description,
            Material icon,
            Metric metric,
            String counterKey,
            int threshold,
            int defaultRewardXp,
            int defaultRewardMeowPower
    ) {

        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.metric = metric;
        this.counterKey = counterKey;
        this.threshold = threshold;
        this.defaultRewardXp = defaultRewardXp;
        this.defaultRewardMeowPower = defaultRewardMeowPower;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    public Metric getMetric() {
        return metric;
    }

    /**
     * 计数器进度键；
     * 非计数器成就是 null。
     */
    public String getCounterKey() {
        return counterKey;
    }

    public int getThreshold() {
        return threshold;
    }

    public int getDefaultRewardXp() {
        return defaultRewardXp;
    }

    public int getDefaultRewardMeowPower() {
        return defaultRewardMeowPower;
    }

    public boolean isCounterBased() {

        return metric == Metric.COUNTER;
    }

    /**
     * config 奖励节使用的 kebab-case 键名，
     * 例如 COMPANION_DAYS_100 → companion-days-100。
     */
    public String getConfigId() {

        return name()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');
    }
}
