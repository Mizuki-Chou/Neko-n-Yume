package mizukichou.nekonyume.config;

import lombok.Getter;
import mizukichou.nekonyume.achievement.CatAchievement;
import mizukichou.nekonyume.cat.CatMood;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.cat.MeowDanQuality;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 配置快照（数据定义）。
 *
 * <p>
 * 全部玩法数值的不可变持有者：
 * ConfigLoader 负责从 config.yml 解析构建，
 * ConfigManager 负责生命周期（reload 原子换发）。
 * 本类不做任何加载 / 重载操作，只承载数据。
 * </p>
 *
 * <p>
 * 快照不可变（volatile 换发），
 * 一次调用内读取到的数值永远一致。
 * </p>
 */
@Getter
public final class ConfigSnapshot {

    /*
     * 语言代码（lang/<code>.yml）。
     */
    private final String language;

    private final Storage storage;

    private final Items items;

    private final Growth growth;

    private final Affection affection;

    private final Meow meow;

    private final Hunger hunger;

    private final Daily daily;

    private final Food food;

    private final Gift gift;

    private final Achievements achievements;

    private final Skills skills;

    private final Battle battle;

    private final Aura aura;

    private final JoinMessage joinMessage;

    private final MumaNight mumaNight;

    private final XpPill xpPill;

    private final Care care;

    private final Drops drops;

    public ConfigSnapshot(
            String language,
            Storage storage,
            Items items,
            Growth growth,
            Affection affection,
            Meow meow,
            Hunger hunger,
            Daily daily,
            Food food,
            Gift gift,
            Achievements achievements,
            Skills skills,
            Battle battle,
            Aura aura,
            JoinMessage joinMessage,
            MumaNight mumaNight,
            XpPill xpPill,
            Care care,
            Drops drops
    ) {

        this.language = language;
        this.storage = storage;
        this.items = items;
        this.growth = growth;
        this.affection = affection;
        this.meow = meow;
        this.hunger = hunger;
        this.daily = daily;
        this.food = food;
        this.gift = gift;
        this.achievements = achievements;
        this.skills = skills;
        this.battle = battle;
        this.aura = aura;
        this.joinMessage = joinMessage;
        this.mumaNight = mumaNight;
        this.xpPill = xpPill;
        this.care = care;
        this.drops = drops;
    }

    /*
     * ============================================================
     * 存储
     * ============================================================
     */

    @Getter
    public static final class Storage {

        private final boolean backupEnabled;

        private final int backupKeep;

        public Storage(
                boolean backupEnabled,
                int backupKeep
        ) {

            this.backupEnabled = backupEnabled;
            this.backupKeep = backupKeep;
        }
    }

    /*
     * ============================================================
     * 物品（喵丹）
     * ============================================================
     */

    @Getter
    public static final class Items {

        private final int meowdanGeneration;

        /*
         * key = 品质名小写，value = CustomModelData。
         */
        private final Map<String, Integer> meowdanCustomModelData;

        public Items(
                int meowdanGeneration,
                Map<String, Integer> meowdanCustomModelData
        ) {

            this.meowdanGeneration = meowdanGeneration;
            this.meowdanCustomModelData =
                    Collections.unmodifiableMap(
                            meowdanCustomModelData
                    );
        }

        public int meowdanCustomModelData(
                MeowDanQuality quality
        ) {

            if (quality == null) {
                return 0;
            }

            return meowdanCustomModelData.getOrDefault(
                    quality.name()
                            .toLowerCase(
                                    Locale.ROOT
                            ),
                    quality.getDefaultModelData()
            );
        }
    }

    /*
     * ============================================================
     * 成长
     * ============================================================
     */

    @Getter
    public static final class Growth {

        private final int petXpMin;

        private final int petXpMax;

        private final int levelCurveBase;

        public Growth(
                int petXpMin,
                int petXpMax,
                int levelCurveBase
        ) {

            this.petXpMin = petXpMin;
            this.petXpMax = petXpMax;
            this.levelCurveBase = levelCurveBase;
        }
    }

