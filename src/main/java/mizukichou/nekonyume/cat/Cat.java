package mizukichou.nekonyume.cat;

import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Neko n' Yume 的猫咪数据模型。
 *
 * <p>
 * 这个 Cat 不是 Bukkit 的 org.bukkit.entity.Cat。
 * 它代表的是 Neko n' Yume 中一只猫咪的完整运行时数据。
 * </p>
 *
 * <p>
 * Cat 是运行期间的猫咪状态真相。
 * CatStore 负责持久化这些状态。
 * </p>
 *
 * <p>
 * Step 6 治理：
 * 曲线数学 → GrowthMath；
 * 技能集合 → CatSkills；
 * 梦槽判定 → CatTier。
 * Cat 只保留状态持有与合法的领域查询。
 * </p>
 */
@Getter
@Setter
public class Cat {

    /*
     * ============================================================
     * 基础身份
     * ============================================================
     */

    /**
     * Neko n' Yume 猫咪自己的永久 UUID。
     *
     * <p>
     * 与 Bukkit Entity UUID 不同。
     * </p>
     */
    private final UUID id;

    /**
     * 猫咪主人的 UUID。
     */
    private final UUID ownerUuid;

    /**
     * 猫咪名称。
     */
    private String name;

    /*
     * ============================================================
     * 性格
     * ============================================================
     *
     * 由逻辑猫 UUID 确定性生成，
     * 永久固定，不存储。
     */

    private final CatPersonality personality;

    /*
     * ============================================================
     * 底蕴
     * ============================================================
     *
     * 普通 / 稀有 / 独特 / 梦幻。
     *
     * 由逻辑猫 UUID 确定性生成，
     * 创建时持久化到 players.yml（data-version v4）。
     * 决定技能槽成长轨迹与技能品质上限。
     */

    private CatTier tier;

    /*
     * ============================================================
     * 成长
     * ============================================================
     */

    /**
     * 猫咪等级。
     */
    private int level;

    /**
     * 累计经验。
     *
     * <p>
     * 等级由累计经验推导（GrowthMath）。
     * </p>
     */
    private int experience;

    /*
     * ============================================================
     * 喵力 / 喵阶
     * ============================================================
     *
     * 喵力是稀有机缘资源。
     * 抚摸与喂食有低概率获得。
     *
     * 升到第 N 阶的累计喵力：
     * N × (N + 19) / 2
     *
     * 曲线参数 19 可通过配置
     * meow.rank-curve-offset 调整。
     */

    private int meowPower;

    private int meowRank;

    /*
     * ============================================================
     * 行为模式
     * ============================================================
     *
     * FOLLOW = 跟随
     * SIT    = 坐下
     * FREE   = 自由
     *
     * 持久化到 players.yml 的 behavior-mode。
     */
    private CatBehaviorMode behaviorMode =
            CatBehaviorMode.FOLLOW;

    /*
     * ============================================================
     * 技能槽
     * ============================================================
     *
     * 按槽位顺序存储（CatSkills 保证唯一）。
     * 梦幻猫的第 0 槽是"梦槽"，
     * 只有梦槽能出现梦幻级技能。
     */

    private final CatSkills skills =
            new CatSkills();

    /*
     * ============================================================
     * 状态
     * ============================================================
     */

    /**
     * 饥饿度。
     *
     * 0   = 极度饥饿
     * 100 = 完全饱腹
     */
    private int hunger;

    /**
     * 好感度。
     *
     * 0   = 完全陌生
     * 100 = 最大信赖
     */
    private int affection;

    /**
     * 健康度。
     *
     * 0   = 危险
     * 100 = 完全健康
     */
    private int health;

    /*
     * ============================================================
     * 外观
     * ============================================================
     */

    /**
     * Minecraft 猫咪花色的 NamespacedKey 字符串。
     *
     * <p>
     * 使用字符串而不是 Bukkit Cat.Type，
     * 避免数据模型直接依赖 Bukkit Registry。
     * </p>
     */
    private String variant;

    /*
     * ============================================================
     * 位置
     * ============================================================
     */

    private String worldName;

    private double x;
    private double y;
    private double z;

    private float yaw;
    private float pitch;

    /*
     * ============================================================
     * Bukkit 实体
     * ============================================================
     */

