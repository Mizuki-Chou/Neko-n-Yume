package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.MeowDanQuality;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.List;

/**
 * 喵丹升级配方的精确校验（兜底版）。
 *
 * <p>
 * 不依赖配方键、不依赖配方结果识别、不依赖枚举声明顺序：
 * 直接扫描工作台矩阵——
 * 只要"9 个同品质且未过期"的喵丹，
 * 就现场给出高一品质的结果；
 * 无效组合且命中的是本插件配方时，结果置空。
 * </p>
 */
public class MeowDanCraftListener implements Listener {

    private final CatFoodManager foodManager;

    public MeowDanCraftListener(
            CatFoodManager foodManager
    ) {

        this.foodManager = foodManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(
            PrepareItemCraftEvent event
    ) {

        ItemStack[] matrix =
                event.getInventory()
                        .getMatrix();

        /*
         * 统计矩阵：
         * count    = 非空材料数
         * unified  = 全部材料的统一品质（null 表示无/不一致）
         * invalid  = 存在非喵丹材料
         */
        int count = 0;
        MeowDanQuality unified = null;
        boolean invalid = false;

        for (ItemStack item :
                matrix) {

            if (item == null ||
                    item.getType().isAir()) {

                continue;
            }

            count++;

            if (!foodManager.isMeowDan(
                    item
            )) {

                invalid = true;
                continue;
            }

            MeowDanQuality quality =
                    foodManager.getMeowDanQuality(
                            item
                    );

            if (quality == null) {

                invalid = true;

            } else if (unified == null) {

                unified = quality;

            } else if (unified != quality) {

                invalid = true;
            }
        }

        boolean nineSameQuality =
                count == 9 &&
                        !invalid &&
                        unified != null;

        /*
         * 判断当前命中的配方是否"本插件相关"：
         * 结果本身是喵丹，或结果是金粒（占位结果被剥离元数据时）。
         */
        Recipe recipe =
                event.getRecipe();

        boolean ourRecipe = false;

        if (recipe != null &&
                recipe.getResult() != null) {

            ItemStack result =
                    recipe.getResult();

            ourRecipe =
                    foodManager.getMeowDanQuality(
                            result
                    ) != null ||
                            result.getType()
                                    == Material.GOLD_NUGGET;
        }

        /*
         * 无效组合：
         * 仅当命中本插件配方时清空结果，
         * 不干预其他插件的配方。
         */
        if (!nineSameQuality) {

            if (ourRecipe) {

                event.getInventory()
                        .setResult(
                                null
                        );
            }

            return;
        }

        /*
         * 有效组合：9 个同品质未过期喵丹。
         * 按数值排序链给出下一级品质
         * （与枚举声明顺序无关）。
         */
        List<MeowDanQuality> qualities =
                CatFoodManager.orderedQualities();

        int index =
                qualities.indexOf(
                        unified
                );

        if (index < 0 ||
                index + 1 >= qualities.size()) {

            /*
             * 至极无法再升级：
             * 若命中本插件配方则清空。
             */
            if (ourRecipe) {

                event.getInventory()
                        .setResult(
                                null
                        );
            }

            return;
        }

        /*
         * 命中本插件配方或配方未识别（无匹配）时，
         * 现场生成下一级喵丹作为结果。
         */
        /*
         * P0-12：
         * 只在"命中的就是本插件配方"时接管结果；
         * recipe == null（无配方匹配）时不插手——
         * 避免覆盖其他插件的 9 金粒类配方。
         */
        if (ourRecipe) {

            event.getInventory()
                    .setResult(
                            foodManager.createMeowDan(
                                    qualities.get(
                                            index + 1
                                    ),
                                    1
                            )
                    );
        }

    }
}