    /*
     * ============================================================
     * 好感
     * ============================================================
     */

    @Getter
    public static final class Affection {

        private final int petBase;

        public Affection(
                int petBase
        ) {

            this.petBase = petBase;
        }
    }

    /*
     * ============================================================
     * 喵力
     * ============================================================
     */

    @Getter
    public static final class Meow {

        private final int petChance;

        private final int feedChance;

        private final int feedChanceLimit;

        private final int rankCurveOffset;

        private final int feedGain;

        private final int petGain;

        public Meow(
                int petChance,
                int feedChance,
                int feedChanceLimit,
                int rankCurveOffset,
                int feedGain,
                int petGain
        ) {

            this.petChance = petChance;
            this.feedChance = feedChance;
            this.feedChanceLimit = feedChanceLimit;
            this.rankCurveOffset = rankCurveOffset;
            this.feedGain = feedGain;
            this.petGain = petGain;
        }
    }

    /*
     * ============================================================
     * 饥饿
     * ============================================================
     */

    @Getter
    public static final class Hunger {

        private final long intervalMillis;

        public Hunger(long intervalMillis) {

            this.intervalMillis = intervalMillis;
        }
    }

    /*
     * ============================================================
     * 每日限制
     * ============================================================
     */

    @Getter
    public static final class Daily {

        private final int petLimit;

        public Daily(int petLimit) {

            this.petLimit = petLimit;
        }
    }

    /*
     * ============================================================
     * 食物表
     * ============================================================
     */

    @Getter
    public static final class Food {

        private final Map<Material, Integer> values;

        public Food(Map<Material, Integer> values) {

            this.values =
                    Collections.unmodifiableMap(
                            values
                    );
        }
    }

    /*
     * ============================================================
     * 礼物
     * ============================================================
     */

    @Getter
    public static final class Gift {

        private final boolean enabled;

        private final CatMood moodMin;

        private final int baseChance;

        private final int chancePerRank;

        private final int maxChance;

        /*
         * key = 档位编号（从 1 开始）。
         */
        private final Map<Integer, List<GiftItemEntry>> tiers;

        private final int maxTier;

        public Gift(
                boolean enabled,
                CatMood moodMin,
                int baseChance,
                int chancePerRank,
                int maxChance,
                Map<Integer, List<GiftItemEntry>> tiers,
                int maxTier
        ) {

            this.enabled = enabled;
            this.moodMin = moodMin;
            this.baseChance = baseChance;
            this.chancePerRank = chancePerRank;
            this.maxChance = maxChance;

            /*
             * 0.8.1 修复（R3，社区上报：深不可变加固）：
             * 外层与内层列表全部包装为不可变，
             * 外部 API 使用者无法篡改运行时配置。
             */
            Map<Integer, List<GiftItemEntry>> wrapped =
                    new java.util.HashMap<>();

            if (tiers != null) {

                for (Map.Entry<Integer, List<GiftItemEntry>> entry :
                        tiers.entrySet()) {

                    wrapped.put(
                            entry.getKey(),
                            entry.getValue() == null
                                    ? List.of()
                                    : Collections.unmodifiableList(
                                    new ArrayList<>(
                                            entry.getValue()
                                    )
                            )
                    );
                }
            }

            this.tiers =
                    Collections.unmodifiableMap(
                            wrapped
                    );
            this.maxTier = maxTier;
        }

        /*
         * 按喵阶计算档位（纯函数，可单元测试）。
         *
         * tier-1 = 0~5
         * tier-2 = 6~10
         * ...
         */
        public static int computeTier(
                int meowRank
        ) {

            if (meowRank < 0) {
                meowRank = 0;
            }

            return Math.max(
                    1,
                    (meowRank + 4) / 5
            );
        }

