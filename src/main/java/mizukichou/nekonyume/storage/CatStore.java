package mizukichou.nekonyume.storage;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 猫咪数据存储抽象。
 *
 * <p>
 * 所有实现必须遵守 P0 四条不变量：
 * </p>
 *
 * <ol>
 *   <li>读操作永不建档：getter 遇到不存在的猫咪数据直接返回默认值；</li>
 *   <li>写操作永不复活：setter 遇到不存在的猫咪数据直接 no-op；</li>
 *   <li>创建数据只能通过 {@link #createCat} / {@link #ensureCat}；</li>
 *   <li>{@link #saveNow} 必须是原子写（磁盘实现），失败时保持脏标记。</li>
 * </ol>
 */
public interface CatStore {

    /*
     * ---------- 基础 ----------
     */

    boolean hasCat(UUID playerUUID);

    void createCat(UUID playerUUID);

    void ensureCat(UUID playerUUID);

    /*
     * ---------- 逻辑 UUID ----------
     */

    UUID getCatUUID(UUID playerUUID);

    void setCatUUID(UUID playerUUID, UUID catUUID);

    /*
     * ---------- 名称 ----------
     */

    String getCatName(UUID playerUUID);

    void setCatName(UUID playerUUID, String name);

    /*
     * ---------- 等级 / 经验 ----------
     */

    int getCatLevel(UUID playerUUID);

    void setCatLevel(UUID playerUUID, int level);

    void addCatLevel(UUID playerUUID, int amount);

    int getCatExperience(UUID playerUUID);

    void setCatExperience(UUID playerUUID, int experience);

    /*
     * ---------- 喵力 / 喵阶 ----------
     */

    int getCatMeowPower(UUID playerUUID);

    void setCatMeowPower(UUID playerUUID, int meowPower);

    int getCatMeowRank(UUID playerUUID);

    void setCatMeowRank(UUID playerUUID, int meowRank);

    /*
     * ---------- 好感度 ----------
     */

    int getCatAffection(UUID playerUUID);

    void setCatAffection(UUID playerUUID, int affection);

    void addCatAffection(UUID playerUUID, int amount);

    /*
     * ---------- 健康度 ----------
     */

    int getCatHealth(UUID playerUUID);

    void setCatHealth(UUID playerUUID, int health);

    void addCatHealth(UUID playerUUID, int amount);

    boolean isCatUnhealthy(UUID playerUUID);

    /*
     * ---------- 饱食度 ----------
     */

    int getCatHunger(UUID playerUUID);

    void setCatHunger(UUID playerUUID, int hunger);

    void addCatHunger(UUID playerUUID, int amount);

    void removeCatHunger(UUID playerUUID, int amount);

    boolean isCatHungry(UUID playerUUID);

    double getCatHungerPercent(UUID playerUUID);

    long getCatHungerLastUpdate(UUID playerUUID);

    void setCatHungerLastUpdate(UUID playerUUID, long timestamp);

    /*
     * ---------- 时间 ----------
     */

    long getCatCreatedAt(UUID playerUUID);

    void setCatCreatedAt(UUID playerUUID, long timestamp);

    long getCatLastFedAt(UUID playerUUID);

    void setCatLastFedAt(UUID playerUUID, long timestamp);

    long getCatLastInteractionAt(UUID playerUUID);

    void setCatLastInteractionAt(UUID playerUUID, long timestamp);

    /*
     * ---------- 每日计数 ----------
     */

    int getCatPetCount(UUID playerUUID);

    void addCatPetCount(UUID playerUUID);

    int getCatFeedCount(UUID playerUUID);

    void addCatFeedCount(UUID playerUUID);

    boolean isGiftCheckedToday(UUID playerUUID);

    void markGiftChecked(UUID playerUUID);

    /*
     * ---------- 行为模式 ----------
     */

    String getCatBehaviorMode(UUID playerUUID);

    void setCatBehaviorMode(UUID playerUUID, String mode);

    /*
     * ---------- 底蕴与技能 ----------
     */

    String getCatTier(UUID playerUUID);

    void setCatTier(UUID playerUUID, String tier);

    List<String> getCatSkills(UUID playerUUID);

    void setCatSkills(UUID playerUUID, List<String> skills);

    /*
     * ---------- 花色 ----------
     */

    String getCatVariant(UUID playerUUID);

    void setCatVariant(UUID playerUUID, String variant);

    /*
     * ---------- 实体绑定 ----------
     */

    UUID getCatEntityUUID(UUID playerUUID);

    void setCatEntityUUID(UUID playerUUID, UUID entityUUID);

    void removeCatEntityUUID(UUID playerUUID);

    boolean removeCat(UUID playerUUID);

    /*
     * ---------- 世界与坐标 ----------
     */

    UUID getCatWorldUUID(UUID playerUUID);

    void setCatWorldUUID(UUID playerUUID, UUID worldUUID);

    double getCatX(UUID playerUUID);

    double getCatY(UUID playerUUID);

    double getCatZ(UUID playerUUID);

    void setCatLocation(
            UUID playerUUID,
            UUID worldUUID,
            double x,
            double y,
            double z
    );

    /*
     * ---------- 成就 ----------
     */

    List<String> getAchievementsUnlockedList(UUID playerUUID);

    boolean isAchievementUnlocked(UUID playerUUID, String id);

    void addAchievementUnlocked(UUID playerUUID, String id);

    int getAchievementProgress(UUID playerUUID, String key);

    void setAchievementProgress(UUID playerUUID, String key, int value);

    void addAchievementProgress(UUID playerUUID, String key, int amount);

    /*
     * ---------- 集合 ----------
     */

    Set<UUID> getCatPlayers();

    /*
     * ---------- 保存生命周期 ----------
     */

    /**
     * 标记存在未落盘数据（不立即写磁盘）。
     */
    void save();

    boolean isDirty();

    /**
     * 若存在未落盘数据则立即写盘。
     */
    void flush();

    /**
     * 无条件立即写盘。磁盘实现必须原子写。
     */
    void saveNow();
}

