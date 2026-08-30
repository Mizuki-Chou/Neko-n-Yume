package mizukichou.nekonyume.gui;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CareMath;
import mizukichou.nekonyume.cat.EquipBonusAttribute;
import mizukichou.nekonyume.cat.GrowthMath;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.lang.Lang;
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
 * 9×6 面板：第二行属性、第三行行为模式、第五行功能入口、
 * 末行中间关闭按钮。
 * </p>
 *
 * <p>
 * 构造注入（CatStore / CatCache / ConfigManager / Lang）；
 * 进度显示统一走 GrowthMath，与配置曲线一致。
 * 0.7.0：物品文案改走 Lang（gui.* 节）。
 * </p>
 */
public class CatGuiManager {

    private static final int INVENTORY_SIZE = 54;

    /*
     * 槽位布局
     *
     * 第二行（9~17）：属性区（等级/喵阶/性格/猫头/饥饿/好感/健康）
     * 第三行（21~23）：行为模式（跟随/坐下/自由）
     * 第五行（39~41）：功能入口（技能/装备/成就）
     * 第六行（49）：关闭
     */
    private static final int SLOT_CAT_HEAD = 13;
    private static final int SLOT_LEVEL = 10;
    private static final int SLOT_MEOW = 11;
    private static final int SLOT_PERSONALITY = 12;
    private static final int SLOT_HUNGER = 14;
    private static final int SLOT_AFFECTION = 15;
    private static final int SLOT_HEALTH = 16;
    private static final int SLOT_MODE_FOLLOW = 21;
    private static final int SLOT_MODE_SIT = 22;
    private static final int SLOT_MODE_FREE = 23;

    private static final int SLOT_SKILL_ENTRY = 39;
    private static final int SLOT_EQUIP_ENTRY = 40;
    private static final int SLOT_ACHIEVEMENT_ENTRY = 41;
    private static final int SLOT_CLOSE = 49;

    private final CatStore store;
    private final CatCache cache;
    private final ConfigManager configManager;
    private final Lang lang;

    public CatGuiManager(
            CatStore store,
            CatCache cache,
            ConfigManager configManager,
            Lang lang
    ) {

        this.store = store;
        this.cache = cache;
        this.configManager = configManager;
        this.lang = lang;
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

        ConfigSnapshot config =
                configManager.snapshot();

        GuiHolder holder =
                new GuiHolder(
                        Page.CAT,
                        playerUUID
                );

        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        INVENTORY_SIZE,
                        lang.forPlayer(player).text(
                                "gui.title"
                        )
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
                        lang.forPlayer(player).text(
                                "gui.head-mood",
                                cat.getMood().getIcon(),
                                lang.forPlayer(player).text(
                                        "mood-name."
                                                + cat.getMood()
                                                .name()
                                                .toLowerCase(
                                                        java.util.Locale.ROOT
                                                )
                                )
                        ),
                        lang.forPlayer(player).text(
                                "gui.head-days",
                                String.valueOf(
                                        cat.getCompanionDays(
                                                System.currentTimeMillis()
                                        )
                                )
                        ),
                        lang.forPlayer(player).text(
                                "gui.head-bond",
                                lang.forPlayer(player).text(
                                        CareMath.bondFor(
                                                cat,
                                                config.getCare()
                                        ).langKey()
                                ),
                                String.valueOf(
                                        cat.getAffection()
                                )
                        ),
                        lang.forPlayer(player).text(
                                "gui.head-combat-bonus",
                                String.valueOf(
                                        (int) Math.round(
                                                (CareMath.battleDamageMultiplier(
                                                        cat.getMood(),
                                                        CareMath.bondFor(
                                                                cat,
                                                                config.getCare()
                                                        ),
                                                        config.getCare()
                                                ) - 1.0) * 100.0
                                        )
                                )
                        ),
                        lang.forPlayer(player).text(
                                "gui.head-equipment",
                                headEquipmentText(
                                        player,
                                        cat
                                )
                        ),
                        lang.forPlayer(player).text(
                                "gui.head-enter-skill"
                        )
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
                        config.getGrowth()
                                .getLevelCurveBase()
                );

