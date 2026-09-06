package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.MeowDanQuality;
import mizukichou.nekonyume.craft.CraftingRecipes;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.List;

/**
 * 喵丹升级配方的精确校验。
 *
 * <p>
 * 策略（0.8.3 P0-12 起）：
 * 扫描工作台矩阵，确认“9 个同品质且未过期”的喵丹；
 * 同时确认命中的配方就是本插件注册的喵丹升级配方哒
 * （配方键由 {@link CraftingRecipes} 维护，枚举声明顺序无关）；
 * 命中时给出高一品质的结果，无效组合时结果置空捏。
 * </p>
 */
public class MeowDanCraftListener implements Listener {

    private final CatFoodManager foodManager;
    private final CraftingRecipes craftingRecipes;

    public MeowDanCraftListener(
            CatFoodManager foodManager,
            CraftingRecipes craftingRecipes
    ) {

        this.foodManager = foodManager;
        this.craftingRecipes = craftingRecipes;
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
         * count    = 非空材料数啦
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
         * 严格按注册键判定，
         * 不再用"结果是金粒"这类宽泛启发式，
         * 避免误伤其他插件的 9 ×金粒配方。
         */
        Recipe recipe =
                event.getRecipe();

        boolean ourRecipe =
                recipe instanceof Keyed keyed &&
                        craftingRecipes.isMeowDanUpgradeKey(
                                keyed.getKey()
                        );

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
         * 0.9.0更新：矩阵含喵丹但命中
         * 原版金粒配方（9 金粒 → 金锭 / 8 金粒 → 金胡萝卜）
         * 时，阻止合成——喵丹会被原版当普通金粒消耗。
         */
        if (!ourRecipe &&
                matrixContainsMeowDan(
                        matrix
                )) {

            event.getInventory()
                    .setResult(
                            null
                    );
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

            /*
             * 结果物品按合成者的客户端语言生成
             * （控制台/异常时回退默认语言）。
             */
            Player crafter = null;

            if (event.getView()
                    .getPlayer()
                    instanceof Player player) {

                crafter = player;
            }

            event.getInventory()
                    .setResult(
                            foodManager.createMeowDan(
                                    qualities.get(
                                            index + 1
                                    ),
                                    1,
                                    crafter
                            )
                    );
        }

    }

    /*
     * 0.9.0更新：工作台点击结果格的最终
     * 防线——Prepare 被跳过（模组客户端 / 极端时序）时，
     * 矩阵含喵丹且命中非本插件配方 → 直接取消合成。
     */
    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onCraftItem(
            CraftItemEvent event
    ) {

        Recipe recipe =
                event.getRecipe();

        boolean ourRecipe =
                recipe instanceof Keyed keyed &&
                        craftingRecipes.isMeowDanUpgradeKey(
                                keyed.getKey()
                        );

        if (ourRecipe) {

            /*
             * 本插件配方：放行（Prepare 已生成正确结果）。
             */
            return;
        }

        if (matrixContainsMeowDan(
                event.getInventory()
                        .getMatrix()
        )) {

            event.setCancelled(
                    true
            );
        }
    }

    /*
     * 0.9.0更新：1.21 合成器没有 Prepare
     * 阶段（自动合成）——9 个原版金粒会直接产出喵丹，
     * 这是经济复制漏洞。合成器一律禁止喵丹升级配方，
     * 且输入含喵丹时禁止任何原版配方消耗它。
     */
    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onCrafterCraft(
            CrafterCraftEvent event
    ) {

        Recipe recipe =
                event.getRecipe();

        if (recipe instanceof Keyed keyed &&
                craftingRecipes.isMeowDanUpgradeKey(
                        keyed.getKey()
                )) {

            /*
             * 合成器不走品质校验：本插件配方一律取消。
             */
            event.setCancelled(
                    true
            );

            return;
        }

        /*
         * 结果含喵丹（其他路径产出）→ 取消。
         */
        ItemStack result =
                event.getResult();

        if (result != null &&
                foodManager.isMeowDan(
                        result
                )) {

            event.setCancelled(
                    true
            );

            return;
        }

        /*
         * 输入含喵丹（被原版金粒配方消耗）→ 取消。
         */
        if (event.getBlock()
                .getState()
                instanceof org.bukkit.block.Crafter crafter) {

            for (ItemStack item :
                    crafter.getSnapshotInventory()
                            .getStorageContents()) {

                if (item != null &&
                        foodManager.isMeowDan(
                                item
                        )) {

                    event.setCancelled(
                            true
                    );

                    return;
                }
            }
        }
    }

    /*
     * 矩阵中是否含本插件喵丹（金粒材质的伪装身份）。
     */
    private boolean matrixContainsMeowDan(
            ItemStack[] matrix
    ) {

        for (ItemStack item :
                matrix) {

            if (item != null &&
                    !item.getType().isAir() &&
                    foodManager.isMeowDan(
                            item
                    )) {

                return true;
            }
        }

        return false;
    }
}

