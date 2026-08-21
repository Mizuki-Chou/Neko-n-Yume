package mizukichou.nekonyume.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * 装备界面持有者。
 *
 * <p>
 * 仅记录面板主人：点击处理时据此做越权校验，
 * 与其余三块面板（状态 / 技能 / 成就）同款模式。
 * </p>
 */
public final class EquipGuiHolder implements InventoryHolder {

    private final UUID ownerUuid;

    public EquipGuiHolder(UUID ownerUuid) {
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
