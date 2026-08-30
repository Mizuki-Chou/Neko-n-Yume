package mizukichou.nekonyume.util;

import mizukichou.nekonyume.lang.Lang;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
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
 * 0.7.0：物品名与 lore 改走 Lang（tool.wand-name / tool.wand-lore）。
 * </p>
 */
public final class CatToolItem {

    private CatToolItem() {
    }

    public static ItemStack create(
            NamespacedKey toolKey,
            Lang lang,
            Player player
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
                    lang.forPlayer(player).text(
                            "tool.wand-name"
                    )
            );

            meta.setLore(
                    List.of(
                            lang.forPlayer(player).text(
                                    "tool.wand-lore"
                            )
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
