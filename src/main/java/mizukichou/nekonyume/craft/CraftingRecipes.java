package mizukichou.nekonyume.craft;

import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.MeowDanQuality;
import mizukichou.nekonyume.util.CatToolItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 合成配方注册器。
 *
 * <p>
 * 喵丹升级：9 × 低级喵丹 → 1 × 高级喵丹（平凡为最低级，不可合成）。
 * </p>
 *
 * <p>
 * Paper 26.2 的 ShapelessRecipe 不接受 RecipeChoice，
 * 因此配方按"9 × 金粒"注册；
 * 品质与批次的精确校验由 MeowDanCraftListener
 * 在 PrepareItemCraftEvent 中完成，
 * 结果物品也在合成预览时按当前批次现场生成。
 * </p>
 *
 * <p>
 * 逗猫棒：1 木棍 + 1 生鳕鱼 → 逗猫棒。
 * </p>
 */
public class CraftingRecipes {

    private final JavaPlugin plugin;
    private final CatFoodManager foodManager;
    private final NamespacedKey toolKey;

    /*
     * 已注册的配方键（用于重载时移除）。
     */
    private final List<NamespacedKey> registeredKeys =
            new ArrayList<>();

    public CraftingRecipes(
            JavaPlugin plugin,
            CatFoodManager foodManager,
            NamespacedKey toolKey
    ) {

        this.plugin = plugin;
        this.foodManager = foodManager;
        this.toolKey = toolKey;
    }

    public void registerAll() {

        unregisterAll();

        /*
         * ========================================================
         * 喵丹升级：9 × 低级 → 1 × 高级
         * ========================================================
         *
         * 平凡（values[0]）是基础材料，不可合成；
         * 从精良到至极每级一条配方。
         *
         * 注册结果只是占位：
         * 真实结果由 MeowDanCraftListener 校验后现场生成。
         */

        /*
         * 与枚举声明顺序无关的品质升序链：
         * i=0 → 平凡→精良，i=1 → 精良→独特，
         * i=2 → 独特→卓越，i=3 → 卓越→至极。
         */
        java.util.List<MeowDanQuality> qualities =
                CatFoodManager.orderedQualities();

        for (int i = 0;
             i < qualities.size() - 1;
             i++) {

            MeowDanQuality to =
                    qualities.get(i + 1);

            ItemStack placeholderResult =
                    foodManager.createMeowDan(
                            to,
                            1
                    );

            NamespacedKey key =
                    new NamespacedKey(
                            plugin,
                            "meowdan_upgrade_" + i
                    );

            ShapelessRecipe recipe =
                    new ShapelessRecipe(
                            key,
                            placeholderResult
                    );

            /*
             * 按材质注册：喵丹本体就是金粒。
             * 品质校验交给 PrepareItemCraftEvent。
             */
            recipe.addIngredient(
                    9,
                    Material.GOLD_NUGGET
            );

            if (Bukkit.addRecipe(
                    recipe
            )) {

                registeredKeys.add(
                        key
                );

            } else {

                plugin.getLogger().warning(
                        "Failed to register recipe: "
                                + key
                );
            }
        }

        /*
         * ========================================================
         * 逗猫棒：木棍 + 生鳕鱼
         * ========================================================
         */

        NamespacedKey toolRecipeKey =
                new NamespacedKey(
                        plugin,
                        "cat_tool"
                );

        ShapelessRecipe toolRecipe =
                new ShapelessRecipe(
                        toolRecipeKey,
                        CatToolItem.create(
                                toolKey
                        )
                );

        toolRecipe.addIngredient(
                1,
                Material.STICK
        );

        toolRecipe.addIngredient(
                1,
                Material.COD
        );

        if (Bukkit.addRecipe(
                toolRecipe
        )) {

            registeredKeys.add(
                    toolRecipeKey
            );

        } else {

            plugin.getLogger().warning(
                    "Failed to register recipe: "
                            + toolRecipeKey
            );
        }
    }

    public void unregisterAll() {

        for (NamespacedKey key :
                registeredKeys) {

            Bukkit.removeRecipe(
                    key
            );
        }

        registeredKeys.clear();
    }
}