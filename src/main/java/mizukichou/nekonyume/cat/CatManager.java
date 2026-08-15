package mizukichou.nekonyume.cat;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 猫咪系统门面（Step 5A：纯委托 + 注入构造）。
 *
 * <p>
 * 缓存/加载/保存 → CatCache；
 * 实体 → CatEntityService；
 * 成长/技能槽 → CatProgressionService。
 * </p>
 *
 * <p>
 * 内部组件已全部改为构造注入；
 * 本门面保留为对外 API 与自动保存编排入口，
 * 不再参与任何 plugin.getX() 定位。
 * </p>
 */
public class CatManager {

    private final CatCache catCache;
    private final CatEntityService catEntityService;
    private final CatProgressionService catProgressionService;

    public CatManager(
            CatCache catCache,
            CatEntityService catEntityService,
            CatProgressionService catProgressionService
    ) {

        this.catCache = catCache;
        this.catEntityService = catEntityService;
        this.catProgressionService = catProgressionService;
    }

    /*
     * ============================================================
     * 查询（CatCache）
     * ============================================================
     */

    public Cat getCat(UUID ownerUUID) {
        return catCache.getCat(ownerUUID);
    }

    public Cat getCat(Player player) {
        return catCache.getCat(player);
    }

    public Cat getCatById(UUID catUUID) {
        return catCache.getCatById(catUUID);
    }

    public Cat getCatByEntity(UUID entityUUID) {
        return catCache.getCatByEntity(entityUUID);
    }

    public List<Cat> getCats() {
        return catCache.getCats();
    }

    /*
     * ============================================================
     * 加载 / 保存（CatCache）
     * ============================================================
     */

    public Cat loadCat(Player player) {
        return catCache.loadCat(player);
    }

    public Cat loadCat(UUID ownerUUID) {
        return catCache.loadCat(ownerUUID);
    }

    public Cat reloadCat(Player player) {
        return catCache.reloadCat(player);
    }

    public void saveCat(Cat cat) {
        catCache.saveCat(cat);
    }

    /**
     * 保存当前内存中的全部猫咪，并驱逐离线缓存。
     *
     * <p>
     * 编排流程：
     * 1. 对每只猫先从实体捕获最新花色与位置；
     * 2. 回写玩家数据层；
     * 3. 驱逐"离线且长时间无互动"的缓存。
     * </p>
     */
    public void saveAllCats() {

        for (Cat cat : catCache.getCats()) {

            catEntityService.captureEntityState(cat);

            catCache.saveCat(cat);
        }

        catCache.evictOffline(
                System.currentTimeMillis()
        );
    }

    public void removeLogicalCat(UUID ownerUUID) {
        catCache.removeByOwner(ownerUUID);
    }

    public void clearLogicalCats() {
        catCache.clear();
    }

    /*
     * ============================================================
     * 成长 / 技能槽（CatProgressionService）
     * ============================================================
     */

    public void gainExperience(
            Player player,
            Cat cat,
            int amount
    ) {

        catProgressionService.gainExperience(
                player,
                cat,
                amount
        );
    }

    public void grantMeowPower(
            Player player,
            Cat cat,
            int amount
    ) {

        catProgressionService.grantMeowPower(
                player,
                cat,
                amount
        );
    }

    public void syncSkillSlots(
            Player player,
            Cat cat
    ) {

        catProgressionService.syncSkillSlots(
                player,
                cat
        );
    }

    public boolean refreshSkillSlot(
            Player player,
            int slotIndex
    ) {

        return catProgressionService.refreshSkillSlot(
                player,
                slotIndex
        );
    }

    public boolean grantSkill(
            Player player,
            CatSkill skill
    ) {

        return catProgressionService.grantSkill(
                player,
                skill
        );
    }

    /*
     * ============================================================
     * 实体（CatEntityService）
     * ============================================================
     */

    public void restoreCatEntity(Player player) {
        catEntityService.restoreCatEntity(player);
    }

    public void retryPendingWorldRestores(World world) {
        catEntityService.retryPendingWorldRestores(world);
    }

    public void clearPendingRestore(UUID playerUUID) {
        catEntityService.clearPendingRestore(playerUUID);
    }

    public void clearEntityBinding(UUID playerUUID) {
        catEntityService.clearEntityBinding(playerUUID);
    }

    public boolean removePlayerCat(UUID playerUUID) {
        return catEntityService.removePlayerCat(playerUUID);
    }

    public void spawnCat(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        catEntityService.spawnCat(
                player,
                name,
                callback
        );
    }

    public void updateCatName(
            Player player,
            String name
    ) {

        catEntityService.updateCatName(
                player,
                name
        );
    }

    public void setCatBehaviorMode(
            Player player,
            CatBehaviorMode mode
    ) {

        catEntityService.setCatBehaviorMode(
                player,
                mode
        );
    }

    public void refreshCustomName(
            org.bukkit.entity.Cat entity,
            Cat logicalCat
    ) {

        catEntityService.refreshCustomName(
                entity,
                logicalCat
        );
    }

    public NamespacedKey getCatKey() {
        return catEntityService.getCatKey();
    }

    public NamespacedKey getOwnerKey() {
        return catEntityService.getOwnerKey();
    }
}
