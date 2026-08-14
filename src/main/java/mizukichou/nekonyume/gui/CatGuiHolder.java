package mizukichou.nekonyume.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * 猫咪面板的持有者。
 *
 * <p>
 * 通过 ownerUuid 绑定主人，
 * 只有主人可以操作面板按钮。
 * </p>
 */
public final class CatGuiHolder implements InventoryHolder {

    private final UUID ownerUuid;

    public CatGuiHolder(
            UUID ownerUuid
    ) {

        this.ownerUuid = ownerUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}