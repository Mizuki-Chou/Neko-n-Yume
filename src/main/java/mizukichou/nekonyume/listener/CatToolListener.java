package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.gui.CatGuiManager;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 快捷工具（逗猫棒）监听器。
 *
 * <p>
 * 持有 /nekoyume tool 发放的逗猫棒：
 * 右键空气 / 方块 / 任意实体 → 打开猫咪面板。
 * </p>
 */
public class CatToolListener implements Listener {

    private final CatGuiManager guiManager;

    private final NamespacedKey toolKey;

    public CatToolListener(
            CatGuiManager guiManager,
            NamespacedKey toolKey
    ) {

        this.guiManager = guiManager;
        this.toolKey = toolKey;
    }

    @EventHandler
    public void onInteract(
            PlayerInteractEvent event
    ) {

        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {

            return;
        }

        if (!isTool(
                event.getItem()
        )) {

            return;
        }

        event.setCancelled(true);

        openPanel(
                event.getPlayer()
        );
    }

    @EventHandler
    public void onInteractEntity(
            PlayerInteractAtEntityEvent event
    ) {

        ItemStack item =
                event.getPlayer()
                        .getInventory()
                        .getItemInMainHand();

        if (!isTool(item)) {
            return;
        }

        event.setCancelled(true);

        openPanel(
                event.getPlayer()
        );
    }

    private boolean isTool(
            ItemStack item
    ) {

        if (item == null ||
                !item.hasItemMeta()) {

            return false;
        }

        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(
                        toolKey,
                        PersistentDataType.BYTE
                );
    }

    private void openPanel(
            Player player
    ) {

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_CAT_AMBIENT,
                1.0f,
                1.4f
        );

        guiManager.open(
                player
        );
    }
}