        /*
         * 获取指定档位的礼物条目。
         * 档位缺失时返回空列表。
         */
        public List<GiftItemEntry> tierExact(
                int tier
        ) {

            List<GiftItemEntry> entries =
                    tiers.get(tier);

            if (entries == null) {
                return Collections.emptyList();
            }

            return Collections.unmodifiableList(
                    entries
            );
        }
    }

    /*
     * ============================================================
     * 成就
     * ============================================================
     */

    @Getter
    public static final class Achievements {

        private final boolean enabled;

        /*
         * key = 成就 config ID（如 companion-days-100）。
         * 只包含 config 中显式配置的条目，
         * 缺失时回退枚举默认值。
         */
        private final Map<String, Integer> rewardXp;

        private final Map<String, Integer> rewardMeowPower;

        public Achievements(
                boolean enabled,
                Map<String, Integer> rewardXp,
                Map<String, Integer> rewardMeowPower
        ) {

            this.enabled = enabled;
            this.rewardXp =
                    Collections.unmodifiableMap(
                            rewardXp
                    );
            this.rewardMeowPower =
                    Collections.unmodifiableMap(
                            rewardMeowPower
                    );
        }

        public int rewardXp(
                CatAchievement achievement,
                int defaultValue
        ) {

            if (achievement == null) {
                return defaultValue;
            }

            return rewardXp.getOrDefault(
                    achievement.getConfigId(),
                    defaultValue
            );
        }

        public int rewardMeowPower(
                CatAchievement achievement,
                int defaultValue
        ) {

            if (achievement == null) {
                return defaultValue;
            }

            return rewardMeowPower.getOrDefault(
                    achievement.getConfigId(),
                    defaultValue
            );
        }
    }

    /*
     * ============================================================
     * 技能
     * ============================================================
     */

    @Getter
    public static final class Skills {

        private final String refreshCostType;

        private final int refreshCost;

        private final int dreamSlotCostMultiplier;

        /*
         * key = 技能名小写，
         * value = 数值键 → 值。
         */
        private final Map<String, Map<String, Double>> values;

        public Skills(
                String refreshCostType,
                int refreshCost,
                int dreamSlotCostMultiplier,
                Map<String, Map<String, Double>> values
        ) {

            this.refreshCostType = refreshCostType;
            this.refreshCost = refreshCost;
            this.dreamSlotCostMultiplier = dreamSlotCostMultiplier;

            /*
             * 0.8.1 修复（R3，社区上报：深不可变加固）：
             * 外层 values 表也包装为不可变（内层 map 已由
             * parser 用 unmodifiableMap 包装），
             * 外部 API 使用者无法篡改运行时配置。
             */
            this.values = values == null
                    ? java.util.Collections.emptyMap()
                    : java.util.Collections.unmodifiableMap(
                            values
                    );
        }

        public double value(
                CatSkill skill,
                String key,
                double defaultValue
        ) {

            if (skill == null) {
                return defaultValue;
            }

            Map<String, Double> entry =
                    values.get(
                            skill.name()
                                    .toLowerCase(
                                            Locale.ROOT
                                    )
                    );

            if (entry == null) {
                return defaultValue;
            }

            double raw =
                    entry.getOrDefault(
                            key,
                            defaultValue
                    );

            /*
             * 0.8.1 修复（R3）：读取层纵深防御——
             * 即使解析层漏过非有限值（未来改动/外部构建），
             * 消费端也绝不把 NaN/Infinity 送进伤害与属性计算。
             */
            return Double.isFinite(raw)
                    ? raw
                    : defaultValue;
        }

        public int valueInt(
                CatSkill skill,
                String key,
                int defaultValue
        ) {

            return (int) value(
                    skill,
                    key,
                    defaultValue
            );
        }
    }

    /*
     * ============================================================
     * 战斗
     * ============================================================
     */

    @Getter
    public static final class Battle {

        private final boolean enabled;

        private final int baseDamage;

        private final int perRankDamage;

        private final int attackIntervalTicks;

        private final int aggroRadius;

