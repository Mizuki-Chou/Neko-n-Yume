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
import java.nio.charset.StandardCharsets;

/**
 * 逻辑猫运行时缓存与持久化桥梁。
 *
 * <p>
 * 职责：
 * 1. 内存缓存（key = 逻辑猫 UUID）+ owner→catId 二级索引；
 * 2. 从 CatStore 加载 / 回写逻辑猫捏；
 * 3. 离线驱逐哒。
 * </p>
 *
 * <p>
 * 线程契约：仅主线程访问（主线程单写模型）。
 * 索引结构使用 ConcurrentHashMap 是防御性选择，
 * 不代表并发写安全——register/evict 的复合更新
 * 必须保持在主线程串行执行。
 * </p>
 *
 * <p>
 * 本类不接触 Bukkit 猫实体；
 * 实体相关一律由 CatEntityService 负责啦。
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

    /*
     * Bukkit 实体 UUID → 逻辑猫 UUID 反向索引（P1-5）。
     *
     * 使 getCatByEntity 从 O(n) 降为 O(1)。
     * 由于 Cat.setEntityUuid 是外部可直接调用的普通 setter，
     * 索引可能短暂陈旧；getCatByEntity 在命中时
     * 校验 cat.getEntityUuid() 与查询键一致，
     * 不一致则回退全量扫描并自愈索引，正确性优先。
     */
    private final ConcurrentHashMap<UUID, UUID> entityToCatId =
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

        /*
         * 快速路径：索引命中且校验一致。
         */
        UUID indexedCatId =
                entityToCatId.get(entityUUID);

        if (indexedCatId != null) {

            Cat indexed =
                    cats.get(indexedCatId);

            if (indexed != null &&
                    entityUUID.equals(
                            indexed.getEntityUuid()
                    )) {

                return indexed;
            }
        }

        /*
         * 回退全量扫描，并自愈索引。
         */
        for (Cat cat : cats.values()) {

            if (entityUUID.equals(cat.getEntityUuid())) {

                entityToCatId.put(
                        entityUUID,
                        cat.getId()
                );

                return cat;
            }
        }

        /*
         * 索引陈旧（实体已解绑）：清除脏条目。
         *
         * 注意：ConcurrentHashMap.remove(key, null) 会抛 NPE，
         * 快速路径未命中（indexedCatId == null）时绝不能调双参版。
         */
        if (indexedCatId != null) {

            entityToCatId.remove(
                    entityUUID,
                    indexedCatId
            );
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

        UUID ownerUuid =
                cat.getOwnerUuid();

        /*
         * 0.9.0更新：Cat UUID 唯一性不变量——
         * 不赌 UUID.randomUUID() 的随机性，持久化边界
         * （存档加载/迁移/人工修改 YAML/备份恢复）必须
         * 把撞号当数据损坏：不同主人撞号 → 拒绝注册
         * （绝不静默覆盖，避免 getCat(A) 返回 B 的猫）。
         */
        Cat collided =
                cats.get(
                        cat.getId()
                );

        if (collided != null &&
                !collided.getOwnerUuid()
                        .equals(
                                ownerUuid
                        )) {

            logger.log(
                    java.util.logging.Level.SEVERE,
                    "Cat UUID collision: " + cat.getId()
                            + " is owned by both "
                            + collided.getOwnerUuid()
                            + " and " + ownerUuid
                            + " — refusing to register "
                            + "the second cat (data corruption)."
            );

            throw new IllegalStateException(
                    "Cat UUID collision detected: "
                            + cat.getId()
            );
        }

        /*
         * P0-2 / P0-6：
         * 维护"一玩家一猫"不变量与索引一致性——
         * 同一主人新猫注册时，先移除旧猫条目与索引，
         * 绝不残留两条逻辑猫。
         */
        UUID oldCatId =
                ownerToCatId.remove(
                        ownerUuid
                );

        if (oldCatId != null) {

            Cat oldCat =
                    cats.remove(oldCatId);

            if (oldCat != null &&
                    oldCat.getEntityUuid() != null) {

                entityToCatId.remove(
                        oldCat.getEntityUuid(),
                        oldCatId
                );
            }
        }

        cats.put(cat.getId(), cat);
        ownerToCatId.put(ownerUuid, cat.getId());

        if (cat.getEntityUuid() != null) {

            entityToCatId.put(
                    cat.getEntityUuid(),
                    cat.getId()
            );
        }
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

        Cat removed =
                cats.remove(catId);

        if (removed != null &&
                removed.getEntityUuid() != null) {

            entityToCatId.remove(
                    removed.getEntityUuid(),
                    catId
            );
        }
    }

    public void clear() {

        cats.clear();
        ownerToCatId.clear();
        entityToCatId.clear();
    }

    /*
     * ============================================================
     * 离线驱逐
     * ============================================================
     *
     * 由 CatManager.saveAllCats() 在每次自动保存后调用。
     * 只驱逐本轮
     * “已成功回写”的猫——保存失败的猫保留在内存中
     * 下一轮重试，绝不把未落盘的更新状态扔掉。
     */

    public void evictOffline(
            long now,
            java.util.Collection<Cat> candidates
    ) {

        if (candidates == null ||
                candidates.isEmpty()) {

            return;
        }

        for (Cat cat : candidates) {

            if (cat == null) {
                continue;
            }

            Player owner =
                    Bukkit.getPlayer(cat.getOwnerUuid());

            if ((owner == null || !owner.isOnline()) &&
                    now - cat.getLastInteractionAt()
                            > EVICT_OFFLINE_MS) {

                cats.remove(
                        cat.getId()
                );

                ownerToCatId.remove(
                        cat.getOwnerUuid(),
                        cat.getId()
                );

                if (cat.getEntityUuid() != null) {

                    entityToCatId.remove(
                            cat.getEntityUuid(),
                            cat.getId()
                    );
                }
            }
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

            /*
             * 数据异常：猫节点存在但缺失 id。
             *
             * 绝不能用随机 UUID 修复：
             * 性格与底蕴都由猫 UUID 确定性推导，
             * 随机会静默改变这两项，
             * 而且每次重启都可能产生不同的猫。
             *
             * 改用"由主人 UUID 确定性推导"修复：
             * 结果稳定、可复现，并在日志中留下痕迹。
             */
            catUUID =
                    UUID.nameUUIDFromBytes(
                            ("nekonyume-cat-" + ownerUUID)
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    )
                    );

            store.setCatUUID(ownerUUID, catUUID);

            logger.warning(
                    "Cat for "
                            + ownerUUID
                            + " was missing its id; repaired deterministically as "
                            + catUUID
            );
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

        /*
         * 0.9.0更新：XP/level 双真相一致性——
         * setExperience 不重推等级，损坏/迁移存档可能
         * 让 restore 的 level 与累计经验矛盾；加载时以
         * 经验为真相重推（等级由经验推导是领域不变量）。
         */
        int derivedLevel =
                GrowthMath.levelFromExperience(
                        logicalCat.getExperience(),
                        GrowthMath.DEFAULT_XP_CURVE_BASE
                );

        if (logicalCat.getLevel() != derivedLevel) {

            logger.log(
                    java.util.logging.Level.WARNING,
                    "Inconsistent level for cat " + catUUID
                            + ": stored level "
                            + logicalCat.getLevel()
                            + " vs experience-derived "
                            + derivedLevel
                            + " — re-deriving from experience."
            );

            logicalCat.setLevel(
                    derivedLevel
            );
        }

        /*
         * 视觉模型（Generic Model 系统，预留）。
         *
         * null = 默认模型；setter 会把空串归一化为 null。
         */
        logicalCat.setModelId(
                store.getCatModelId(ownerUUID)
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
         * 装备（0.8.0）。
         */
        logicalCat.setEquippedItem(
                CatEquipItem.fromCode(
                        store.getCatEquipment(ownerUUID)
                )
        );

        logicalCat.setEquippedBonus(
                EquipBonusAttribute.fromCode(
                        store.getCatEquipmentBonus(ownerUUID)
                )
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

            logicalCat.setWorldUUID(
                    worldUUID.toString()
            );

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
         * 0.9.0更新：朝向恢复——此前重启必丢，这个坑藏得可深了。
         */
        logicalCat.setYaw(
                store.getCatYaw(ownerUUID)
        );

        logicalCat.setPitch(
                store.getCatPitch(ownerUUID)
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

        /*
         * 0.9.0更新：重载前先落盘运行时
         * 状态——reload 语义是"reloadAfterSave"，
         * 绝不静默丢弃未持久化的内存修改。
         */
        Cat oldCat =
                cats.get(
                        player.getUniqueId()
                );

        if (oldCat != null) {

            saveCat(oldCat);
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
         * 0.9.0更新：拒绝陈旧引用覆盖当前存档——
         * 事件/异步回调/GUI 可能长期持有旧 Cat 对象；只有
         * "当前缓存中的那只猫"才有资格写回 Store。
         */
        Cat current =
                cats.get(
                        cat.getId()
                );

        if (current != cat) {

            logger.warning(
                    "Rejected stale saveCat for cat "
                            + cat.getId()
                            + " (owner " + ownerUUID + ")."
            );

            return;
        }

        /*
         * 性能优化：只写"与存档不同"的字段。
         *
         * 此前无条件重写全部字段并标记脏，
         * 导致即使没有任何变化，自动保存每 60 秒
         * 仍要全量序列化整个 players.yml。
         * 改为逐字段比对后：
         * - 无变化的猫不触碰存储；
         * - flush() 见不到脏标记，磁盘零写入；
         * - 真正变化时才序列化落盘。
         * 行为与原实现完全一致。
         */

        if (!java.util.Objects.equals(
                store.getCatUUID(ownerUUID),
                cat.getId()
        )) {

            store.setCatUUID(ownerUUID, cat.getId());
        }

        if (!java.util.Objects.equals(
                store.getCatName(ownerUUID),
                cat.getName()
        )) {

            store.setCatName(ownerUUID, cat.getName());
        }

        if (store.getCatLevel(ownerUUID)
                != cat.getLevel()) {

            store.setCatLevel(ownerUUID, cat.getLevel());
        }

        if (store.getCatExperience(ownerUUID)
                != cat.getExperience()) {

            store.setCatExperience(
                    ownerUUID,
                    cat.getExperience()
            );
        }

        if (store.getCatMeowPower(ownerUUID)
                != cat.getMeowPower()) {

            store.setCatMeowPower(
                    ownerUUID,
                    cat.getMeowPower()
            );
        }

        if (store.getCatMeowRank(ownerUUID)
                != cat.getMeowRank()) {

            store.setCatMeowRank(
                    ownerUUID,
                    cat.getMeowRank()
            );
        }

        if (!java.util.Objects.equals(
                store.getCatBehaviorMode(ownerUUID),
                cat.getBehaviorMode().name()
        )) {

            store.setCatBehaviorMode(
                    ownerUUID,
                    cat.getBehaviorMode().name()
            );
        }

        if (!java.util.Objects.equals(
                store.getCatTier(ownerUUID),
                cat.getTier().name()
        )) {

            store.setCatTier(
                    ownerUUID,
                    cat.getTier().name()
            );
        }

        List<String> skillNames = new ArrayList<>();

        for (CatSkill skill : cat.getSkills()) {
            skillNames.add(skill.name());
        }

        if (!skillNames.equals(
                store.getCatSkills(ownerUUID)
        )) {

            store.setCatSkills(ownerUUID, skillNames);
        }

        if (store.getCatAffection(ownerUUID)
                != cat.getAffection()) {

            store.setCatAffection(
                    ownerUUID,
                    cat.getAffection()
            );
        }

        if (store.getCatHunger(ownerUUID)
                != cat.getHunger()) {

            store.setCatHunger(
                    ownerUUID,
                    cat.getHunger()
            );
        }

        if (store.getCatHealth(ownerUUID)
                != cat.getHealth()) {

            store.setCatHealth(
                    ownerUUID,
                    cat.getHealth()
            );
        }

        /*
         * 0.9.0更新：花色对称写——
         * 两侧归一化为空串后比较，null 变体同样可清除
         * （旧花色不再"死而复生"）。
         */
        if (!java.util.Objects.equals(
                store.getCatVariant(ownerUUID),
                cat.getVariant() == null
                        ? ""
                        : cat.getVariant()
        )) {

            store.setCatVariant(
                    ownerUUID,
                    cat.getVariant() == null
                            ? ""
                            : cat.getVariant()
            );
        }

        /*
         * 视觉模型（Generic Model 系统，预留）：
         * 对称写——两侧都归一化为空串后比较，空态收敛不写。
         */
        if (!java.util.Objects.equals(
                store.getCatModelId(ownerUUID),
                cat.getModelId() == null
                        ? ""
                        : cat.getModelId()
        )) {

            store.setCatModelId(
                    ownerUUID,
                    cat.getModelId()
            );
        }

        /*
         * 装备（0.8.0）：对称写——支持卸下（空串）。
         */
        if (!java.util.Objects.equals(
                store.getCatEquipment(ownerUUID),
                cat.getEquippedItem() == null
                        ? ""
                        : cat.getEquippedItem().getCode()
        )) {

            store.setCatEquipment(
                    ownerUUID,
                    cat.getEquippedItem() == null
                            ? ""
                            : cat.getEquippedItem().getCode()
            );
        }

        /*
         * 装备附加属性（0.8.0）：对称写——与装备位绑定。
         */
        if (!java.util.Objects.equals(
                store.getCatEquipmentBonus(ownerUUID),
                cat.getEquippedBonus() == null
                        ? ""
                        : cat.getEquippedBonus().getCode()
        )) {

            store.setCatEquipmentBonus(
                    ownerUUID,
                    cat.getEquippedBonus() == null
                            ? ""
                            : cat.getEquippedBonus().getCode()
            );
        }

        if (store.getCatCreatedAt(ownerUUID)
                != cat.getCreatedAt()) {

            store.setCatCreatedAt(
                    ownerUUID,
                    cat.getCreatedAt()
            );
        }

        if (store.getCatLastFedAt(ownerUUID)
                != cat.getLastFedAt()) {

            store.setCatLastFedAt(
                    ownerUUID,
                    cat.getLastFedAt()
            );
        }

        if (store.getCatLastInteractionAt(ownerUUID)
                != cat.getLastInteractionAt()) {

            store.setCatLastInteractionAt(
                    ownerUUID,
                    cat.getLastInteractionAt()
            );
        }

        /*
         * P1-13：实体绑定全量对称同步。
         *
         * 运行时与存档不一致时无条件写入——
         * 包括运行时为 null 而存档仍残留旧 UUID 的情况
         * （此时显式清除存档字段），
         * saveCat 因此成为 Cat → Store 的完整状态同步器，
         * 而不是"部分字段同步器"。
         */
        if (!java.util.Objects.equals(
                cat.getEntityUuid(),
                store.getCatEntityUUID(ownerUUID)
        )) {

            if (cat.getEntityUuid() == null) {

                store.removeCatEntityUUID(
                        ownerUUID
                );

            } else {

                store.setCatEntityUUID(
                        ownerUUID,
                        cat.getEntityUuid()
                );
            }
        }

                /*
         * 0.9.0更新：位置持久化不依赖 World
         * 已加载——世界卸载期间的位置更新同样落盘（setRaw
         * 自带"值未变化跳过"，无条件调用零额外开销）。
         */
        if (cat.getWorldUUID() != null) {

            try {

                store.setCatLocation(
                        ownerUUID,
                        UUID.fromString(
                                cat.getWorldUUID()
                        ),
                        cat.getX(),
                        cat.getY(),
                        cat.getZ()
                );

            } catch (IllegalArgumentException exception) {

                logger.warning(
                        "Invalid world UUID on cat "
                                + cat.getId()
                                + " - location save skipped."
                );
            }

            if (Float.compare(
                    store.getCatYaw(ownerUUID),
                    cat.getYaw()
            ) != 0) {

                store.setCatYaw(
                        ownerUUID,
                        cat.getYaw()
                );
            }

            if (Float.compare(
                    store.getCatPitch(ownerUUID),
                    cat.getPitch()
            ) != 0) {

                store.setCatPitch(
                        ownerUUID,
                        cat.getPitch()
                );
            }
        }
    }
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

