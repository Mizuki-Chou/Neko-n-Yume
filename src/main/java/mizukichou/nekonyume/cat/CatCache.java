package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 逻辑猫运行时缓存与持久化桥梁。
 *
 * <p>
 * 职责：
 * 1. 内存缓存（key = 逻辑猫 UUID）+ owner→catId 二级索引；
 * 2. 从 CatStore 加载 / 回写逻辑猫；
 * 3. 离线驱逐。
 * </p>
 *
 * <p>
 * 本类不接触 Bukkit 猫实体；
 * 实体相关一律由 CatEntityService 负责。
 * </p>
 */
public class CatCache {

    /*
     * 离线猫咪缓存驱逐阈值。
     *
     * 主人离线且超过该时长没有任何互动时，
     * 从运行时缓存卸载。数据已经保存，不会丢失。
     */
    private static final long EVICT_OFFLINE_MS =
            10 * 60 * 1000L;

    private final CatStore store;
    private final Logger logger;

    private final ConcurrentHashMap<UUID, Cat> cats =
            new ConcurrentHashMap<>();

    /*
     * owner UUID → 逻辑猫 UUID。
     * 使 getCat(owner) 从 O(n) 降为 O(1)。
     */
    private final ConcurrentHashMap<UUID, UUID> ownerToCatId =
            new ConcurrentHashMap<>();

    public CatCache(
            CatStore store,
            Logger logger
    ) {

        this.store = store;
        this.logger = logger;
    }

    /*
     * ============================================================
     * 逻辑猫咪查询
     * ============================================================
     */

    public Cat getCat(UUID ownerUUID) {

        if (ownerUUID == null) {
            return null;
        }

        UUID catId =
                ownerToCatId.get(ownerUUID);

        if (catId == null) {
            return null;
        }

        return cats.get(catId);
    }

    public Cat getCat(Player player) {

        if (player == null) {
            return null;
        }

        return getCat(player.getUniqueId());
    }

    public Cat getCatById(UUID catUUID) {

        if (catUUID == null) {
            return null;
        }

        return cats.get(catUUID);
    }

    public Cat getCatByEntity(UUID entityUUID) {

        if (entityUUID == null) {
            return null;
        }

        for (Cat cat : cats.values()) {

            if (entityUUID.equals(cat.getEntityUuid())) {
                return cat;
            }
        }

        return null;
    }

    public List<Cat> getCats() {

        return List.copyOf(cats.values());
    }

    /*
     * ============================================================
     * 缓存写入（统一维护二级索引）
     * ============================================================
     */

    private void register(Cat cat) {

        if (cat == null) {
            return;
        }

        cats.put(cat.getId(), cat);
        ownerToCatId.put(cat.getOwnerUuid(), cat.getId());
    }

    public void put(Cat cat) {

        register(cat);
    }

    public void removeByOwner(UUID ownerUUID) {

        if (ownerUUID == null) {
            return;
        }

        UUID catId =
                ownerToCatId.remove(ownerUUID);

        if (catId == null) {
            return;
        }

        cats.remove(catId);
    }

    public void clear() {

        cats.clear();
        ownerToCatId.clear();
    }

    /*
     * ============================================================
     * 离线驱逐
     * ============================================================
     *
     * 由 CatManager.saveAllCats() 在每次自动保存后调用。
     * 被驱逐的猫都已先完成回写，不会丢失任何状态。
     */

    public void evictOffline(long now) {

        List<Cat> toEvict = new ArrayList<>();

        for (Cat cat : cats.values()) {

            Player owner =
                    Bukkit.getPlayer(cat.getOwnerUuid());

            if ((owner == null || !owner.isOnline()) &&
                    now - cat.getLastInteractionAt()
                            > EVICT_OFFLINE_MS) {

                toEvict.add(cat);
            }
        }

        for (Cat cat : toEvict) {

            cats.remove(cat.getId());
            ownerToCatId.remove(
                    cat.getOwnerUuid(),
                    cat.getId()
            );
        }
    }

    /*
     * ============================================================
     * 从玩家存档加载
     * ============================================================
     */

    public Cat loadCat(Player player) {

        if (player == null) {
            return null;
        }

        return loadCat(
                player.getUniqueId(),
                player.getName()
        );
    }

    public Cat loadCat(UUID ownerUUID) {

        return loadCat(
                ownerUUID,
                ownerUUID == null
                        ? "unknown"
                        : ownerUUID.toString()
        );
    }

