package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.skill.CatBattleState;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * 逻辑猫与 Bukkit 实体的绑定层。
 *
 * <p>
 * 职责（从 CatEntityService 拆分）：
 * 1. PDC Keys（catKey / ownerKey，由组合根注入）；
 * 2. bindLogicalCat / updateCat：实体绑定与基础属性刷写；
 * 3. 名称 / 行为模式 / 头顶名称；
 * 4. captureEntityState：自动保存前从实体捕获状态；
 * 5. syncLogicalCatLocation / saveCatLocation：位置双向同步。
 * </p>
 *
 * <p>
 * 单线程模型：全部方法在主线程调用。
 * </p>
 */
public class CatEntityBinding {

    private final CatStore store;
    private final CatCache cache;
    private final CatProgressionService progression;
    private final CatVariantService variantService;
    private final CatBattleState battleState;
    private final Lang lang;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    /*
     * 0.8.3：实体索引（恢复/清理路径的 O(1) 加速器）。
     */
    private final CatEntityIndex entityIndex;

    /*
     * 0.8.4：实体运行时 seam（生产委托 Bukkit，测试用 fake）。
     */
    private final CatEntityRuntime runtime;

    public CatEntityBinding(
            CatStore store,
            CatCache cache,
            CatProgressionService progression,
            CatVariantService variantService,
            CatBattleState battleState,
            Lang lang,
            NamespacedKey catKey,
            NamespacedKey ownerKey,
            CatEntityIndex entityIndex,
            CatEntityRuntime runtime
    ) {

        this.store = store;
        this.cache = cache;
        this.progression = progression;
        this.variantService = variantService;
        this.battleState = battleState;
        this.lang = lang;
        this.catKey = catKey;
        this.ownerKey = ownerKey;
        this.entityIndex = entityIndex;
        this.runtime = runtime;
    }

    /*
     * ============================================================
     * PDC Keys
     * ============================================================
     */

    public NamespacedKey getCatKey() {
        return catKey;
    }

    public NamespacedKey getOwnerKey() {
        return ownerKey;
    }

    /*
     * ============================================================
     * 从实体捕获状态（自动保存前调用）
     * ============================================================
     *
     * 实体在线时，把当前花色与位置同步回逻辑猫，
     * 供 CatManager.saveAllCats() 回写。
     */

    public void captureEntityState(Cat logicalCat) {

        if (logicalCat == null) {
            return;
        }

        UUID entityUUID =
                logicalCat.getEntityUuid();

        if (entityUUID == null) {
            return;
        }

        Entity entity =
                runtime.getEntity(entityUUID);

        if (!(entity instanceof org.bukkit.entity.Cat bukkitCat) ||
                bukkitCat.isDead() ||
                !bukkitCat.isValid()) {

            return;
        }

        NamespacedKey key =
                runtime
                        .typeKey(
                                bukkitCat.getCatType()
                        );

        if (key != null) {

            logicalCat.setVariant(
                    key.toString()
            );
        }

        syncLogicalCatLocation(
                logicalCat,
                bukkitCat
        );
    }

    /*
     * ============================================================
     * 清除实体绑定（不删除逻辑猫）
     * ============================================================
     */

    public void clearEntityBinding(UUID playerUUID) {

        if (playerUUID == null) {
            return;
        }

        /*
         * 0.8.3：同步撤销实体索引条目。
         */
        UUID entityUuid =
                store.getCatEntityUUID(playerUUID);

        if (entityUuid != null) {

            entityIndex.removeEntity(entityUuid);
        }

        Cat cat = cache.getCat(playerUUID);

        if (cat != null) {

            cat.setEntityUuid(null);
        }

        store.removeCatEntityUUID(playerUUID);
    }

    /*
     * ============================================================
     * 创建 / 绑定逻辑猫
     * ============================================================
     *
     * 补丁 2：
     * 绑定完成后立即调用 syncSkillSlots，
     * 梦幻猫出生即拥有梦槽技能。
     */