    /**
     * 当前对应的 Minecraft 猫实体 UUID。
     *
     * <p>
     * 这不是永久身份。
     * </p>
     */
    private UUID entityUuid;

    /*
     * ============================================================
     * 时间
     * ============================================================
     */

    /**
     * 猫咪创建时间。
     */
    private final long createdAt;

    /**
     * 上一次喂食时间。
     */
    private long lastFedAt;

    /**
     * 上一次互动时间。
     */
    private long lastInteractionAt;

    /*
     * ============================================================
     * 创建新猫
     * ============================================================
     */

    /**
     * 创建全新的猫咪。
     *
     * 默认：
     *
     * level     = 1
     * affection = 50
     * hunger    = 100
     * health    = 100
     * variant   = null
     *
     * experience = 0
     * meowPower  = 0
     * meowRank   = 0
     */
    public Cat(
            UUID id,
            UUID ownerUuid,
            String name
    ) {

        this(
                id,
                ownerUuid,
                name,
                1,
                50,
                100,
                100,
                null,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );
    }

    /*
     * ============================================================
     * 完整构造函数
     * ============================================================
     */

    public Cat(
            UUID id,
            UUID ownerUuid,
            String name,
            int level,
            int affection,
            int hunger,
            int health,
            String variant,
            long createdAt,
            long lastFedAt,
            long lastInteractionAt
    ) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Cat id cannot be null."
            );
        }

        if (ownerUuid == null) {
            throw new IllegalArgumentException(
                    "Cat owner UUID cannot be null."
            );
        }

        if (name == null ||
                name.isBlank()) {

            name = "Mikan";
        }

        this.id = id;
        this.ownerUuid = ownerUuid;
        this.name = name;

        this.personality =
                CatPersonality.fromCatId(
                        id
                );

        this.tier =
                CatTier.fromCatId(
                        id
                );

        this.level =
                Math.max(
                        1,
                        level
                );

        this.experience = 0;

        this.meowPower = 0;
        this.meowRank = 0;

        this.affection =
                clamp(
                        affection,
                        0,
                        100
                );

        this.hunger =
                clamp(
                        hunger,
                        0,
                        100
                );

        this.health =
                clamp(
                        health,
                        0,
                        100
                );

        this.variant =
                normalizeVariant(
                        variant
                );

        long now =
                System.currentTimeMillis();

        this.createdAt =
                createdAt >= 0
                        ? createdAt
                        : now;

        this.lastFedAt =
                lastFedAt >= 0
                        ? lastFedAt
                        : this.createdAt;

        this.lastInteractionAt =
                lastInteractionAt >= 0
                        ? lastInteractionAt
                        : this.createdAt;
    }

    /*
     * ============================================================
     * 工厂
     * ============================================================
     */

    public static Cat createNew(
            UUID id,
            UUID ownerUuid,
            String name
    ) {

        return new Cat(
                id,
                ownerUuid,
                name
        );
    }

    /**
     * 从完整存档恢复。
     *
     * <p>
     * experience / meowPower / meowRank / tier / skills
     * 由调用方通过 setter 恢复。
     * </p>
     */
    public static Cat restore(
            UUID id,
            UUID ownerUuid,
            String name,
            int level,
            int affection,
            int hunger,
            int health,
            String variant,
            long createdAt,
            long lastFedAt,
            long lastInteractionAt
    ) {

        return new Cat(
                id,
                ownerUuid,
                name,
                level,
                affection,
                hunger,
                health,
                variant,
                createdAt,
                lastFedAt,
                lastInteractionAt
        );
    }

    /*
     * ============================================================
     * 基础身份
     * ============================================================
     */

    public void setName(
            String name
    ) {

        if (name == null ||
                name.isBlank()) {

            return;
        }

        this.name = name;
    }

    /*
     * ============================================================
     * 底蕴
     * ============================================================
     */

    public void setTier(
            CatTier tier
    ) {

        if (tier != null) {
            this.tier = tier;
        }
    }

    /*
     * ============================================================
     * 等级 / 经验
     * ============================================================
     */

    public void setLevel(
            int level
    ) {

        this.level =
                Math.max(
                        1,
                        level
                );
    }

    public void addLevel(
            int amount
    ) {

        setLevel(
                this.level + amount
        );
    }

    public void setExperience(
            int experience
    ) {

        this.experience =
                Math.max(
                        0,
                        experience
                );
    }

    /**
     * 增加经验（默认曲线 100）。
     *
     * <p>
     * 返回升了几级（0 表示未升级）。
     * </p>
     */
    public int addExperience(
            int amount
    ) {

        return addExperience(
                amount,
                GrowthMath.DEFAULT_XP_CURVE_BASE
        );
    }

    /**
     * 增加经验（自定义曲线）。
     *
     * <p>
     * 返回升了几级（0 表示未升级）。
     * </p>
     */
    public int addExperience(
            int amount,
            int curveBase
    ) {

        if (amount <= 0) {
            return 0;
        }

        /*
         * 防溢出：经验累计封顶 Integer.MAX_VALUE。
         */
        long totalExperience =
                (long) this.experience + amount;

        this.experience =
                totalExperience > Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : (int) totalExperience;


        int newLevel =
                GrowthMath.levelFromExperience(
                        this.experience,
                        curveBase
                );

        int gained =
                newLevel - this.level;

        if (gained > 0) {

            this.level =
                    newLevel;
        }

        return gained;
    }

    /*
     * ============================================================
     * 喵力 / 喵阶
     * ============================================================
     */

    public void setMeowPower(
            int meowPower
    ) {

        this.meowPower =
                Math.max(
                        0,
                        meowPower
                );
    }

    public void setMeowRank(
            int meowRank
    ) {

        this.meowRank =
                Math.max(
                        0,
                        meowRank
                );
    }

    /**
     * 增加喵力（默认曲线参数 19）。
     *
     * <p>
     * 返回升了几阶（0 表示未升阶）。
     * </p>
     */
    public int addMeowPower(
            int amount
    ) {

        return addMeowPower(
                amount,
                GrowthMath.DEFAULT_MEOW_CURVE_OFFSET
        );
    }

    /**
     * 增加喵力（自定义曲线参数）。
     *
     * <p>
     * 返回升了几阶（0 表示未升阶）。
     * </p>
     */
    public int addMeowPower(
            int amount,
            int curveOffset
    ) {

        if (amount <= 0) {
            return 0;
        }

        /*
         * 防溢出：喵力累计封顶 Integer.MAX_VALUE。
         */
        long totalMeowPower =
                (long) this.meowPower + amount;

        this.meowPower =
                totalMeowPower > Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : (int) totalMeowPower;

        int newRank =
                GrowthMath.meowRankFromPower(
                        this.meowPower,
                        curveOffset
                );

        int gained =
                newRank - this.meowRank;

        if (gained > 0) {

            this.meowRank =
                    newRank;
        }

        return gained;
    }

    /*
     * ============================================================
     * 行为模式
     * ============================================================
     */

    public void setBehaviorMode(
            CatBehaviorMode behaviorMode
    ) {

        this.behaviorMode =
                behaviorMode == null
                        ? CatBehaviorMode.FOLLOW
                        : behaviorMode;
    }

    /*
     * ============================================================
     * 技能槽（委托 CatSkills）
     * ============================================================
     */

    public List<CatSkill> getSkills() {

        return skills.toList();
    }

    public boolean hasSkill(
            CatSkill skill
    ) {

        return skills.contains(
                skill
        );
    }

    public void addSkill(
            CatSkill skill
    ) {

        skills.add(skill);
    }

    public void clearSkills() {

        skills.clear();
    }

    public void setSkills(
            Collection<CatSkill> newSkills
    ) {

        skills.replaceAll(
                newSkills
        );
    }

    /*
     * 替换指定槽位的技能（用于刷新）。
     */
    public void setSkillAt(
            int index,
            CatSkill skill
    ) {

        skills.set(
                index,
                skill
        );
    }

    /*
     * 当前应有技能槽数
     * （按底蕴与成长拐点推导）。
     */
    public int getSkillSlotCount() {

        int checkpoints =
                CatTier.checkpointsReached(
                        meowRank,
                        level
                );

        return tier.slotCount(
                checkpoints
        );
    }

    /*
     * 该槽位是否是"梦槽"
     * （梦幻猫的第 0 槽，专属梦幻级技能）。
     * 委托 CatTier。
     */
    public boolean isDreamSlot(
            int slotIndex
    ) {

        return tier.isDreamSlot(
                slotIndex
        );
    }

    /*
     * ============================================================
     * 心情
     * ============================================================
     */

    public CatMood getMood() {

        return getMood(
                System.currentTimeMillis()
        );
    }

    public CatMood getMood(
            long now
    ) {

        int score =
                CatMood.calculateScore(
                        this,
                        now
                );

        return CatMood.fromScore(
                score
        );
    }

    /*
     * ============================================================
     * 饥饿
     * ============================================================
     */

    public void setHunger(
            int hunger
    ) {

        this.hunger =
                clamp(
                        hunger,
                        0,
                        100
                );
    }

    public void addHunger(
            int amount
    ) {

        setHunger(
                this.hunger + amount
        );
    }

    public void removeHunger(
            int amount
    ) {

        setHunger(
                this.hunger - amount
        );
    }

    /*
     * ============================================================
     * 好感度
     * ============================================================
     */

    public void setAffection(
            int affection
    ) {

        this.affection =
                clamp(
                        affection,
                        0,
                        100
                );
    }

    public void addAffection(
            int amount
    ) {

        setAffection(
                this.affection + amount
        );
    }

    public void removeAffection(
            int amount
    ) {

        setAffection(
                this.affection - amount
        );
    }

    /*
     * ============================================================
     * 健康度
     * ============================================================
     */

    public void setHealth(
            int health
    ) {

        this.health =
                clamp(
                        health,
                        0,
                        100
                );
    }

    public void addHealth(
            int amount
    ) {

        setHealth(
                this.health + amount
        );
    }

    public void removeHealth(
            int amount
    ) {

        setHealth(
                this.health - amount
        );
    }

    /*
     * ============================================================
     * 花色
     * ============================================================
     */

    public void setVariant(
            String variant
    ) {

        this.variant =
                normalizeVariant(
                        variant
                );
    }

    private String normalizeVariant(
            String variant
    ) {

        if (variant == null ||
                variant.isBlank()) {

            return null;
        }

        return variant.trim();
    }

    /*
     * ============================================================
     * 时间
     * ============================================================
     */

    public void setLastFedAt(
            long timestamp
    ) {

        if (timestamp < 0) {
            return;
        }

        this.lastFedAt =
                timestamp;
    }

    public void markFed() {

        this.lastFedAt =
                System.currentTimeMillis();
    }

    public void setLastInteractionAt(
            long timestamp
    ) {

        if (timestamp < 0) {
            return;
        }

        this.lastInteractionAt =
                timestamp;
    }

    public void markInteracted() {

        this.lastInteractionAt =
                System.currentTimeMillis();
    }

    /*
     * ============================================================
     * 陪伴天数
     * ============================================================
     *
     * 从创建日算起，含当天：
     * 创建于今天 → 第 1 天。
     */

    public int getCompanionDays(
            long now
    ) {

        if (now <= 0) {

            now =
                    System.currentTimeMillis();
        }

        if (now <= this.createdAt) {
            return 1;
        }

        long days =
                (now - this.createdAt)
                        / (24L * 60 * 60 * 1000);

        return (int) days + 1;
    }

    /*
     * ============================================================
     * 状态判断
     * ============================================================
     */

    public boolean isStarving() {

        return hunger <= 0;
    }

    public boolean isHungry() {

        return hunger <= 20;
    }

    public boolean isUnhealthy() {

        return health <= 0;
    }

    public boolean isMaxAffection() {

        return affection >= 100;
    }

    /*
     * ============================================================
     * 工具
     * ============================================================
     */

    private int clamp(
            int value,
            int min,
            int max
    ) {

        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    /*
     * ============================================================
     * Debug
     * ============================================================
     */

    @Override
    public String toString() {

        return "Cat{" +
                "id=" + id +
                ", ownerUuid=" + ownerUuid +
                ", name='" + name + '\'' +
                ", personality=" + personality +
                ", tier=" + tier +
                ", level=" + level +
                ", experience=" + experience +
                ", meowPower=" + meowPower +
                ", meowRank=" + meowRank +
                ", behaviorMode=" + behaviorMode +
                ", skills=" + skills +
                ", hunger=" + hunger +
                ", affection=" + affection +
                ", health=" + health +
                ", variant='" + variant + '\'' +
                ", entityUuid=" + entityUuid +
                '}';
    }
}