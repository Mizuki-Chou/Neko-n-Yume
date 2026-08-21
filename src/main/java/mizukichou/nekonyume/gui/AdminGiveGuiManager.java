package mizukichou.nekonyume.gui;

import java.util.Map;

import mizukichou.nekonyume.cat.CatEquipItem;
import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.EquipBonusAttribute;
import mizukichou.nekonyume.cat.MeowDanQuality;
import mizukichou.nekonyume.cat.XpPillTier;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.util.CatToolItem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/*
 * /nekonyumeadmin give 管理发放面板（0.8.0）。
 *
 * 集中发放本插件全部特殊物品：
 * 逗猫棒、喵丹 ×5、经验丸 ×2、装备 ×25。
 *
 * 至极装备在发放瞬间有 20% 概率觉醒附加属性
 * （掷出后捡回不会重roll）。
 *
 * 点击即获得（背包优先，满则掉落脚边），面板保持打开可连续发放；
 * 右下角栅栏关闭。仅打开者本人可交互。
 */
public final class AdminGiveGuiManager {

    private final CatFoodManager foodManager;

    private final NamespacedKey toolKey;

    private final Lang lang;

    /*
     * 含 § 色码的物品名进聊天组件前必须转换。
     */
    private final LegacyComponentSerializer legacySerializer =
            LegacyComponentSerializer.legacySection();

    public AdminGiveGuiManager(
            CatFoodManager foodManager,
            NamespacedKey toolKey,
            Lang lang
    ) {

        this.foodManager = foodManager;
        this.toolKey = toolKey;
        this.lang = lang;
    }

    public void open(
            Player player
    ) {

        if (player == null ||
                !player.isOnline()) {

            return;
        }

        Inventory inventory =
                Bukkit.createInventory(
                        new AdminGiveHolder(
                                player.getUniqueId()
                        ),
                        54,
                        lang.forPlayer(player).text(
                                "give-gui.title"
                        )
                );

        int slot = 0;

        /*
         * 逗猫棒。
         */
        inventory.setItem(
                slot++,
                CatToolItem.create(
                        toolKey,
                        lang,
                        player
                )
        );

        /*
         * 喵丹（全品质）。
         */
        for (MeowDanQuality quality :
                MeowDanQuality.values()) {

            inventory.setItem(
                    slot++,
                    foodManager.createMeowDan(
                            quality,
                            1,
                            player
                    )
            );
        }

        /*
         * 经验丸。
         */
        inventory.setItem(
                slot++,
                foodManager.createXpPill(
                        XpPillTier.NORMAL,
                        1,
                        player
                )
        );

        inventory.setItem(
                slot++,
                foodManager.createXpPill(
                        XpPillTier.ELITE,
                        1,
                        player
                )
        );

        /*
         * 装备（五型 × 五品质 = 25 件）。
         */
        for (CatEquipItem equip :
                CatEquipItem.values()) {

            inventory.setItem(
                    slot++,
                    foodManager.createEquipment(
                            equip,
                            1,
                            player
                    )
            );
        }

        /*
         * 猫猫装备袋（0.8.0 梦魔之夜掉落物）。
         */
        inventory.setItem(
                slot++,
                foodManager.createEquipBag(
                        1,
                        player
                )
        );

        /*
         * 关闭按钮。
         */
        ItemStack close =
                new ItemStack(
                        Material.BARRIER,
                        1
                );

        ItemMeta closeMeta =
                close.getItemMeta();

        if (closeMeta != null) {

            closeMeta.setDisplayName(
                    lang.forPlayer(player).text(
                            "give-gui.close"
                    )
            );

            close.setItemMeta(
                    closeMeta
            );
        }

        inventory.setItem(
                53,
                close
        );

        player.openInventory(
                inventory
        );
    }

    /*
     * 点击处理（事件已在监听器取消；此处只做发放/关闭）。
     */
    public void handleClick(
            Player player,
            InventoryClickEvent event
    ) {

        if (player == null ||
                event == null) {

            return;
        }

        ItemStack clicked =
                event.getCurrentItem();

        if (clicked == null ||
                clicked.getType().isAir()) {

            return;
        }

        if (clicked.getType() == Material.BARRIER) {

            player.closeInventory();

            return;
        }

        /*
         * 装备（0.8.0）：走获取途径专用发放——
         * 至极品质在获得的瞬间有概率觉醒附加属性。
         * 其他物品维持原有 clone 发放。
         */
        CatEquipItem equip =
                foodManager.getEquipment(
                        clicked
                );

        ItemStack give;

        if (equip != null) {

            give =
                    foodManager.grantEquipment(
                            player,
                            equip
                    );

            EquipBonusAttribute bonus =
                    foodManager.getEquipmentBonus(
                            give
                    );

            if (bonus != null) {

                foodManager.announceEquipBonus(
                        player,
                        bonus
                );
            }

        } else {

            give =
                    clicked.clone();

            give.setAmount(
                    1
            );

            Map<Integer, ItemStack> left =
                    player.getInventory()
                            .addItem(
                                    give
                            );

            if (!left.isEmpty()) {

                for (ItemStack rest :
                        left.values()) {

                    player.getWorld()
                            .dropItemNaturally(
                                    player.getLocation(),
                                    rest
                            );
                }
            }
        }

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_ITEM_PICKUP,
                1.0f,
                1.0f
        );

        player.sendMessage(
                lang.forPlayer(player)
                        .messageComponents(
                                "give-gui.received",
                                legacySerializer.deserialize(
                                        displayName(
                                                give
                                        )
                                )
                        )
        );
    }

    private String displayName(
            ItemStack item
    ) {

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null &&
                meta.hasDisplayName()) {

            return meta.getDisplayName();
        }

        return item.getType()
                .name();
    }
}
