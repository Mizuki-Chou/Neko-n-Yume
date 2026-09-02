package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.gui.CatDetailGuiManager;
import mizukichou.nekonyume.gui.GuiHolder;
import mizukichou.nekonyume.gui.Page;
import mizukichou.nekonyume.gui.RankingGuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * 0.8.5：排行 / 猫咪详情 / 删除确认三个面板的点击与拖拽守卫。
 *
 * <p>
 * 全部点击一律取消（只读或受控交互）；
 * 拖拽全拦截；身份以 GuiHolder 的 owner 校验。
 * </p>
 */
public final class RankingGuiListener implements Listener {

    private final RankingGuiManager rankingGuiManager;
    private final CatDetailGuiManager detailGuiManager;

    public RankingGuiListener(
        RankingGuiManager rankingGuiManager,
        CatDetailGuiManager detailGuiManager
    ) {
        this.rankingGuiManager = rankingGuiManager;
        this.detailGuiManager = detailGuiManager;
    }

    @EventHandler(
        priority = EventPriority.NORMAL,
        ignoreCancelled = true
    )
    public void onInventoryClick(
        InventoryClickEvent event
    ) {

        if (!(event.getView()
            .getTopInventory()
            .getHolder() instanceof GuiHolder holder)) {

            return;
        }

        Page page = holder.getPage();

        if (page != Page.RANKING
            && page != Page.CAT_DETAIL
            && page != Page.CAT_DELETE_CONFIRM) {

            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked()
            instanceof Player player)) {

            return;
        }

        if (!player.getUniqueId()
            .equals(
                holder.getOwnerUuid()
            )) {

            return;
        }

        int rawSlot = event.getRawSlot();

        if (rawSlot < 0
            || rawSlot
                >= event.getView()
                    .getTopInventory()
                    .getSize()) {

            return;
        }

        boolean leftClick =
            event.isLeftClick();

        switch (page) {

            case RANKING ->
                rankingGuiManager.handleClick(
                    player,
                    rawSlot,
                    leftClick
                );

            case CAT_DETAIL ->
                detailGuiManager.handleDetailClick(
                    player,
                    rawSlot
                );

            case CAT_DELETE_CONFIRM ->
                detailGuiManager.handleConfirmClick(
                    player,
                    rawSlot
                );

            default -> {
                /* 其他页面不在此处理 */
            }
        }
    }

    @EventHandler(
        priority = EventPriority.NORMAL,
        ignoreCancelled = true
    )
    public void onInventoryDrag(
        InventoryDragEvent event
    ) {

        Object holder =
            event.getView()
                .getTopInventory()
                .getHolder();

        if (!(holder instanceof GuiHolder guiHolder)) {
            return;
        }

        Page page = guiHolder.getPage();

        if (page == Page.RANKING
            || page == Page.CAT_DETAIL
            || page == Page.CAT_DELETE_CONFIRM) {

            event.setCancelled(true);
        }
    }
}