        private final int weaknessSeconds;

        private final int recoverySeconds;

        private final int regenIntervalSeconds;

        private final int eternityRebirthSeconds;

        /*
         * 0.7.4：战斗掉落经验（猫击杀时）。
         */
        private final int xpPerKillMin;

        private final int xpPerKillMax;

        private final int dragonXp;

        private final int witherXpMin;

        private final int witherXpMax;

        public Battle(
                boolean enabled,
                int baseDamage,
                int perRankDamage,
                int attackIntervalTicks,
                int aggroRadius,
                int weaknessSeconds,
                int recoverySeconds,
                int regenIntervalSeconds,
                int eternityRebirthSeconds,
                int xpPerKillMin,
                int xpPerKillMax,
                int dragonXp,
                int witherXpMin,
                int witherXpMax
        ) {

            this.enabled = enabled;
            this.baseDamage = baseDamage;
            this.perRankDamage = perRankDamage;
            this.attackIntervalTicks = attackIntervalTicks;
            this.aggroRadius = aggroRadius;
            this.weaknessSeconds = weaknessSeconds;
            this.recoverySeconds = recoverySeconds;
            this.regenIntervalSeconds = regenIntervalSeconds;
            this.eternityRebirthSeconds = eternityRebirthSeconds;
            this.xpPerKillMin = xpPerKillMin;
            this.xpPerKillMax = xpPerKillMax;
            this.dragonXp = dragonXp;
            this.witherXpMin = witherXpMin;
            this.witherXpMax = witherXpMax;
        }
    }

    /*
     * ============================================================
     * 光环
     * ============================================================
     */

    @Getter
    public static final class Aura {

        private final boolean enabled;

        private final int baseRadius;

        private final int speedUnlockLevel;

        private final int strengthUnlockMeowRank;

        private final int regenUnlockLevel;

        private final int regenAffection;

        public Aura(
                boolean enabled,
                int baseRadius,
                int speedUnlockLevel,
                int strengthUnlockMeowRank,
                int regenUnlockLevel,
                int regenAffection
        ) {

            this.enabled = enabled;
            this.baseRadius = baseRadius;
            this.speedUnlockLevel = speedUnlockLevel;
            this.strengthUnlockMeowRank = strengthUnlockMeowRank;
            this.regenUnlockLevel = regenUnlockLevel;
            this.regenAffection = regenAffection;
        }
    }

    /*
     * ============================================================
     * 登录消息
     * ============================================================
     */

    @Getter
    public static final class JoinMessage {

        private final boolean enabled;

        private final List<String> messages;

        public JoinMessage(
                boolean enabled,
                List<String> messages
        ) {

            this.enabled = enabled;
            this.messages =
                    Collections.unmodifiableList(
                            messages
                    );
        }
    }

    /*
     * ============================================================
     * 梦魔之夜
     * ============================================================
     */

    @Getter
    public static final class MumaNight {

        private final double chance;

        private final double healthMultiplier;

        private final double damageMultiplier;

        public MumaNight(
                double chance,
                double healthMultiplier,
                double damageMultiplier
        ) {

            this.chance = chance;
            this.healthMultiplier = healthMultiplier;
            this.damageMultiplier = damageMultiplier;
        }
    }

    /*
     * ============================================================
     * 经验丸（0.7.4）
     * ============================================================
     */

    @Getter
    public static final class XpPill {

        private final int normalXp;

        private final int eliteXp;

        public XpPill(
                int normalXp,
                int eliteXp
        ) {

            this.normalXp = normalXp;
            this.eliteXp = eliteXp;
        }
    }

    /*
     * ============================================================
     * 羁绊纪元（0.8.0）
     * ============================================================
     *
     * 好感 / 饥饿 / 心情 / 羁绊的全部玩法数值。
     * 缺失节使用默认值；解析见 CareConfigParser。
     */

    @Getter
    public static final class Care {

        private final Map<CatMood, Double> moodDamagePercent;

