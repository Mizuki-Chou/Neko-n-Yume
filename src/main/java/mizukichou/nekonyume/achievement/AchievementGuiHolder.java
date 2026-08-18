package mizukichou.nekonyume.achievement;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * 成就殿堂面板持有者。
 *
 * <p>
 * 通过 ownerUuid 绑定主人，
 * 只有主人可以操作面板。
 * </p>
 */
@Getter
public final class AchievementGuiHolder implements InventoryHolder {

    private final UUID ownerUuid;

    public AchievementGuiHolder(
            UUID ownerUuid
    ) {

        this.ownerUuid = ownerUuid;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
