package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.cat.CatEquipItem;
import mizukichou.nekonyume.cat.CatEquipType;
import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.EquipBagOdds;
import mizukichou.nekonyume.cat.EquipBonusAttribute;
import mizukichou.nekonyume.cat.MeowDanQuality;
import mizukichou.nekonyume.lang.Lang;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;

/**
 * 猫猫装备袋（0.8.0 梦魔之夜）监听：
 * 右键开启 → 按品质权重（40/30/20/7.5/2.5）抽取一件装备。
 *
 * <p>
 * 抽取走 {@link CatFoodManager#grantEquipment} 获取途径专用发放：
 * 至极品质同样享受“获得的一瞬间”觉醒 roll（与发放面板口径一致）。
 * </p>
 */
public class EquipBagListener implements Listener {

    private final CatFoodManager foodManager;
    private final Lang lang;

    private final LegacyComponentSerializer legacySerializer =
            LegacyComponentSerializer.legacySection();

    private final Random random =
            new Random();

    public EquipBagListener(
            CatFoodManager foodManager,
            Lang lang
    ) {

        this.foodManager = foodManager;
        this.lang = lang;
    }

    @EventHandler(
            priority = EventPriority.NORMAL,
            ignoreCancelled = true
    )
    public void onOpenBag(
            PlayerInteractEvent event
    ) {

        Action action =
                event.getAction();

        if (action != Action.RIGHT_CLICK_AIR &&
                action != Action.RIGHT_CLICK_BLOCK) {

            return;
        }

        /*
         * 只处理主手，避免双持时打开两次。
         */
        if (event.getHand()
                != EquipmentSlot.HAND) {

            return;
        }

        ItemStack item =
                event.getItem();

        if (!foodManager.isEquipBag(
                item
        )) {

            return;
        }

        event.setCancelled(
                true
        );

        Player player =
                event.getPlayer();

        /*
         * 抽取：品质按权重、类型均匀。
         */
        MeowDanQuality quality =
                EquipBagOdds.pickQuality(
                        random
                );

        CatEquipType type =
                EquipBagOdds.pickType(
                        random
                );

        if (quality == null ||
                type == null) {

            return;
        }

        CatEquipItem equip =
                CatEquipItem.of(
                        type,
                        quality
                );

        if (equip == null) {
            return;
        }

        /*
         * 消耗袋子（创造模式不消耗，与喵丹/经验丸口径一致）。
         *
         * 注意：必须重新从背包取出手持物品再修改——
         * PlayerInteractEvent#getItem() 在 Paper 1.21 不保证是
         * 背包槽位的活引用，直接改它可能吞不掉袋子
         * （与 equipCat 的消耗模式保持一致）。
         */
        if (player.getGameMode()
                != GameMode.CREATIVE) {

            ItemStack hand =
                    player.getInventory()
                            .getItemInMainHand();

            if (hand != null &&
                    !hand.getType().isAir() &&
                    foodManager.isEquipBag(
                            hand
                    )) {

                hand.setAmount(
                        hand.getAmount() <= 1
                                ? 0
                                : hand.getAmount() - 1
                );
            }
        }

        /*
         * 获取途径专用发放：背包优先，满则掉落在脚边。
         */
        ItemStack given =
                foodManager.grantEquipment(
                        player,
                        equip
                );

        EquipBonusAttribute bonus =
                foodManager.getEquipmentBonus(
                        given
                );

        if (bonus != null) {

            foodManager.announceEquipBonus(
                    player,
                    bonus
            );
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
                                "equip-bag.open",
                                legacySerializer.deserialize(
                                        displayName(
                                                given
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
