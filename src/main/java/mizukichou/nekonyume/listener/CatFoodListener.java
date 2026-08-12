package mizukichou.nekonyume.listener;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import mizukichou.nekonyume.NekoNYume;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class CatFoodListener implements Listener {

    private static final int MAX_HUNGER = 100;

    /*
     * 原版营养值 × 5 = 猫咪饱食度
     *
     * 苹果 4 → +20
     * 熟牛肉 8 → +40
     * 金胡萝卜 6 → +30
     */
    private static final int NUTRITION_MULTIPLIER = 5;

    private final NekoNYume plugin;

    public CatFoodListener(NekoNYume plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCatFeed(PlayerInteractAtEntityEvent event) {

        if (!(event.getRightClicked() instanceof Cat cat)) {
            return;
        }

        Player player = event.getPlayer();

        /*
         * 只处理 Neko n' Yume 的猫
         */
        if (!cat.getPersistentDataContainer().has(
                plugin.getCatManager().getCatKey(),
                PersistentDataType.BYTE
        )) {
            return;
        }

        /*
         * 只允许主人喂食
         */
        String ownerUUID =
                cat.getPersistentDataContainer().get(
                        plugin.getCatManager().getOwnerKey(),
                        PersistentDataType.STRING
                );

        if (ownerUUID == null ||
                !ownerUUID.equals(
                        player.getUniqueId().toString()
                )) {
            return;
        }

        /*
         * 获取玩家使用的手
         */
        EquipmentSlot hand = event.getHand();

        ItemStack item =
                player.getInventory().getItem(hand);

        if (item == null || item.getType().isAir()) {
            return;
        }

        /*
         * 获取原版 Food Component
         */
        FoodProperties food =
                item.getData(DataComponentTypes.FOOD);

        /*
         * 不是食物
         */
        if (food == null) {
            return;
        }

        int currentHunger =
                plugin.getDataManager()
                        .getCatHunger(player.getUniqueId());

        /*
         * 已经吃饱
         */
        if (currentHunger >= MAX_HUNGER) {

            event.setCancelled(true);

            player.sendMessage(
                    "§e🐱 你的猫咪已经吃饱啦！"
            );

            return;
        }

        /*
         * 原版食物营养值
         */
        int nutrition =
                food.nutrition();

        /*
         * 转换成我们的饱食度
         */
        int hungerGain =
                nutrition * NUTRITION_MULTIPLIER;

        int newHunger =
                Math.min(
                        MAX_HUNGER,
                        currentHunger + hungerGain
                );

        int actualGain =
                newHunger - currentHunger;

        /*
         * 阻止 Minecraft 原本对猫的处理
         */
        event.setCancelled(true);

        /*
         * 保存饱食度
         */
        plugin.getDataManager()
                .setCatHunger(
                        player.getUniqueId(),
                        newHunger
                );

        /*
         * 创造模式不消耗食物
         */
        if (player.getGameMode() != GameMode.CREATIVE) {

            if (item.getAmount() <= 1) {

                player.getInventory()
                        .setItem(hand, null);

            } else {

                item.setAmount(
                        item.getAmount() - 1
                );
            }
        }

        /*
         * 播放吃东西音效
         */
        player.getWorld().playSound(
                cat.getLocation(),
                Sound.ENTITY_CAT_EAT,
                1.0f,
                1.0f
        );

        String name =
                plugin.getDataManager()
                        .getCatName(
                                player.getUniqueId()
                        );

        player.sendMessage(
                "§d🐱 " + name
                        + " §a恢复了 "
                        + actualGain
                        + " 点饱食度 §7("
                        + newHunger
                        + "/"
                        + MAX_HUNGER
                        + ")"
        );
    }
}