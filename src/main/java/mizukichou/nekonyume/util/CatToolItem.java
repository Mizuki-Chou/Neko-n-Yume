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
 * 统一入口：命令发放与工作台合成共用啦，
 * 保证 PDC 标记与外观完全一致。
 * 0.7.0：物品名与 lore 改走 Lang（tool.wand-name / tool.wand-lore）。
 * </p>
 */
public final class CatToolItem {

    private CatToolItem() {
    }

    /*
     * 0.9.0更新：已有逗猫棒判定（/nekonyume
     * tool 不重复发放）捏。
     */
    public static boolean isTool(
            ItemStack item,
            NamespacedKey toolKey
    ) {

        if (item == null ||
                !item.hasItemMeta()) {

            return false;
        }

        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(
                        toolKey,
                        org.bukkit.persistence.PersistentDataType.BYTE
                );
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

