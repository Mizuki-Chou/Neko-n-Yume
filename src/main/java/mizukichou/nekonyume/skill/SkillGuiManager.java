package mizukichou.nekonyume.skill;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.cat.CatTier;
import mizukichou.nekonyume.config.PluginConfig;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 技能面板（54 格）。
 *
 * <p>
 * 布局：
 * 顶部信息行（底蕴 / 成长 / 槽位进度 / 操作说明 / 猫头 / 关闭）
 * 槽位区（第 18 格起，最多 10 个槽）
 * </p>
 */
public class SkillGuiManager {

    private static final int SIZE = 54;

    private static final int SLOT_TIER = 0;
    private static final int SLOT_INFO = 1;
    private static final int SLOT_PROGRESS = 2;
    private static final int SLOT_HINT = 3;
    private static final int SLOT_HEAD = 4;
    private static final int SLOT_CLOSE = 8;

    private static final int SLOT_FIRST_SKILL = 18;

    private static final int MAX_SKILL_SLOTS = 10;

    private final CatStore store;
    private final CatCache cache;
    private final CatSkillManager skillManager;
    private final PluginConfig config;

    public SkillGuiManager(
            CatStore store,
            CatCache cache,
            CatSkillManager skillManager,
            PluginConfig config
    ) {

        this.store = store;
        this.cache = cache;
        this.skillManager = skillManager;
        this.config = config;
    }

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

