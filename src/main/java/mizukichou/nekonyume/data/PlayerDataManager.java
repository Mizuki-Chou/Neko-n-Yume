package mizukichou.nekonyume.data;

import mizukichou.nekonyume.storage.CatStore;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家猫咪数据存储门面。
 *
 * <p>
 * Step 3 起退化为薄适配器：
 * 字段语义与 P0 不变量集中在 storage.AbstractCatStore；
 * 磁盘实现（备份 / 迁移 / 原子写 / 损坏检测）在 storage.YamlCatStore。
 * </p>
 *
 * <p>
 * 本类保留全部历史公开签名并实现 CatStore 接口，
 * 既有调用方（CatCache / 监听器 / 任务等）零改动。
 * Step 5（去 Service Locator）完成后，调用方将直连 CatStore。
 * </p>
 */
public class PlayerDataManager implements CatStore {

    private final CatStore delegate;

    public PlayerDataManager(CatStore delegate) {

        this.delegate = delegate;
    }

    @Override
    public boolean hasCat(UUID playerUUID) {
        return delegate.hasCat(playerUUID);
    }

    @Override
    public void createCat(UUID playerUUID) {
        delegate.createCat(playerUUID);
    }

    @Override
    public void ensureCat(UUID playerUUID) {
        delegate.ensureCat(playerUUID);
    }

    @Override
    public UUID getCatUUID(UUID playerUUID) {
        return delegate.getCatUUID(playerUUID);
    }

    @Override
    public void setCatUUID(UUID playerUUID, UUID catUUID) {
        delegate.setCatUUID(playerUUID, catUUID);
    }

    @Override
    public String getCatName(UUID playerUUID) {
        return delegate.getCatName(playerUUID);
    }

    @Override
    public void setCatName(UUID playerUUID, String name) {
        delegate.setCatName(playerUUID, name);
    }

    @Override
    public int getCatLevel(UUID playerUUID) {
        return delegate.getCatLevel(playerUUID);
    }

    @Override
    public void setCatLevel(UUID playerUUID, int level) {
        delegate.setCatLevel(playerUUID, level);
    }

    @Override
    public void addCatLevel(UUID playerUUID, int amount) {
        delegate.addCatLevel(playerUUID, amount);
    }

    @Override
    public int getCatExperience(UUID playerUUID) {
        return delegate.getCatExperience(playerUUID);
    }

    @Override
    public void setCatExperience(UUID playerUUID, int experience) {
        delegate.setCatExperience(playerUUID, experience);
    }

    @Override
    public int getCatMeowPower(UUID playerUUID) {
        return delegate.getCatMeowPower(playerUUID);
    }

    @Override
    public void setCatMeowPower(UUID playerUUID, int meowPower) {
        delegate.setCatMeowPower(playerUUID, meowPower);
    }

    @Override
    public int getCatMeowRank(UUID playerUUID) {
        return delegate.getCatMeowRank(playerUUID);
    }

    @Override
    public void setCatMeowRank(UUID playerUUID, int meowRank) {
        delegate.setCatMeowRank(playerUUID, meowRank);
    }

    @Override
    public int getCatAffection(UUID playerUUID) {
        return delegate.getCatAffection(playerUUID);
    }

    @Override
    public void setCatAffection(UUID playerUUID, int affection) {
        delegate.setCatAffection(playerUUID, affection);
    }

    @Override
    public void addCatAffection(UUID playerUUID, int amount) {
        delegate.addCatAffection(playerUUID, amount);
    }

    @Override
    public int getCatHealth(UUID playerUUID) {
        return delegate.getCatHealth(playerUUID);
    }

    @Override
    public void setCatHealth(UUID playerUUID, int health) {
        delegate.setCatHealth(playerUUID, health);
    }

    @Override
    public void addCatHealth(UUID playerUUID, int amount) {
        delegate.addCatHealth(playerUUID, amount);
    }

    @Override
    public boolean isCatUnhealthy(UUID playerUUID) {
        return delegate.isCatUnhealthy(playerUUID);
    }

    @Override
    public int getCatHunger(UUID playerUUID) {
        return delegate.getCatHunger(playerUUID);
    }

    @Override
    public void setCatHunger(UUID playerUUID, int hunger) {
        delegate.setCatHunger(playerUUID, hunger);
    }

    @Override
    public void addCatHunger(UUID playerUUID, int amount) {
        delegate.addCatHunger(playerUUID, amount);
    }

    @Override
    public void removeCatHunger(UUID playerUUID, int amount) {
        delegate.removeCatHunger(playerUUID, amount);
    }

    @Override
    public boolean isCatHungry(UUID playerUUID) {
        return delegate.isCatHungry(playerUUID);
    }

    @Override
    public double getCatHungerPercent(UUID playerUUID) {
        return delegate.getCatHungerPercent(playerUUID);
    }

    @Override
    public long getCatHungerLastUpdate(UUID playerUUID) {
        return delegate.getCatHungerLastUpdate(playerUUID);
    }

    @Override
    public void setCatHungerLastUpdate(UUID playerUUID, long timestamp) {
        delegate.setCatHungerLastUpdate(playerUUID, timestamp);
    }

    @Override
    public long getCatCreatedAt(UUID playerUUID) {
        return delegate.getCatCreatedAt(playerUUID);
    }

    @Override
    public void setCatCreatedAt(UUID playerUUID, long timestamp) {
        delegate.setCatCreatedAt(playerUUID, timestamp);
    }

    @Override
    public long getCatLastFedAt(UUID playerUUID) {
        return delegate.getCatLastFedAt(playerUUID);
    }

