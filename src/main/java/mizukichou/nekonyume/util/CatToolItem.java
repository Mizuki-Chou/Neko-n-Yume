package mizukichou.nekonyume.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 逗猫棒物品工厂。
 *
 * <p>
 * 统一入口：命令发放与工作台合成共用，
 * 保证 PDC 标记与外观完全一致。
 * </p>
 */
public final class CatToolItem {

    private CatToolItem() {
    }

    public static ItemStack create(
            NamespacedKey toolKey
    ) {

        ItemStack tool =
                new ItemStack(
                        Material.STICK,
                        1
                );

        ItemMeta meta =
                tool.getItemMeta();

        if (meta != null) {

            meta.setDisplayName(
                    "§d🐱 逗猫棒"
            );

            meta.setLore(
                    List.of(
                            "§7右键打开猫咪面板"
                    )
            );

            meta.getPersistentDataContainer()
                    .set(
                            toolKey,
                            PersistentDataType.BYTE,
                            (byte) 1
                    );

            tool.setItemMeta(
                    meta
            );
        }

        return tool;
    }
}