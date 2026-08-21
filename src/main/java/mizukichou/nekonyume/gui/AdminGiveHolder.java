package mizukichou.nekonyume.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/*
 * /nekonyumeadmin give 管理发放面板的持有者。
 */
public final class AdminGiveHolder implements InventoryHolder {

    private final UUID ownerUuid;

    public AdminGiveHolder(UUID ownerUuid) {

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