        private final Map<CatMood, Double> moodXpPercent;

        private final int affectionDailyDecay;

        private final int feedHungryAffection;

        private final int feedNormalAffection;

        private final int hungrySkillThreshold;

        private final int starvingFightThreshold;

        private final int hungryFeedThreshold;

        private final List<Integer> bondTierThresholds;

        private final List<Integer> bondXpPercent;

        private final List<Integer> bondCooldownPercent;

        private final List<Integer> bondDamagePercent;

        private final int defeatHealthLoss;

        private final int feedHealthRestore;

        private final int hungerAffectionLossMinutes;

        public Care(
                Map<CatMood, Double> moodDamagePercent,
                Map<CatMood, Double> moodXpPercent,
                int affectionDailyDecay,
                int feedHungryAffection,
                int feedNormalAffection,
                int hungrySkillThreshold,
                int starvingFightThreshold,
                int hungryFeedThreshold,
                List<Integer> bondTierThresholds,
                List<Integer> bondXpPercent,
                List<Integer> bondCooldownPercent,
                List<Integer> bondDamagePercent,
                int defeatHealthLoss,
                int feedHealthRestore,
                int hungerAffectionLossMinutes
        ) {

            this.moodDamagePercent = moodDamagePercent;
            this.moodXpPercent = moodXpPercent;
            this.affectionDailyDecay = affectionDailyDecay;
            this.feedHungryAffection = feedHungryAffection;
            this.feedNormalAffection = feedNormalAffection;
            this.hungrySkillThreshold = hungrySkillThreshold;
            this.starvingFightThreshold = starvingFightThreshold;
            this.hungryFeedThreshold = hungryFeedThreshold;
            this.bondTierThresholds = bondTierThresholds;
            this.bondXpPercent = bondXpPercent;
            this.bondCooldownPercent = bondCooldownPercent;
            this.bondDamagePercent = bondDamagePercent;
            this.defeatHealthLoss = defeatHealthLoss;
            this.feedHealthRestore = feedHealthRestore;
            this.hungerAffectionLossMinutes =
                    hungerAffectionLossMinutes;
        }
    }

    /*
     * ============================================================
     * 掉落（0.8.0）
     * ============================================================
     *
     * 平时（梦魔夜以外）与梦魔夜的 NekoNYume 物品掉落：
     * 是否开启、喵丹概率与五品质权重（平凡→至极）、
     * 初阶/高阶经验丸概率、猫猫装备袋概率。
     * 权重为相对值，滚动时自动按总和归一。
     */
    @Getter
    public static final class Drops {

        private final DropSet general;

        private final DropSet mumaNight;

        public Drops(
                DropSet general,
                DropSet mumaNight
        ) {

            this.general = general;
            this.mumaNight = mumaNight;
        }

        /*
         * 单一状态下的掉落集合（不可变）。
         */
        @Getter
        public static final class DropSet {

            private final boolean enabled;

            private final double meowdanChance;

            private final int[] meowdanQualityWeights;

            private final double xpPillChance;

            private final double eliteXpPillChance;

            private final double equipBagChance;

            public DropSet(
                    boolean enabled,
                    double meowdanChance,
                    int[] meowdanQualityWeights,
                    double xpPillChance,
                    double eliteXpPillChance,
                    double equipBagChance
            ) {

                this.enabled = enabled;
                this.meowdanChance = meowdanChance;
                this.meowdanQualityWeights =
                        meowdanQualityWeights == null
                                ? new int[0]
                                : meowdanQualityWeights.clone();
                this.xpPillChance = xpPillChance;
                this.eliteXpPillChance =
                        eliteXpPillChance;
                this.equipBagChance = equipBagChance;
            }

            /*
             * 0.8.4 R18（社区上报 L-NEW-02）：
             * 权重数组返回克隆，快照真正不可变。
             */
            public int[] getMeowdanQualityWeights() {

                return meowdanQualityWeights.clone();
            }
        }
    }
}
