package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.NekoNYume;
import org.bukkit.Sound;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class CatFoodListener implements Listener {

    private final NekoNYume plugin;

    public CatFoodListener(
            NekoNYume plugin
    ) {

        this.plugin = plugin;
    }

    @EventHandler
    public void onCatFeed(
            PlayerInteractAtEntityEvent event
    ) {

        /*
         * ============================================================
         * 1. 只处理 Bukkit 猫
         * ============================================================
         */

        if (!(event.getRightClicked()
                instanceof Cat cat)) {

            return;
        }

        Player player =
                event.getPlayer();

        /*
         * ============================================================
         * 2. 只处理 Neko n' Yume 的猫
         * ============================================================
         */

        if (!cat.getPersistentDataContainer()
                .has(
                        plugin.getCatManager()
                                .getCatKey(),
                        PersistentDataType.BYTE
                )) {

            return;
        }

        /*
         * ============================================================
         * 3. 只允许主人喂食
         * ============================================================
         */

        String ownerUUID =
                cat.getPersistentDataContainer()
                        .get(
                                plugin.getCatManager()
                                        .getOwnerKey(),
                                PersistentDataType.STRING
                        );

        if (ownerUUID == null ||
                !ownerUUID.equals(
                        player.getUniqueId()
                                .toString()
                )) {

            return;
        }

        /*
         * ============================================================
         * 4. 获取使用的手
         * ============================================================
         */

        EquipmentSlot hand =
                event.getHand();

        if (hand == null) {
            return;
        }

        ItemStack item =
                player.getInventory()
                        .getItem(hand);

        if (item == null ||
                item.getType().isAir()) {

            return;
        }

        /*
         * ============================================================
         * 5a. 喵丹优先判定
         * ============================================================
         *
         * 喵丹不算普通食物，
         * 走独立的使用逻辑。
         *
         * 伪造成食物模样的假喵丹
         * 没有 PDC 标记，无法通过判定。
         */

        if (plugin.getCatFoodManager()
                .isMeowDan(item)) {

            /*
             * 阻止原版右键默认行为。
             */
            event.setCancelled(true);

            boolean used =
                    plugin.getCatFoodManager()
                            .feedMeowDan(
                                    player,
                                    item
                            );

            if (used) {

                player.getWorld()
                        .playSound(
                                cat.getLocation(),
                                Sound.ENTITY_CAT_EAT,
                                1.0f,
                                1.0f
                        );
            }

            return;
        }

        /*
         * ============================================================
         * 5b. 交给 CatFoodManager 判断和处理
         * ============================================================
         *
         * 不再在 Listener 里自己操作 hunger / affection。
         */

        if (!plugin.getCatFoodManager()
                .isFood(item)) {

            return;
        }

        /*
         * 阻止 Minecraft 对这次右键猫咪的默认处理。
         *
         * 注意：
         * 即使猫咪已经吃饱（feedCat 返回 false），
         * 也必须取消事件。
         * 否则玩家手持鱼类右键驯服猫时，
         * 原版会尝试喂食并消耗物品，
         * 与"已经吃饱"提示相互矛盾。
         */
        event.setCancelled(true);

        boolean success =
                plugin.getCatFoodManager()
                        .feedCat(
                                player,
                                item
                        );

        /*
         * ============================================================
         * 6. 喂食成功 → 播放音效
         * ============================================================
         *
         * 成功即播放。
         * 创造模式不消耗物品，但同样播放音效。
         */

        if (success) {

            player.getWorld()
                    .playSound(
                            cat.getLocation(),
                            Sound.ENTITY_CAT_EAT,
                            1.0f,
                            1.0f
                    );
        }
    }
}