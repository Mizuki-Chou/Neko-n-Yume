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
     * ---------- 日常衰减结算（0.8.0） ----------
     *
     * 好感日常衰减的结算锚点（世界日期字符串）；
     * 空串表示尚未结算。
     */

    String getAffectionDecayDate(UUID playerUUID);

    void setAffectionDecayDate(
            UUID playerUUID,
            String date
    );

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
     * ---------- 装备（0.8.0，唯一装备位） ----------
     */

    String getCatEquipment(UUID playerUUID);

    void setCatEquipment(UUID playerUUID, String equipment);

    /*
     * ---------- 装备附加属性（0.8.0，与装备位绑定） ----------
     */

    String getCatEquipmentBonus(UUID playerUUID);

    void setCatEquipmentBonus(UUID playerUUID, String bonus);

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
     * 奖励待发队列（P0-2 崩溃恢复）+ 奖励台账（0.7.4 防重）。
     *
     * 三态设计：
     *   unlocked（已解锁）→ pending（待发）→ rewarded（已发）。
     *
     * 发放顺序为"先记台账 → 再发奖励 → 最后清 pending"：
     * 台账与经验/喵力同文档同快照原子落盘，任何进程崩溃点
     * 都不会出现"奖励已落盘而台账未落盘"的组合，
     * 补发路径先查台账，杜绝重复发放。
     */

    List<String> getAchievementsPendingList(UUID playerUUID);

    void addAchievementPending(UUID playerUUID, String id);

    void removeAchievementPending(UUID playerUUID, String id);

    List<String> getAchievementsRewardedList(UUID playerUUID);

    boolean isAchievementRewarded(UUID playerUUID, String id);

    void addAchievementRewarded(UUID playerUUID, String id);

    /*
     * 0.8.4 R17（社区上报）：
     * 逐币种奖励已发放标记（幂等发放协议）。
     */

    boolean isAchievementRewardXpApplied(UUID playerUUID, String id);

    void addAchievementRewardXpApplied(UUID playerUUID, String id);

    void removeAchievementRewardXpApplied(UUID playerUUID, String id);

    boolean isAchievementRewardMeowApplied(UUID playerUUID, String id);

    void addAchievementRewardMeowApplied(UUID playerUUID, String id);

    void removeAchievementRewardMeowApplied(UUID playerUUID, String id);

    /*
     * ---------- 集合 ----------
     */

    Set<UUID> getCatPlayers();

    /*
     * ---------- 保存生命周期 ----------
     *
     * 三档语义（0.7.4 修订，如实描述）：
     *
     * save()
     *   仅标记脏（内存），不生成快照、不写盘。
     *
     * flush() / saveNow()
     *   生成快照并提交给后台保存线程，方法返回时
     *   磁盘写入尚未完成（异步写盘协议）。
     *   flush 仅在脏时提交；saveNow 无条件提交。
     *   两者在耐久性上等价——所谓"立即保存"
     *   的准确含义是"立即进入写盘队列"。
     *
     * awaitPendingSave(ms)
     *   阻塞主线程直到在飞快照写盘完成（上限 ms）；
     *   仅用于低频关键操作（建档 / 删档 / 关服），
     *   常规路径保持异步以保证 TPS。
     *
     * 崩溃窗口契约：普通变更在自动保存周期（默认 60s）、
     * 玩家退出、关服三处落盘；进程被 SIGKILL 时，
     * 距上次成功落盘的变更最多丢失一个周期。
     * 这是异步保存设计的固有取舍，业务层不得假设
     * flush()/saveNow() 返回时数据已持久。
     */

    /**
     * 标记存在未落盘数据（内存脏标记，不写盘）。
     */
    void save();

    boolean isDirty();

    /**
     * 脏时提交快照给后台保存线程（不等待磁盘完成）。
     */
    void flush();

    /**
     * 无条件提交快照给后台保存线程（不等待磁盘完成）。
     * 磁盘实现必须原子写（tmp + fsync + ATOMIC_MOVE），
     * 失败时保持快照待写与脏标记。
     */
    void saveNow();

    /**
     * 阻塞直到在飞快照写盘完成，上限 timeoutMillis。
     * 默认实现为空操作（内存实现无磁盘概念）；
     * 磁盘实现重写为真实等待。
     */
    default boolean awaitPendingSave(long timeoutMillis) {

        // 内存实现：无磁盘，无需等待。
        return true;
    }

    /**
     * 最近一次磁盘写入是否失败（用于业务层探测耐久性异常）。
     * 默认 false（内存实现永不失败）；磁盘实现重写。
     */
    default boolean isLastWriteFailed() {

        return false;
    }
}
