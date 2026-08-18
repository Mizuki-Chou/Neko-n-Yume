package mizukichou.nekonyume.achievement;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
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
 * 成就殿堂面板（54 格）。
 *
 * <p>
 * 布局：
 * 顶部信息行（成就殿堂 / 猫头 / 关闭），
 * 成就区自第 9 格起，按枚举顺序排列 16 个成就。
 * 面板只读：已解锁显示绿色勾选，
 * 未解锁显示当前进度 / 阈值。
 * </p>
 */
public class AchievementGuiManager {

    private static final int SIZE = 54;

    private static final int SLOT_INFO = 0;
    private static final int SLOT_HEAD = 4;
    private static final int SLOT_CLOSE = 8;
    private static final int SLOT_FIRST_ACHIEVEMENT = 9;

    private final CatStore store;
    private final CatCache cache;
    private final AchievementService service;

    public AchievementGuiManager(
            CatStore store,
            CatCache cache,
            AchievementService service
    ) {

        this.store = store;
        this.cache = cache;
        this.service = service;
    }

    public void open(Player player) {

        if (player == null) {
            return;
        }

        UUID playerUuid =
                player.getUniqueId();

        if (!store.hasCat(playerUuid)) {
            return;
        }

        Cat cat =
                cache.loadCat(player);

        if (cat == null) {
            return;
        }

        AchievementGuiHolder holder =
                new AchievementGuiHolder(
                        playerUuid
                );

        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        SIZE,
                        "🏆 成就殿堂"
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
             i < SIZE;
             i++) {

            inventory.setItem(
                    i,
                    filler
            );
        }

        /*
         * 已解锁计数。
         */
        int unlockedCount = 0;

        for (CatAchievement achievement :
                CatAchievement.values()) {

            if (store.isAchievementUnlocked(
                    playerUuid,
                    achievement.name()
            )) {

                unlockedCount++;
            }
        }

        inventory.setItem(
                SLOT_INFO,
                item(
                        Material.EXPERIENCE_BOTTLE,
                        "§e成就殿堂",
                        "§7与猫咪的每一次互动，",
                        "§7都会成为值得纪念的成就。",
                        "§7已解锁: §f"
                                + unlockedCount
                                + " / "
                                + CatAchievement.values().length
                )
        );

        inventory.setItem(
                SLOT_HEAD,
                item(
                        Material.CAT_SPAWN_EGG,
                        "§d🐱 " + cat.getName(),
                        "§7陪伴: 第 "
                                + cat.getCompanionDays(
                                System.currentTimeMillis()
                        )
                                + " 天"
                )
        );

        inventory.setItem(
                SLOT_CLOSE,
                item(
                        Material.BARRIER,
                        "§c关闭"
                )
        );

        /*
         * 成就区。
         */
        int slot =
                SLOT_FIRST_ACHIEVEMENT;

        for (CatAchievement achievement :
                CatAchievement.values()) {

            inventory.setItem(
                    slot++,
                    achievementItem(
                            player,
                            cat,
                            achievement
                    )
            );
        }

        player.openInventory(
                inventory
        );
    }

    /*
     * ============================================================
     * 成就物品
     * ============================================================
     */

    private ItemStack achievementItem(
            Player player,
            Cat cat,
            CatAchievement achievement
    ) {

        boolean unlocked =
                store.isAchievementUnlocked(
                        player.getUniqueId(),
                        achievement.name()
                );

        if (unlocked) {

            return item(
                    achievement.getIcon(),
                    "§e🏆 §6"
                            + achievement.getDisplayName(),
                    "§7"
                            + achievement.getDescription(),
                    "§a✔ 已解锁",
                    "§7奖励: "
                            + rewardLine(
                            achievement
                    )
            );
        }

        int value =
                service.currentValue(
                        achievement,
                        player,
                        cat
                );

        return item(
                achievement.getIcon(),
                "§8🏆 "
                        + achievement.getDisplayName(),
                "§7"
                        + achievement.getDescription(),
                "§7进度: §f"
                        + value
                        + " / "
                        + achievement.getThreshold(),
                "§8奖励: "
                        + rewardLine(
                        achievement
                )
        );
    }

    private String rewardLine(
            CatAchievement achievement
    ) {

        int xp =
                service.rewardXp(
                        achievement
                );

        int meow =
                service.rewardMeowPower(
                        achievement
                );

        if (xp > 0 && meow > 0) {

            return "+" + xp
                    + " 经验, +"
                    + meow
                    + " 喵力";
        }

        if (meow > 0) {

            return "+" + meow + " 喵力";
        }

        return "+" + xp + " 经验";
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