    Cat bindLogicalCat(
            Player player,
            org.bukkit.entity.Cat entity,
            String name
    ) {

        UUID ownerUUID =
                player.getUniqueId();

        /*
         * 0.8.1 修复（R3，社区上报：删除后复活）：
         * 绑定前必须已有玩家数据。
         *
         * 旧实现在这里调用 store.ensureCat 兜底建档——
         * 一旦管理员删除猫咪后仍有旧异步召唤回调回到主线程，
         * 本方法会重新创建已被删除的数据，猫直接“复活”。
         *
         * 现在：无数据即返回 null，由调用方中止流水线；
         * 建档只走 AbstractCatStore.createCat（领取路径）这一唯一入口，
         * 与 P0“创建唯一入口”不变量重新对齐。
         */
        if (!store.hasCat(ownerUUID)) {
            return null;
        }

        Cat logicalCat =
                cache.getCat(ownerUUID);

        if (logicalCat == null) {

            logicalCat =
                    cache.loadCat(player);
        }

        if (logicalCat == null) {
            return null;
        }

        logicalCat.setEntityUuid(
                entity.getUniqueId()
        );

        logicalCat.setName(name);

        syncLogicalCatLocation(
                logicalCat,
                entity
        );

        variantService.restoreVariant(
                ownerUUID,
                entity,
                logicalCat
        );

        /*
         * 补丁 2：
         * 梦幻猫出生即有 1 个梦槽，
         * 绑定完成必须同步一次技能槽，保证出生即抽取。
         */
        progression.syncSkillSlots(
                player,
                logicalCat
        );

        return logicalCat;
    }

    /*
     * ============================================================
     * 更新 Bukkit 猫实体
     * ============================================================
     */

    void updateCat(
            org.bukkit.entity.Cat cat,
            Player player,
            String name
    ) {

        /*
         * 过滤 §，防止名字注入传统颜色码。
         */
        String safeName =
                name == null
                        ? ""
                        : name.replace("§", "");

        cat.setCustomName(
                "§d🐱 " + safeName
        );

        cat.setCustomNameVisible(true);

        cat.setOwner(player);

        cat.setTamed(true);

        /*
         * 猫参与战斗：可受伤，但不会死亡。
         * 受伤减免与致死保护由 CatEntityListener 处理。
         */
        cat.setInvulnerable(false);

        /*
         * 0.6.2：最大生命按等级补刷
         * （离线升级 / 老存档在下次绑定或恢复时同步）。
         */
        Cat logical =
                cache.getCat(
                        player.getUniqueId()
                );

        if (logical != null) {

            /*
             * 0.8.1：最大生命统一走 Cat#entityMaxHealth，
             * 等级与装备加成永不漂移。
             */
            double scaled =
                    logical.entityMaxHealth();

            org.bukkit.attribute.AttributeInstance attribute =
                    runtime.maxHealthAttribute(
                            cat
                    );

            if (attribute != null &&
                    Math.abs(
                            attribute.getBaseValue()
                                    - scaled
                    ) > 0.01) {

                attribute.setBaseValue(
                        scaled
                );

                if (cat.getHealth() > scaled) {

                    cat.setHealth(
                            scaled
                    );
                }
            }
        }

        cat.getPersistentDataContainer()
                .set(
                        catKey,
                        PersistentDataType.BYTE,
                        (byte) 1
                );

        cat.getPersistentDataContainer()
                .set(
                        ownerKey,
                        PersistentDataType.STRING,
                        player.getUniqueId().toString()
                );

        /*
         * 0.8.3：登记实体索引（所有绑定路径都经 updateCat）。
         */
        entityIndex.put(
                cat.getUniqueId(),
                player.getUniqueId()
        );
    }

    /*
     * ============================================================
     * 保存位置
     * ============================================================
     */

