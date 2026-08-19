package mizukichou.nekonyume.skill;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.cat.CatTier;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.lang.Lang;
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
 *
 * <p>
 * 0.7.0：物品文案改走 Lang（lang/zh_cn.yml 的 skill-gui.* 节）；
 * 配置改走 ConfigManager 快照。
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
    private final ConfigManager configManager;
    private final Lang lang;

    public SkillGuiManager(
            CatStore store,
            CatCache cache,
            CatSkillManager skillManager,
            ConfigManager configManager,
            Lang lang
    ) {

        this.store = store;
        this.cache = cache;
        this.skillManager = skillManager;
        this.configManager = configManager;
        this.lang = lang;
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
                        lang.forPlayer(player).text(
                                "skill-gui.title"
                        )
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
                tierItem(player, cat)
        );

        inventory.setItem(
                SLOT_INFO,
                infoItem(player, cat)
        );

        inventory.setItem(
                SLOT_PROGRESS,
                progressItem(player, cat)
        );

        inventory.setItem(
                SLOT_HINT,
                hintItem(player, cat)
        );

        inventory.setItem(
                SLOT_HEAD,
                item(
                        Material.CAT_SPAWN_EGG,
                        "§d🐱 "
                                + cat.getName(),
                        lang.forPlayer(player).text(
                                "skill-gui.head-lore"
                        )
                )
        );

        inventory.setItem(
                SLOT_CLOSE,
                item(
                        Material.BARRIER,
                        lang.forPlayer(player).text(
                                "skill-gui.close"
                        )
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
                                player,
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

        /*
         * 返回语言键而非硬编码文案：
         * 具体显示文案由 lang 文件的 checkpoint.hint-N 提供。
         */
        return "checkpoint.hint-" + checkpoint;
    }

    /*
     * ============================================================
     * 物品构建
     * ============================================================
     */

    private ItemStack tierItem(
            Player player,
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
                lang.forPlayer(player).text(
                        "skill-gui.tier-name",
                        tierColor(cat.getTier())
                                + lang.forPlayer(player).text(
                                "tier-name."
                                        + cat.getTier()
                                        .name()
                                        .toLowerCase(
                                                java.util.Locale.ROOT
                                        )
                        )
                ),
                lang.forPlayer(player).text(
                        "skill-gui.tier-lore"
                )
        );
    }

    private ItemStack infoItem(
            Player player,
            Cat cat
    ) {

        return item(
                Material.EXPERIENCE_BOTTLE,
                lang.forPlayer(player).text(
                        "skill-gui.growth-name"
                ),
                lang.forPlayer(player).text(
                        "skill-gui.growth-level",
                        String.valueOf(
                                cat.getLevel()
                        )
                ),
                lang.forPlayer(player).text(
                        "skill-gui.growth-rank",
                        String.valueOf(
                                cat.getMeowRank()
                        )
                )
        );
    }

    private ItemStack progressItem(
            Player player,
            Cat cat
    ) {

        return item(
                Material.AMETHYST_SHARD,
                lang.forPlayer(player).text(
                        "skill-gui.progress-name"
                ),
                lang.forPlayer(player).text(
                        "skill-gui.progress-count",
                        String.valueOf(
                                cat.getSkills().size()
                        ),
                        String.valueOf(
                                cat.getSkillSlotCount()
                        )
                )
        );
    }

    private ItemStack hintItem(
            Player player,
            Cat cat
    ) {

        return item(
                Material.GOLD_NUGGET,
                lang.forPlayer(player).text(
                        "skill-gui.hint-name"
                ),
                lang.forPlayer(player).text(
                        "skill-gui.hint-cast"
                ),
                lang.forPlayer(player).text(
                        "skill-gui.hint-refresh",
                        skillManager
                                .getRefreshCostDisplay(
                                        lang.forPlayer(player),
                                        false
                                )
                ),
                lang.forPlayer(player).text(
                        "skill-gui.hint-dream-multiplier",
                        String.valueOf(
                                configManager.snapshot()
                                        .getSkills()
                                        .getDreamSlotCostMultiplier()
                        )
                )
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
                lang.forPlayer(player).text(
                        "skill-gui.slot-empty"
                ),
                lang.forPlayer(player).text(
                        "skill-gui.slot-index",
                        String.valueOf(
                                index + 1
                        )
                )
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
                lang.forPlayer(player).text(
                        "skill-gui.quality",
                        tierColor
                                + lang.forPlayer(player).text(
                                "tier-name."
                                        + skill.getTier()
                                        .name()
                                        .toLowerCase(
                                                java.util.Locale.ROOT
                                        )
                        )
                )
        );

        lore.add(
                "§7"
                        + lang.forPlayer(player).text(
                        "skill-desc."
                                + skill.name()
                                .toLowerCase(
                                        java.util.Locale.ROOT
                                )
                )
        );

        lore.add(
                lang.forPlayer(player).text(
                        skill.isActive()
                                ? "skill-gui.type-active"
                                : "skill-gui.type-passive"
                )
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
                        lang.forPlayer(player).text(
                                "skill-gui.cooldown",
                                String.valueOf(
                                        remainingSec
                                )
                        )
                );

            } else {

                lore.add(
                        lang.forPlayer(player).text(
                                "skill-gui.ready"
                        )
                );
            }
        }

        if (dreamSlot) {

            lore.add(
                    lang.forPlayer(player).text(
                            "skill-gui.dream-slot"
                    )
            );
        }

        lore.add(
                lang.forPlayer(player).text(
                        "skill-gui.slot-index",
                        String.valueOf(
                                index + 1
                        )
                )
        );

        String refreshCost =
                skillManager
                        .getRefreshCostDisplay(
                                lang.forPlayer(player),
                                dreamSlot
                        );

        if (skill.isActive()) {

            lore.add(
                    lang.forPlayer(player).text(
                            "skill-gui.cast-refresh",
                            refreshCost
                    )
            );

        } else {

            lore.add(
                    lang.forPlayer(player).text(
                            "skill-gui.passive-refresh",
                            refreshCost
                    )
            );
        }

        return item(
                skill.getIcon(),
                tierColor
                        + lang.forPlayer(player).text(
                        "skill-name."
                                + skill.name()
                                .toLowerCase(
                                        java.util.Locale.ROOT
                                )
                ),
                lore.toArray(
                        new String[0]
                )
        );
    }

    private ItemStack lockedSlotItem(
            Player player,
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
                    lang.forPlayer(player).text(
                            "skill-gui.max-reached"
                    ),
                    lang.forPlayer(player).text(
                            "skill-gui.max-reached-tier",
                            lang.forPlayer(player).text(
                                    "tier-name."
                                            + cat.getTier()
                                            .name()
                                            .toLowerCase(
                                                    java.util.Locale.ROOT
                                            )
                            )
                    ),
                    lang.forPlayer(player).text(
                            "skill-gui.max-reached-slots",
                            String.valueOf(
                                    maxSlots
                            )
                    )
            );
        }

        int checkpoint =
                checkpointForSlot(
                        cat.getTier(),
                        index
                );

        return item(
                Material.BLACK_STAINED_GLASS_PANE,
                lang.forPlayer(player).text(
                        "skill-gui.locked-name"
                ),
                lang.forPlayer(player).text(
                        "skill-gui.locked-hint"
                ),
                "§7"
                        + lang.forPlayer(player).text(
                        "checkpoint.hint-"
                                + checkpoint
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
