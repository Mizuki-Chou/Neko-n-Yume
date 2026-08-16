package mizukichou.nekonyume.gui;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.GrowthMath;
import mizukichou.nekonyume.config.PluginConfig;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.UUID;

/**
 * 猫咪状态面板。
 *
 * <p>
 * 9×3 只读面板 + 三个行为模式按钮 + 关闭按钮。
 * </p>
 *
 * <p>
 * 构造注入（CatStore / CatCache / PluginConfig）；
 * 进度显示统一走 GrowthMath，与配置曲线一致。
 * </p>
 */
public class CatGuiManager {

    private static final int INVENTORY_SIZE = 27;

    /*
     * 槽位布局
     */
    private static final int SLOT_CAT_HEAD = 13;
    private static final int SLOT_LEVEL = 10;
    private static final int SLOT_MEOW = 11;
    private static final int SLOT_PERSONALITY = 12;
    private static final int SLOT_HUNGER = 14;
    private static final int SLOT_AFFECTION = 15;
    private static final int SLOT_HEALTH = 16;
    private static final int SLOT_MODE_FOLLOW = 18;
    private static final int SLOT_MODE_SIT = 19;
    private static final int SLOT_MODE_FREE = 20;
    private static final int SLOT_CLOSE = 26;

    private final CatStore store;
    private final CatCache cache;
    private final PluginConfig config;

    public CatGuiManager(
            CatStore store,
            CatCache cache,
            PluginConfig config
    ) {

        this.store = store;
        this.cache = cache;
        this.config = config;
    }

    /*
     * ============================================================
     * 打开面板
     * ============================================================
     */

    public void open(
            Player player
    ) {

        if (player == null) {
            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        if (!store.hasCat(playerUUID)) {
            return;
        }

        Cat cat =
                cache.loadCat(player);

        if (cat == null) {
            return;
        }

        CatGuiHolder holder =
                new CatGuiHolder(
                        playerUUID
                );

        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        INVENTORY_SIZE,
                        "🐱 Neko n' Yume"
                );

        /*
         * 装饰背景。
         */
        ItemStack filler =
                item(
                        Material.GRAY_STAINED_GLASS_PANE,
                        "§0"
                );

        for (int i = 0;
             i < INVENTORY_SIZE;
             i++) {

            inventory.setItem(
                    i,
                    filler
            );
        }

        /*
         * 猫头。
         */
        inventory.setItem(
                SLOT_CAT_HEAD,
                item(
                        Material.CAT_SPAWN_EGG,
                        "§d🐱 "
                                + cat.getName(),
                        "§7心情: "
                                + cat.getMood().getIcon()
                                + " "
                                + cat.getMood().getDisplayName(),
                        "§7陪伴: 第 "
                                + cat.getCompanionDays(
                                System.currentTimeMillis()
                        )
                                + " 天",
                        "§9✦ 点此进入技能界面"
                )
        );

        /*
         * 等级。
         */
        int level =
                cat.getLevel();

        long nextLevelXp =
                GrowthMath.xpRequiredForLevel(
                        level + 1,
                        config.getLevelCurveBase()
                );

        inventory.setItem(
                SLOT_LEVEL,
                item(
                        Material.EXPERIENCE_BOTTLE,
                        "§e等级",
                        "§7等级: §f"
                                + level,
                        "§7经验: §f"
                                + cat.getExperience()
                                + " / "
                                + nextLevelXp
                )
        );

        /*
         * 喵阶。
         */
        int meowRank =
                cat.getMeowRank();

        long nextRankPower =
                GrowthMath.meowRequiredForRank(
                        meowRank + 1,
                        config.getMeowRankCurveOffset()
                );

        inventory.setItem(
                SLOT_MEOW,
                item(
                        Material.AMETHYST_SHARD,
                        "§d喵阶",
                        "§7喵阶: §f"
                                + meowRank,
                        "§7喵力: §f"
                                + cat.getMeowPower()
                                + " / "
                                + nextRankPower
                )
        );

        /*
         * 性格。
         */
        inventory.setItem(
                SLOT_PERSONALITY,
                item(
                        Material.PAPER,
                        "§b性格",
                        "§7"
                                + cat.getPersonality()
                                .getDisplayName()
                )
        );

        /*
         * 饥饿。
         */
        inventory.setItem(
                SLOT_HUNGER,
                item(
                        Material.COOKED_COD,
                        "§6饥饿",
                        "§7"
                                + cat.getHunger()
                                + " / 100"
                )
        );

        /*
         * 好感。
         */
        inventory.setItem(
                SLOT_AFFECTION,
                item(
                        Material.RED_DYE,
                        "§c好感",
                        "§7"
                                + cat.getAffection()
                                + " / 100"
                )
        );

        /*
         * 健康。
         */
        inventory.setItem(
                SLOT_HEALTH,
                item(
                        Material.GOLDEN_APPLE,
                        "§a健康",
                        "§7"
                                + cat.getHealth()
                                + " / 100"
                )
        );

        /*
         * 行为模式按钮。
         */
        inventory.setItem(
                SLOT_MODE_FOLLOW,
                modeItem(
                        cat,
                        CatBehaviorMode.FOLLOW,
                        "§a🐾 跟随",
                        Material.LEAD
                )
        );

        inventory.setItem(
                SLOT_MODE_SIT,
                modeItem(
                        cat,
                        CatBehaviorMode.SIT,
                        "§e🪑 坐下",
                        Material.GREEN_WOOL
                )
        );

        inventory.setItem(
                SLOT_MODE_FREE,
                modeItem(
                        cat,
                        CatBehaviorMode.FREE,
                        "§7🌿 自由",
                        Material.FEATHER
                )
        );

        /*
         * 关闭。
         */
        inventory.setItem(
                SLOT_CLOSE,
                item(
                        Material.BARRIER,
                        "§c关闭"
                )
        );

        player.openInventory(
                inventory
        );
    }

    /*
     * ============================================================
     * 工具
     * ============================================================
     */

    private ItemStack modeItem(
            Cat cat,
            CatBehaviorMode mode,
            String displayName,
            Material material
    ) {

        if (cat.getBehaviorMode() == mode) {

            return item(
                    material,
                    displayName,
                    "§7✔ 当前模式"
            );
        }

        return item(
                material,
                displayName
        );
    }

    private ItemStack item(
            Material material,
            String displayName,
            String... lore
    ) {

        ItemStack item =
                new ItemStack(
                        material,
                        1
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            meta.setDisplayName(
                    displayName
            );

            if (lore != null &&
                    lore.length > 0) {

                meta.setLore(
                        Arrays.asList(
                                lore
                        )
                );
            }

            item.setItemMeta(
                    meta
            );
        }

        return item;
    }
}