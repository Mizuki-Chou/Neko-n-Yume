package mizukichou.nekonyume.cat;

import java.util.UUID;

/**
 * 猫咪性格。
 *
 * <p>
 * 由逻辑猫 UUID 哈希确定性生成，
 * 不存储、不迁移。
 * </p>
 *
 * <p>
 * 每只猫的性格永久固定，
 * 与其"身份永恒"的理念一致。
 * </p>
 */
public enum CatPersonality {

    /*
     * 贪吃：
     * 饥饿更快，喂食好感更多，
     * 喂食更容易获得喵力。
     */
    GOURMAND(
            "贪吃",
            1.5,
            3,
            3,
            0,
            1000L,
            1.0,
            0
    ),

    /*
     * 悠闲：
     * 饥饿更慢。
     */
    LAZY(
            "悠闲",
            0.7,
            0,
            0,
            0,
            1000L,
            1.0,
            0
    ),

    /*
     * 粘人：
     * 抚摸冷却更短，
     * 抚摸更容易获得喵力。
     */
    CLINGY(
            "粘人",
            1.0,
            0,
            0,
            2,
            500L,
            1.0,
            0
    ),

    /*
     * 独立：
     * 饥饿略慢，
     * 但抚摸更难获得喵力。
     */
    INDEPENDENT(
            "独立",
            0.9,
            0,
            0,
            -2,
            1000L,
            1.0,
            0
    ),

    /*
     * 挑食：
     * 食物效果打折扣，
     * 喂食更难获得喵力。
     */
    PICKY(
            "挑食",
            1.0,
            0,
            -3,
            0,
            1000L,
            0.8,
            0
    ),

    /*
     * 阳光：
     * 心情恒定向好，
     * 双轨喵力概率各 +1%。
     */
    SUNNY(
            "阳光",
            1.0,
            0,
            1,
            1,
            1000L,
            1.0,
            10
    );

    private final String displayName;

    /*
     * 饥饿速率倍率。
     * >1 饿得快，<1 饿得慢。
     */
    private final double hungerRate;

    /*
     * 喂食好感额外加成。
     */
    private final int feedAffectionBonus;

    /*
     * 喂食喵力概率偏移（百分点）。
     */
    private final int feedMeowChanceBonus;

    /*
     * 抚摸喵力概率偏移（百分点）。
     */
    private final int petMeowChanceBonus;

    /*
     * 抚摸冷却（毫秒）。
     */
    private final long petCooldownMillis;

    /*
     * 食物效果倍率。
     */
    private final double foodValueMultiplier;

    /*
     * 心情得分偏移。
     */
    private final int moodBonus;

    CatPersonality(
            String displayName,
            double hungerRate,
            int feedAffectionBonus,
            int feedMeowChanceBonus,
            int petMeowChanceBonus,
            long petCooldownMillis,
            double foodValueMultiplier,
            int moodBonus
    ) {

        this.displayName = displayName;
        this.hungerRate = hungerRate;
        this.feedAffectionBonus = feedAffectionBonus;
        this.feedMeowChanceBonus = feedMeowChanceBonus;
        this.petMeowChanceBonus = petMeowChanceBonus;
        this.petCooldownMillis = petCooldownMillis;
        this.foodValueMultiplier = foodValueMultiplier;
        this.moodBonus = moodBonus;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getHungerRate() {
        return hungerRate;
    }

    public int getFeedAffectionBonus() {
        return feedAffectionBonus;
    }

    public int getFeedMeowChanceBonus() {
        return feedMeowChanceBonus;
    }

    public int getPetMeowChanceBonus() {
        return petMeowChanceBonus;
    }

    public long getPetCooldownMillis() {
        return petCooldownMillis;
    }

    public double getFoodValueMultiplier() {
        return foodValueMultiplier;
    }

    public int getMoodBonus() {
        return moodBonus;
    }

    /*
     * ============================================================
     * 由逻辑猫 UUID 确定性生成
     * ============================================================
     *
     * 同一 UUID 永远得到同一性格。
     * UUID.hashCode() 是确定性的，
     * floorMod 保证索引非负。
     */

    public static CatPersonality fromCatId(
            UUID catId
    ) {

        if (catId == null) {
            return LAZY;
        }

        int index =
                Math.floorMod(
                        catId.hashCode(),
                        values().length
                );

        return values()[index];
    }
}
