package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.NekoNYume;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.gui.CatGuiHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class CatGuiListener implements Listener {

    private final NekoNYume plugin;

    public CatGuiListener(
            NekoNYume plugin
    ) {

        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(
            InventoryClickEvent event
    ) {

        if (!(event.getInventory()
                .getHolder()
                instanceof CatGuiHolder holder)) {

            return;
        }

        /*
         * 面板内所有点击一律取消，
         * 物品不可被取出 / 移动。
         */
        event.setCancelled(true);

        /*
         * 只有面板主人能操作。
         */
        if (!event.getWhoClicked()
                .getUniqueId()
                .equals(holder.getOwnerUuid())) {

            return;
        }

        Player player =
                (Player) event.getWhoClicked();

        ItemStack clicked =
                event.getCurrentItem();

        if (clicked == null ||
                clicked.getType().isAir()) {

            return;
        }

        int slot =
                event.getRawSlot();

        CatBehaviorMode mode;

        switch (slot) {

            case 18 ->
                    mode = CatBehaviorMode.FOLLOW;

            case 19 ->
                    mode = CatBehaviorMode.SIT;

            case 20 ->
                    mode = CatBehaviorMode.FREE;

            case 26 -> {

                player.closeInventory();
                return;
            }

            default -> {
                return;
            }
        }

        /*
         * 切换模式并刷新面板。
         *
         * 延迟 1 tick 重开，
         * 避免在点击事件处理中直接操作背包。
         */
        plugin.getCatManager()
                .setCatBehaviorMode(
                        player,
                        mode
                );

        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> plugin.getCatGuiManager()
                                .open(
                                        player
                                )
                );
    }
}