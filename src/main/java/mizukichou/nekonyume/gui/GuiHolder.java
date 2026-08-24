package mizukichou.nekonyume.gui;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
public class GuiHolder implements InventoryHolder {

    private final Page page;
    private final UUID ownerUuid;

    public GuiHolder(
            Page page,
            UUID playerUuid
    ) {

        this.page = page;
        this.ownerUuid = playerUuid;
    }

    @Override
    public @Nullable Inventory getInventory() {
        return null;
    }
}