    void saveCatLocation(
            Player player,
            org.bukkit.entity.Cat entity
    ) {

        Location location =
                entity.getLocation();

        if (location.getWorld() == null) {
            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        store.setCatLocation(
                playerUUID,
                location.getWorld().getUID(),
                location.getX(),
                location.getY(),
                location.getZ()
        );

        Cat logicalCat =
                cache.getCat(playerUUID);

        if (logicalCat == null) {
            return;
        }

        syncLogicalCatLocation(
                logicalCat,
                entity
        );
    }

    /*
     * ============================================================
     * 更新名称
     * ============================================================
     */

    public void updateCatName(
            Player player,
            String name
    ) {

        if (player == null ||
                name == null ||
                name.isBlank()) {

            return;
        }

        String safeName =
                name.replace("§", "");

        UUID playerUUID =
                player.getUniqueId();

        Cat logicalCat =
                cache.loadCat(player);

        if (logicalCat != null) {

            logicalCat.setName(safeName);
        }

        store.setCatName(
                playerUUID,
                safeName
        );

        UUID entityUUID =
                store.getCatEntityUUID(playerUUID);

        if (entityUUID == null) {
            return;
        }

        Entity entity =
                runtime.getEntity(entityUUID);

        if (!(entity instanceof org.bukkit.entity.Cat cat)) {
            return;
        }

        if (cat.isDead() || !cat.isValid()) {
            return;
        }

        refreshCustomName(
                cat,
                logicalCat
        );
    }

    /*
     * ============================================================
     * 切换行为模式
     * ============================================================
     */

    public void setCatBehaviorMode(
            Player player,
            CatBehaviorMode mode
    ) {

        if (player == null || mode == null) {
            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 防止猫被删除后通过 GUI 按钮触发 ensureCat 重建。
         */
        if (!store.hasCat(playerUUID)) {
            return;
        }

        Cat logicalCat =
                cache.loadCat(player);

        if (logicalCat == null) {
            return;
        }

        logicalCat.setBehaviorMode(mode);

        store.setCatBehaviorMode(
                playerUUID,
                mode.name()
        );

        /*
         * 立即应用到实体。
         */
        UUID entityUuid =
                logicalCat.getEntityUuid();

        if (entityUuid == null) {
            return;
        }

        Entity entity =
                runtime.getEntity(entityUuid);

        if (!(entity instanceof org.bukkit.entity.Cat cat) ||
                cat.isDead() ||
                !cat.isValid()) {

            return;
        }

        if (mode == CatBehaviorMode.SIT) {

            cat.setSitting(true);

        } else {

            cat.setSitting(false);
        }
    }

    /*
     * ============================================================
     * 刷新头顶名称
     * ============================================================
     *
     * 受伤恢复期内显示倒计时悬浮字。
     */

    public void refreshCustomName(
            org.bukkit.entity.Cat entity,
            Cat logicalCat
    ) {

        if (entity == null ||
                !entity.isValid() ||
                logicalCat == null) {

            return;
        }

        String safeName =
                logicalCat.getName()
                        .replace("§", "");

        Player owner =
                Bukkit.getPlayer(
                        logicalCat.getOwnerUuid()
                );

        /*
         * 受伤恢复期：悬浮字显示倒计时。
         */
        if (battleState.isRecovering(
                entity.getUniqueId()
        )) {

            int seconds =
                    battleState.getRecoveryRemainingSeconds(
                            entity.getUniqueId()
                    );

            entity.setCustomName(
                    lang.forPlayer(owner).text(
                            "entity.name-recovering",
                            safeName,
                            String.valueOf(
                                    seconds
                            )
                    )
            );

        } else {

            entity.setCustomName(
                    lang.forPlayer(owner).text(
                            "entity.name-normal",
                            safeName,
                            logicalCat.getMood().getHeadIcon()
                    )
            );
        }

        entity.setCustomNameVisible(true);
    }

    /*
     * ============================================================
     * 同步位置
     * ============================================================
     */

    void syncLogicalCatLocation(
            Cat logicalCat,
            org.bukkit.entity.Cat entity
    ) {

        Location location =
                entity.getLocation();

        if (location.getWorld() == null) {
            return;
        }

        logicalCat.setWorldName(
                location.getWorld().getName()
        );

        logicalCat.setX(location.getX());
        logicalCat.setY(location.getY());
        logicalCat.setZ(location.getZ());
        logicalCat.setYaw(location.getYaw());
        logicalCat.setPitch(location.getPitch());

        logicalCat.setEntityUuid(
                entity.getUniqueId()
        );
    }
}