    @Override
    public void setCatLastFedAt(UUID playerUUID, long timestamp) {
        delegate.setCatLastFedAt(playerUUID, timestamp);
    }

    @Override
    public long getCatLastInteractionAt(UUID playerUUID) {
        return delegate.getCatLastInteractionAt(playerUUID);
    }

    @Override
    public void setCatLastInteractionAt(UUID playerUUID, long timestamp) {
        delegate.setCatLastInteractionAt(playerUUID, timestamp);
    }

    @Override
    public int getCatPetCount(UUID playerUUID) {
        return delegate.getCatPetCount(playerUUID);
    }

    @Override
    public void addCatPetCount(UUID playerUUID) {
        delegate.addCatPetCount(playerUUID);
    }

    @Override
    public int getCatFeedCount(UUID playerUUID) {
        return delegate.getCatFeedCount(playerUUID);
    }

    @Override
    public void addCatFeedCount(UUID playerUUID) {
        delegate.addCatFeedCount(playerUUID);
    }

    @Override
    public boolean isGiftCheckedToday(UUID playerUUID) {
        return delegate.isGiftCheckedToday(playerUUID);
    }

    @Override
    public void markGiftChecked(UUID playerUUID) {
        delegate.markGiftChecked(playerUUID);
    }

    @Override
    public String getCatBehaviorMode(UUID playerUUID) {
        return delegate.getCatBehaviorMode(playerUUID);
    }

    @Override
    public void setCatBehaviorMode(UUID playerUUID, String mode) {
        delegate.setCatBehaviorMode(playerUUID, mode);
    }

    @Override
    public String getCatTier(UUID playerUUID) {
        return delegate.getCatTier(playerUUID);
    }

    @Override
    public void setCatTier(UUID playerUUID, String tier) {
        delegate.setCatTier(playerUUID, tier);
    }

    @Override
    public List<String> getCatSkills(UUID playerUUID) {
        return delegate.getCatSkills(playerUUID);
    }

    @Override
    public void setCatSkills(UUID playerUUID, List<String> skills) {
        delegate.setCatSkills(playerUUID, skills);
    }

    @Override
    public String getCatVariant(UUID playerUUID) {
        return delegate.getCatVariant(playerUUID);
    }

    @Override
    public void setCatVariant(UUID playerUUID, String variant) {
        delegate.setCatVariant(playerUUID, variant);
    }

    @Override
    public UUID getCatEntityUUID(UUID playerUUID) {
        return delegate.getCatEntityUUID(playerUUID);
    }

    @Override
    public void setCatEntityUUID(UUID playerUUID, UUID entityUUID) {
        delegate.setCatEntityUUID(playerUUID, entityUUID);
    }

    @Override
    public void removeCatEntityUUID(UUID playerUUID) {
        delegate.removeCatEntityUUID(playerUUID);
    }

    @Override
    public boolean removeCat(UUID playerUUID) {
        return delegate.removeCat(playerUUID);
    }

    @Override
    public UUID getCatWorldUUID(UUID playerUUID) {
        return delegate.getCatWorldUUID(playerUUID);
    }

    @Override
    public void setCatWorldUUID(UUID playerUUID, UUID worldUUID) {
        delegate.setCatWorldUUID(playerUUID, worldUUID);
    }

    @Override
    public double getCatX(UUID playerUUID) {
        return delegate.getCatX(playerUUID);
    }

    @Override
    public double getCatY(UUID playerUUID) {
        return delegate.getCatY(playerUUID);
    }

    @Override
    public double getCatZ(UUID playerUUID) {
        return delegate.getCatZ(playerUUID);
    }

    @Override
    public void setCatLocation(
            UUID playerUUID,
            UUID worldUUID,
            double x,
            double y,
            double z
    ) {

        delegate.setCatLocation(
                playerUUID,
                worldUUID,
                x,
                y,
                z
        );
    }

    @Override
    public List<String> getAchievementsUnlockedList(UUID playerUUID) {
        return delegate.getAchievementsUnlockedList(playerUUID);
    }

    @Override
    public boolean isAchievementUnlocked(UUID playerUUID, String id) {
        return delegate.isAchievementUnlocked(playerUUID, id);
    }

    @Override
    public void addAchievementUnlocked(UUID playerUUID, String id) {
        delegate.addAchievementUnlocked(playerUUID, id);
    }

    @Override
    public int getAchievementProgress(UUID playerUUID, String key) {
        return delegate.getAchievementProgress(playerUUID, key);
    }

    @Override
    public void setAchievementProgress(UUID playerUUID, String key, int value) {
        delegate.setAchievementProgress(playerUUID, key, value);
    }

    @Override
    public void addAchievementProgress(UUID playerUUID, String key, int amount) {
        delegate.addAchievementProgress(playerUUID, key, amount);
    }

    @Override
    public List<String> getAchievementsPendingList(UUID playerUUID) {
        return delegate.getAchievementsPendingList(playerUUID);
    }

    @Override
    public void addAchievementPending(UUID playerUUID, String id) {
        delegate.addAchievementPending(playerUUID, id);
    }

    @Override
    public void removeAchievementPending(UUID playerUUID, String id) {
        delegate.removeAchievementPending(playerUUID, id);
    }

    @Override
    public Set<UUID> getCatPlayers() {
        return delegate.getCatPlayers();
    }

    @Override
    public void save() {
        delegate.save();
    }

    @Override
    public boolean isDirty() {
        return delegate.isDirty();
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void saveNow() {
        delegate.saveNow();
    }
}
