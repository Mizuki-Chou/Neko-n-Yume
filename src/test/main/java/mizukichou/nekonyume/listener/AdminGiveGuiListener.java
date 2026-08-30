package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.gui.AdminGiveGuiManager;

import mizukichou.nekonyume.gui.GuiHolder;
import mizukichou.nekonyume.gui.Page;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/*
 * /nekonyumeadmin give 管理发放面板点击监听（0.8.0）。
 *
 * 安全约束：仅打开者本人可交互；全部点击取消（物品不可移动/提取）。
 */
public class AdminGiveGuiListener implements Listener {

    private final AdminGiveGuiManager manager;

    public AdminGiveGuiListener(
            AdminGiveGuiManager manager
    ) {

        this.manager = manager;
    }

    @EventHandler
    public void onInventoryClick(
            InventoryClickEvent event
    ) {

        if (!(event.getView()
                .getTopInventory()
                .getHolder() instanceof GuiHolder holder
                && holder.getPage() == Page.ADMIN)) {

            return;
        }

        if (!(event.getWhoClicked()
                instanceof Player player)) {

            return;
        }

        event.setCancelled(
                true
        );

        if (!player.getUniqueId()
                .equals(
                        holder.getOwnerUuid()
                )) {

            return;
        }

        manager.handleClick(
                player,
                event
        );
    }

    /*
     * 拖拽同样全部取消（防止把面板物品拖出 / 把玩家物品拖入）。
     */
    @EventHandler
    public void onInventoryDrag(
            InventoryDragEvent event
    ) {

        if (!(event.getView()
                .getTopInventory()
                .getHolder() instanceof GuiHolder holder
                && holder.getPage() == Page.ADMIN)) {

            return;
        }

        /*
         * 只拦涉及面板顶部的拖拽；
         * 纯背包内拖拽整理保持原版体验。
         */
        int topSize =
                event.getView()
                        .getTopInventory()
                        .getSize();

        for (int rawSlot :
                event.getRawSlots()) {

            if (rawSlot < topSize) {

                event.setCancelled(
                        true
                );

                return;
            }
        }
    }
}
