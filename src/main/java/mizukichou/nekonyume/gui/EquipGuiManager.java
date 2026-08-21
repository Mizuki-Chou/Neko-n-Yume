package mizukichou.nekonyume.gui;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.cat.CatEquipItem;
import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.EquipBonusAttribute;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 装备界面（0.8.0）。
 *
 * <p>
 * 布局（27 格）：
 * </p>
 * <ul>
 * <li>4：当前装备（含完整属性与"已装备"标记；未装备时显示空态）；</li>
 * <li>22：卸下当前装备（归还物品）；</li>
 * <li>26：关闭。</li>
 * </ul>
 *
 * <p>
 * 面板仅主人可操作；卸下后装备物品归还背包（满则掉落脚边），
 * 并即时重算实体最大生命。
 * </p>
 */
public final class EquipGuiManager {

    private static final int INVENTORY_SIZE = 27;

    private static final int SLOT_CURRENT = 4;

    private static final int SLOT_UNEQUIP = 22;

    private static final int SLOT_CLOSE = 26;

    private final CatStore store;

    private final CatCache cache;

    private final CatFoodManager foodManager;

    private final CatEntityService entityService;

    private final Lang lang;

    public EquipGuiManager(
            CatStore store,
            CatCache cache,
            CatFoodManager foodManager,
            CatEntityService entityService,
            Lang lang
    ) {

        this.store = store;
        this.cache = cache;
        this.foodManager = foodManager;
        this.entityService = entityService;
        this.lang = lang;
    }

    /*
     * ============================================================
     * 打开面板
     * ============================================================
     */

    public void open(Player player) {

        if (player == null) {
            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        if (!store.hasCat(playerUUID)) {
            return;
        }

        Cat cat =
                cache.loadCat(player);

        if (cat == null) {
            return;
        }

        Inventory inventory =
                Bukkit.createInventory(
                        new EquipGuiHolder(playerUUID),
                        INVENTORY_SIZE,
                        lang.forPlayer(player).text(
                                "equip-gui.title"
                        )
                );

        ItemStack filler =
                new ItemStack(
                        Material.GRAY_STAINED_GLASS_PANE
                );

        ItemMeta fillerMeta =
                filler.getItemMeta();

        fillerMeta.setDisplayName(
                "§0"
        );

        filler.setItemMeta(
                fillerMeta
        );

        for (int i = 0;
             i < INVENTORY_SIZE;
             i++) {

            inventory.setItem(
                    i,
                    filler
            );
        }

        /*
         * 当前装备。
         */
        CatEquipItem equipped =
                cat.getEquippedItem();

        if (equipped == null) {

            inventory.setItem(
                    SLOT_CURRENT,
                    named(
                            Material.PAPER,
                            lang.forPlayer(player).text(
                                    "equip-gui.none"
                            )
                    )
            );

        } else {

            inventory.setItem(
                    SLOT_CURRENT,
                    buildDisplayItem(
                            player,
                            equipped,
                            true,
                            cat.getEquippedBonus()
                    )
            );
        }

        /*
         * 卸下按钮（未装备时置灰）。
         */
        if (equipped != null) {

            inventory.setItem(
                    SLOT_UNEQUIP,
                    named(
                            Material.BARRIER,
                            lang.forPlayer(player).text(
                                    "equip-gui.unequip"
                            )
                    )
            );
        }

        /*
         * 关闭按钮。
         */
        inventory.setItem(
                SLOT_CLOSE,
                named(
                        Material.RED_STAINED_GLASS_PANE,
                        lang.forPlayer(player).text(
                                "equip-gui.close"
                        )
                )
        );

        player.openInventory(
                inventory
        );
    }

    /*
     * ============================================================
     * 面板点击
     * ============================================================
     */

    public boolean handleClick(
            Player player,
            int slot
    ) {

        if (slot == SLOT_CLOSE) {

            player.closeInventory();

            /*
             * 关闭不重开面板。
             */
            return false;
        }

        if (slot == SLOT_UNEQUIP) {

            /*
             * 卸下成功后返回 true，由监听器延迟重开面板。
             */
            return unequip(player);
        }

        return false;
    }

    /*
     * ============================================================
     * 卸下装备
     * ============================================================
     */

    public boolean unequip(Player player) {

        if (player == null) {
            return false;
        }

        UUID playerUUID =
                player.getUniqueId();

        Cat cat =
                cache.loadCat(player);

        if (cat == null) {
            return false;
        }

        CatEquipItem old =
                cat.getEquippedItem();

        if (old == null) {
            return false;
        }

        EquipBonusAttribute oldBonus =
                cat.getEquippedBonus();

        cat.setEquippedItem(
                null
        );

        cat.setEquippedBonus(
                null
        );

        store.setCatEquipment(
                playerUUID,
                ""
        );

        store.setCatEquipmentBonus(
                playerUUID,
                ""
        );

        /*
         * 归还装备（含附加属性；背包满则掉落脚边）。
         */
        ItemStack returned =
                foodManager.createEquippedReturn(
                        old,
                        oldBonus,
                        player
                );

        for (ItemStack leftover :
                player.getInventory()
                        .addItem(returned)
                        .values()) {

            player.getWorld().dropItemNaturally(
                    player.getLocation(),
                    leftover
            );
        }

        /*
         * 实体在场时重算最大生命。
         */
        if (cat.getEntityUuid() != null) {

            org.bukkit.entity.Cat entity =
                    (org.bukkit.entity.Cat) Bukkit.getEntity(
                            cat.getEntityUuid()
                    );

            if (entity != null &&
                    entity.isValid()) {

                entityService.refreshEquipStats(
                        player,
                        cat,
                        entity
                );
            }
        }

        player.sendMessage(
                lang.forPlayer(player).message(
                        "equip.unequipped",
                        lang.forPlayer(player).text(
                                old.getLangKey()
                        )
                )
        );

        return true;
    }

    /*
     * ============================================================
     * 背包快捷穿戴（0.8.0）
     * ============================================================
     *
     * 装备位为空时，点击背包中的装备直接穿上；
     * 已有装备时提示先卸下（返回 false，点击保持拦截）。
     * 返回 true 表示已完成穿戴，调用方负责刷新面板与槽位。
     */

    public boolean equipFromInventory(
            Player player,
            ItemStack clicked
    ) {

        if (player == null ||
                clicked == null ||
                clicked.getType().isAir()) {

            return false;
        }

        CatEquipItem equip =
                foodManager.getEquipment(
                        clicked
                );

        if (equip == null) {
            return false;
        }

        UUID playerUUID =
                player.getUniqueId();

        Cat cat =
                cache.loadCat(
                        player
                );

        if (cat == null) {
            return false;
        }

        if (cat.getEquippedItem() != null) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "equip-gui.busy"
                    )
            );