        SkillGuiHolder holder =
                new SkillGuiHolder(
                        playerUUID
                );

        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        SIZE,
                        "🐱 技能面板"
                );

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
         * 顶部信息行。
         */
        inventory.setItem(
                SLOT_TIER,
                tierItem(cat)
        );

        inventory.setItem(
                SLOT_INFO,
                infoItem(cat)
        );

        inventory.setItem(
                SLOT_PROGRESS,
                progressItem(cat)
        );

        inventory.setItem(
                SLOT_HINT,
                hintItem(cat)
        );

        inventory.setItem(
                SLOT_HEAD,
                item(
                        Material.CAT_SPAWN_EGG,
                        "§d🐱 "
                                + cat.getName(),
                        "§7技能面板"
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
         * 槽位区。
         */
        int slotCount =
                cat.getSkillSlotCount();

        List<CatSkill> skills =
                cat.getSkills();

        for (int i = 0;
             i < MAX_SKILL_SLOTS;
             i++) {

            int slot =
                    SLOT_FIRST_SKILL + i;

            if (i < slotCount) {

                inventory.setItem(
                        slot,
                        unlockedSlotItem(
                                player,
                                cat,
                                i,
                                skills
                        )
                );

            } else {

                inventory.setItem(
                        slot,
                        lockedSlotItem(
                                cat,
                                i
                        )
                );
            }
        }

        player.openInventory(
                inventory
        );
    }

    /*
     * ============================================================
     * 槽位 → 拐点映射（纯函数，可单元测试）
     * ============================================================
     */

    public static int checkpointForSlot(
            CatTier tier,
            int index
    ) {

        if (tier == null) {
            tier = CatTier.COMMON;
        }

        if (tier == CatTier.DREAM) {

            if (index <= 0) {
                return 0;
            }

            if (index <= 3) {
                return 1;
            }

            if (index <= 6) {
                return 2;
            }

            return 3;
        }

        int per =
                tier == CatTier.UNIQUE
                        ? 2
                        : 1;

        if (index < per) {
            return 1;
        }

        if (index < per * 2) {
            return 2;
        }

        return 3;
    }

    public static String checkpointHint(
            int checkpoint
    ) {

        return switch (checkpoint) {

            case 0 -> "天生梦槽";
            case 1 -> "喵阶 1";
            case 2 -> "喵阶 10 且等级 30";
            case 3 -> "喵阶 30 且等级 60";
            default -> "未知";
        };
    }

    /*
     * ============================================================
     * 物品构建
     * ============================================================
     */

    private ItemStack tierItem(
            Cat cat
    ) {

        Material material =
                switch (cat.getTier()) {

                    case COMMON -> Material.PAPER;
                    case RARE -> Material.IRON_INGOT;
                    case UNIQUE -> Material.DIAMOND;
                    case DREAM -> Material.NETHER_STAR;
                };

        return item(
                material,
                "§e底蕴: "
                        + tierColor(cat.getTier())
                        + cat.getTier().getDisplayName(),
                "§7技能槽成长随底蕴提升"
        );
    }

    private ItemStack infoItem(
            Cat cat
    ) {

        return item(
                Material.EXPERIENCE_BOTTLE,
                "§a成长",
                "§7等级: "
                        + cat.getLevel(),
                "§7喵阶: "
                        + cat.getMeowRank()
        );
    }

    private ItemStack progressItem(
            Cat cat
    ) {

        return item(
                Material.AMETHYST_SHARD,
                "§d技能槽",
                "§7"
                        + cat.getSkills().size()
                        + " / "
                        + cat.getSkillSlotCount()
                        + " 槽"
        );
    }

    private ItemStack hintItem(
            Cat cat
    ) {

        return item(
                Material.GOLD_NUGGET,
                "§6操作说明",
                "§7左键: 施放主动技能",
                "§7右键: 刷新技能（花费 "
                        + skillManager
                        .getRefreshCostDisplay(
                                false
                        )
                        + "）",
                "§7梦槽刷新消耗 ×"
                        + config
                        .getDreamSlotCostMultiplier()
        );
    }

    private ItemStack unlockedSlotItem(
            Player player,
            Cat cat,
            int index,
            List<CatSkill> skills
    ) {

        if (index < skills.size()) {

            return skillItem(
                    player,
                    cat,
                    index,
                    skills.get(index)
            );
        }

        /*
         * 空槽（理论上不发生：解锁即免费抽取）。
         */
        return item(
                Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                "§7空槽",
                "§8槽位 #" + (index + 1)
        );
    }

    private ItemStack skillItem(
            Player player,
            Cat cat,
            int index,
            CatSkill skill
    ) {

        boolean dreamSlot =
                cat.isDreamSlot(
                        index
                );

        String tierColor =
                tierColor(
                        skill.getTier()
                );

        List<String> lore =
                new ArrayList<>();

        lore.add(
                "§7品质: "
                        + tierColor
                        + skill.getTier()
                        .getDisplayName()
        );

        lore.add(
                "§7" + skill.getDescription()
        );

        lore.add(
                "§7类型: "
                        + (skill.isActive()
                        ? "§e主动"
                        : "§b被动")
        );

        if (skill.isActive()) {

            long remainingSec =
                    skillManager
                            .getRemainingCooldownSeconds(
                                    player,
                                    skill
                            );

            if (remainingSec > 0) {

                lore.add(
                        "§c⏳ 冷却中: "
                                + remainingSec
                                + " 秒"
                );

            } else {

                lore.add(
                        "§a✔ 已就绪"
                );
            }
        }

        if (dreamSlot) {

            lore.add(
                    "§d✦ 梦槽 ✦"
            );
        }

        lore.add(
                "§8槽位 #" + (index + 1)
        );

        String refreshCost =
                skillManager
                        .getRefreshCostDisplay(
                                dreamSlot
                        );

        if (skill.isActive()) {

            lore.add(
                    "§8左键施放 · 右键刷新（"
                            + refreshCost
                            + "）"
            );

        } else {

            lore.add(
                    "§8被动自动生效 · 右键刷新（"
                            + refreshCost
                            + "）"
            );
        }

        return item(
                skill.getIcon(),
                tierColor
                        + skill.getDisplayName(),
                lore.toArray(
                        new String[0]
                )
        );
    }

    private ItemStack lockedSlotItem(
            Cat cat,
            int index
    ) {

        /*
         * 补丁 3：已达上限判定。
         *
         * 该底蕴下可拥有的最大槽数 = slotCount(3)：
         * 普通 1 / 稀有 3 / 独特 6 / 梦幻 10。
         */
        int maxSlots =
                cat.getTier().slotCount(3);

        if (index >= maxSlots) {

            return item(
                    Material.RED_STAINED_GLASS_PANE,
                    "§c已达上限",
                    "§7"
                            + cat.getTier().getDisplayName()
                            + "底蕴的猫咪",
                    "§7最多拥有 "
                            + maxSlots
                            + " 个技能槽"
            );
        }

        int checkpoint =
                checkpointForSlot(
                        cat.getTier(),
                        index
                );

        return item(
                Material.BLACK_STAINED_GLASS_PANE,
                "§8未解锁技能槽",
                "§7解锁条件:",
                "§7" + checkpointHint(
                        checkpoint
                )
        );
    }

    private String tierColor(
            CatTier tier
    ) {

        return switch (tier) {

            case COMMON -> "§7";
            case RARE -> "§a";
            case UNIQUE -> "§9";
            case DREAM -> "§6";
        };
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
                        List.of(
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