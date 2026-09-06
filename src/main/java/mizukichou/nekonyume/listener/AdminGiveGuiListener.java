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

        /*
         * 仅处理面板顶部槽位。
         * 底部（玩家背包）点击不属于发放面板交互啦，
         * 既不取消也不进业务层啦——否则管理员可点击
         * 自己背包里的任意物品无限复制。
         */
        int rawSlot =
                event.getRawSlot();

        if (rawSlot < 0 ||
                rawSlot >=
                        event.getView()
                                .getTopInventory()
                                .getSize()) {

            /*
             * 0.9.0更新：底部点击同样取消——
             * 否则 shift 点击背包物品会把它塞进面板空槽，
             * 再点击该槽即 clone 复制任意物品。
             */
            event.setCancelled(
                    true
            );

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

