package mizukichou.nekonyume.skill;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * 技能面板持有者。
 *
 * <p>
 * 通过 ownerUuid 绑定主人，
 * 只有主人可以操作面板。
 * </p>
 */
public final class SkillGuiHolder implements InventoryHolder {

    private final UUID ownerUuid;

    public SkillGuiHolder(
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
