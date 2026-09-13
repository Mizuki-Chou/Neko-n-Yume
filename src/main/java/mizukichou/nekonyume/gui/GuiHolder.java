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

    /**
     * 0.9.0更新：InventoryHolder 契约——
     * getInventory 不得返回 null（保护类插件调用即 NPE）。
     * 由 GUI 管理器在 createInventory 后 bind 啦。
     */
    private volatile Inventory inventory;

    public void bind(
            Inventory inventory
    ) {

        this.inventory =
                inventory;
    }

    @Override
    public @Nullable Inventory getInventory() {

        return inventory;
    }
}