    private Cat loadCat(
            UUID ownerUUID,
            String logName
    ) {

        if (ownerUUID == null) {
            return null;
        }

        /*
         * 已经存在于内存中就直接返回。
         */
        Cat loaded = getCat(ownerUUID);

        if (loaded != null) {
            return loaded;
        }

        /*
         * P0 不变量：读操作永不建档。
         * 玩家没有猫咪数据时直接返回 null。
         */
        if (!store.hasCat(ownerUUID)) {
            return null;
        }

        /*
         * 猫咪永久 UUID。
         */
        UUID catUUID =
                store.getCatUUID(ownerUUID);

        if (catUUID == null) {

            catUUID = UUID.randomUUID();

            store.setCatUUID(ownerUUID, catUUID);
        }

        /*
         * 完整数据。
         */
        String name =
                store.getCatName(ownerUUID);

        int level =
                store.getCatLevel(ownerUUID);

        int affection =
                store.getCatAffection(ownerUUID);

        int hunger =
                store.getCatHunger(ownerUUID);

        int health =
                store.getCatHealth(ownerUUID);

        String variant =
                store.getCatVariant(ownerUUID);

        long createdAt =
                store.getCatCreatedAt(ownerUUID);

        long lastFedAt =
                store.getCatLastFedAt(ownerUUID);

        long lastInteractionAt =
                store.getCatLastInteractionAt(ownerUUID);

        /*
         * 从存档恢复完整 Cat。
         */
        Cat logicalCat =
                Cat.restore(
                        catUUID,
                        ownerUUID,
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

        /*
         * 双轨成长。
         */
        logicalCat.setExperience(
                store.getCatExperience(ownerUUID)
        );

        logicalCat.setMeowPower(
                store.getCatMeowPower(ownerUUID)
        );

        logicalCat.setMeowRank(
                store.getCatMeowRank(ownerUUID)
        );

        /*
         * 行为模式。
         */
        logicalCat.setBehaviorMode(
                CatBehaviorMode.fromName(
                        store.getCatBehaviorMode(ownerUUID)
                )
        );

        /*
         * 底蕴与技能。
         */
        applyTierAndSkills(
                logicalCat,
                ownerUUID,
                catUUID
        );

        /*
         * Entity UUID。
         */
        logicalCat.setEntityUuid(
                store.getCatEntityUUID(ownerUUID)
        );

        /*
         * 世界。
         */
        UUID worldUUID =
                store.getCatWorldUUID(ownerUUID);

        if (worldUUID != null) {

            World world =
                    Bukkit.getWorld(worldUUID);

            if (world != null) {

                logicalCat.setWorldName(
                        world.getName()
                );
            }
        }

        /*
         * 位置。
         */
        logicalCat.setX(
                store.getCatX(ownerUUID)
        );

        logicalCat.setY(
                store.getCatY(ownerUUID)
        );

        logicalCat.setZ(
                store.getCatZ(ownerUUID)
        );

        /*
         * 放入运行时缓存（维护索引）。
         */
        register(logicalCat);

        logger.info(
                "Loaded cat "
                        + logicalCat.getName()
                        + " ("
                        + logicalCat.getId()
                        + ") for "
                        + logName
        );

        return logicalCat;
    }

    /*
     * 从缓存移除后重新加载（供重载路径使用）。
     */
    public Cat reloadCat(Player player) {

        if (player == null) {
            return null;
        }

        removeByOwner(player.getUniqueId());

        return loadCat(player);
    }

    /*
     * ============================================================
     * 保存一只完整猫咪
     * ============================================================
     */

    public void saveCat(Cat cat) {

        if (cat == null) {
            return;
        }

        UUID ownerUUID = cat.getOwnerUuid();

        if (ownerUUID == null) {
            return;
        }

        /*
         * 基础身份
         */
        store.setCatUUID(ownerUUID, cat.getId());

        store.setCatName(ownerUUID, cat.getName());

        /*
         * 成长
         */
        store.setCatLevel(ownerUUID, cat.getLevel());

        store.setCatExperience(
                ownerUUID,
                cat.getExperience()
        );

        /*
         * 喵力 / 喵阶
         */
        store.setCatMeowPower(
                ownerUUID,
                cat.getMeowPower()
        );

        store.setCatMeowRank(
                ownerUUID,
                cat.getMeowRank()
        );

        /*
         * 行为模式
         */
        store.setCatBehaviorMode(
                ownerUUID,
                cat.getBehaviorMode().name()
        );

        /*
         * 底蕴与技能
         */
        store.setCatTier(
                ownerUUID,
                cat.getTier().name()
        );

        List<String> skillNames = new ArrayList<>();

        for (CatSkill skill : cat.getSkills()) {
            skillNames.add(skill.name());
        }

        store.setCatSkills(ownerUUID, skillNames);

        /*
         * 状态
         */
        store.setCatAffection(
                ownerUUID,
                cat.getAffection()
        );

        store.setCatHunger(
                ownerUUID,
                cat.getHunger()
        );

        store.setCatHealth(
                ownerUUID,
                cat.getHealth()
        );

        /*
         * 花色
         */
        if (cat.getVariant() != null &&
                !cat.getVariant().isBlank()) {

            store.setCatVariant(
                    ownerUUID,
                    cat.getVariant()
            );
        }

        /*
         * 时间
         */
        store.setCatCreatedAt(
                ownerUUID,
                cat.getCreatedAt()
        );

        store.setCatLastFedAt(
                ownerUUID,
                cat.getLastFedAt()
        );

        store.setCatLastInteractionAt(
                ownerUUID,
                cat.getLastInteractionAt()
        );

        /*
         * 当前 Bukkit Entity UUID
         */
        if (cat.getEntityUuid() != null) {

            store.setCatEntityUUID(
                    ownerUUID,
                    cat.getEntityUuid()
            );
        }

        /*
         * 当前保存位置
         */
        if (cat.getWorldName() != null &&
                !cat.getWorldName().isBlank()) {

            World world =
                    Bukkit.getWorld(cat.getWorldName());

            if (world != null) {

                store.setCatLocation(
                        ownerUUID,
                        world.getUID(),
                        cat.getX(),
                        cat.getY(),
                        cat.getZ()
                );
            }
        }
    }

    /*
     * ============================================================
     * 从存档恢复底蕴与技能槽
     * ============================================================
     */

    private void applyTierAndSkills(
            Cat logicalCat,
            UUID ownerUUID,
            UUID catUUID
    ) {

        CatTier tier =
                CatTier.fromName(
                        store.getCatTier(ownerUUID)
                );

        if (tier == null) {

            tier = CatTier.fromCatId(catUUID);
        }

        logicalCat.setTier(tier);

        List<CatSkill> skills = new ArrayList<>();

        for (String skillName :
                store.getCatSkills(ownerUUID)) {

            CatSkill skill =
                    CatSkill.fromName(skillName);

            if (skill != null && !skills.contains(skill)) {
                skills.add(skill);
            }
        }

        logicalCat.setSkills(skills);
    }
}