            return false;
        }

        if (!foodManager.equipFromStack(
                player,
                cat,
                equip,
                clicked
        )) {

            return false;
        }

        /*
         * 实体在场时重算最大生命。
         */
        if (cat.getEntityUuid() != null) {

            org.bukkit.entity.Cat entity =
                    (org.bukkit.entity.Cat) Bukkit.getEntity(
                            cat.getEntityUuid()
                    );

            if (entity != null &&
                    entity.isValid()) {

                entityService.refreshEquipStats(
                        player,
                        cat,
                        entity
                );
            }
        }

        return true;
    }

    /*
     * ============================================================
     * 展示物品
     * ============================================================
     */

    private ItemStack buildDisplayItem(
            Player player,
            CatEquipItem equip,
            boolean isEquipped,
            EquipBonusAttribute bonus
    ) {

        ItemStack display =
                foodManager.createEquipment(
                        equip,
                        1,
                        player
                );

        /*
         * 附加属性（0.8.0）：展示炫彩色属性行。
         */
        if (bonus != null) {

            foodManager.applyBonusAttribute(
                    display,
                    bonus,
                    player
            );
        }

        ItemMeta meta =
                display.getItemMeta();

        List<String> lore =
                meta.getLore() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(
                                meta.getLore()
                        );

        if (isEquipped) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-gui.equipped-mark"
                    )
            );
        }

        meta.setLore(
                lore
        );

        display.setItemMeta(
                meta
        );

        return display;
    }

    private ItemStack named(
            Material material,
            String name
    ) {

        ItemStack item =
                new ItemStack(
                        material
                );

        ItemMeta meta =
                item.getItemMeta();

        meta.setDisplayName(
                name
        );

        item.setItemMeta(
                meta
        );

        return item;
    }
}