        inventory.setItem(
                SLOT_LEVEL,
                item(
                        Material.EXPERIENCE_BOTTLE,
                        lang.forPlayer(player).text(
                                "gui.level-name"
                        ),
                        lang.forPlayer(player).text(
                                "gui.level-value",
                                String.valueOf(
                                        level
                                )
                        ),
                        lang.forPlayer(player).text(
                                "gui.level-xp",
                                String.valueOf(
                                        cat.getExperience()
                                ),
                                String.valueOf(
                                        nextLevelXp
                                )
                        )
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
                        config.getMeow()
                                .getRankCurveOffset()
                );

        inventory.setItem(
                SLOT_MEOW,
                item(
                        Material.AMETHYST_SHARD,
                        lang.forPlayer(player).text(
                                "gui.meow-name"
                        ),
                        lang.forPlayer(player).text(
                                "gui.meow-value",
                                String.valueOf(
                                        meowRank
                                )
                        ),
                        lang.forPlayer(player).text(
                                "gui.meow-power",
                                String.valueOf(
                                        cat.getMeowPower()
                                ),
                                String.valueOf(
                                        nextRankPower
                                )
                        )
                )
        );

        /*
         * 性格。
         */
        inventory.setItem(
                SLOT_PERSONALITY,
                item(
                        Material.PAPER,
                        lang.forPlayer(player).text(
                                "gui.personality-name"
                        ),
                        lang.forPlayer(player).text(
                                "gui.personality-value",
                                lang.forPlayer(player).text(
                                        "personality-name."
                                                + cat.getPersonality()
                                                .name()
                                                .toLowerCase(
                                                        java.util.Locale.ROOT
                                                )
                                )
                        )
                )
        );

        /*
         * 饥饿。
         */
        inventory.setItem(
                SLOT_HUNGER,
                item(
                        Material.COOKED_COD,
                        lang.forPlayer(player).text(
                                "gui.hunger-name"
                        ),
                        lang.forPlayer(player).text(
                                "gui.hunger-value",
                                String.valueOf(
                                        cat.getHunger()
                                )
                        )
                )
        );

        /*
         * 好感。
         */
        inventory.setItem(
                SLOT_AFFECTION,
                item(
                        Material.RED_DYE,
                        lang.forPlayer(player).text(
                                "gui.affection-name"
                        ),
                        lang.forPlayer(player).text(
                                "gui.affection-value",
                                String.valueOf(
                                        cat.getAffection()
                                )
                        )
                )
        );

        /*
         * 健康。
         */
        inventory.setItem(
                SLOT_HEALTH,
                item(
                        Material.GOLDEN_APPLE,
                        lang.forPlayer(player).text(
                                "gui.health-name"
                        ),
                        lang.forPlayer(player).text(
                                "gui.health-value",
                                String.valueOf(
                                        cat.getHealth()
                                )
                        )
                )
        );

        /*
         * 行为模式按钮。
         */
        inventory.setItem(
                SLOT_MODE_FOLLOW,
                modeItem(
                        player,
                        cat,
                        CatBehaviorMode.FOLLOW,
                        lang.forPlayer(player).text(
                                "gui.mode-follow"
                        ),
                        Material.LEAD
                )
        );

        inventory.setItem(
                SLOT_MODE_SIT,
                modeItem(
                        player,
                        cat,
                        CatBehaviorMode.SIT,
                        lang.forPlayer(player).text(
                                "gui.mode-sit"
                        ),
                        Material.GREEN_WOOL
                )
        );

        inventory.setItem(
                SLOT_MODE_FREE,
                modeItem(
                        player,
                        cat,
                        CatBehaviorMode.FREE,
                        lang.forPlayer(player).text(
                                "gui.mode-free"
                        ),
                        Material.FEATHER
                )
        );

        /*
         * 功能入口（0.8.0 面板扩展）。
         */
        inventory.setItem(
                SLOT_SKILL_ENTRY,
                item(
                        Material.ENCHANTED_BOOK,
                        lang.forPlayer(player).text(
                                "gui.entry-skill"
                        )
                )
        );

        inventory.setItem(
                SLOT_EQUIP_ENTRY,
                item(
                        Material.SHIELD,
                        lang.forPlayer(player).text(
                                "gui.entry-equipment"
                        )
                )
        );

        inventory.setItem(
                SLOT_ACHIEVEMENT_ENTRY,
                item(
                        Material.NETHER_STAR,
                        lang.forPlayer(player).text(
                                "gui.entry-achievements"
                        )
                )
        );

        /*
         * 关闭。
         */
        inventory.setItem(
                SLOT_CLOSE,
                item(
                        Material.BARRIER,
                        lang.forPlayer(player).text(
                                "gui.close"
                        )
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
            Player player,
            Cat cat,
            CatBehaviorMode mode,
            String displayName,
            Material material
    ) {

        if (cat.getBehaviorMode() == mode) {

            return item(
                    material,
                    displayName,
                    lang.forPlayer(player).text(
                            "gui.mode-current"
                    )
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

    /*
     * 头部装备行文案：未装备 → equip-none；
     * 已装备 → 装备名，觉醒附加属性时追加「✦ 属性名」。
     */
    private String headEquipmentText(
            Player player,
            Cat cat
    ) {

        if (cat.getEquippedItem() == null) {

            return lang.forPlayer(player).text(
                    "equip-none"
            );
        }

        String text =
                lang.forPlayer(player).text(
                        cat.getEquippedItem()
                                .getLangKey()
                );

        EquipBonusAttribute bonus =
                cat.getEquippedBonus();

        if (bonus != null) {

            text +=
                    " ✦ "
                            + lang.forPlayer(player).text(
                            bonus.getLangKey()
                    );
        }

        return text;
    }
}
