package mizukichou.nekonyume.gui;

import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.cat.CatPersonality;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 0.8.5：管理员猫咪详情面板。
 *
 * <p>
 * 从管理员模式的全服排行左键进入，展示单只猫的完整信息
 * （主人/名字/等级/喵阶/底蕴/性格/饱食/好感/生命/行为/花色/相识日/技能），
 * 并支持“强制删除”二级确认（全程 GUI，无命令）。
 * </p>
 */
public final class CatDetailGuiManager {

    private static final int INVENTORY_SIZE = 54;

    private static final int SLOT_HEAD = 10;
    private static final int SLOT_OWNER = 11;
    private static final int SLOT_NAME = 12;
    private static final int SLOT_LEVEL = 13;
    private static final int SLOT_MEOW = 14;
    private static final int SLOT_TIER = 15;
    private static final int SLOT_PERSONALITY = 16;
    private static final int SLOT_HUNGER = 19;
    private static final int SLOT_AFFECTION = 20;
    private static final int SLOT_HEALTH = 21;
    private static final int SLOT_BEHAVIOR = 22;
    private static final int SLOT_VARIANT = 23;
    private static final int SLOT_CREATED = 24;
    private static final int SLOT_SKILLS_HEADER = 25;

    private static final int[] SKILL_SLOTS = {
        28, 29, 30, 31, 32, 33, 34, 37, 38, 39
    };

    private static final int[] CONTENT_FILLER_SLOTS = {
        40, 41, 42, 43
    };

    private static final int SLOT_DELETE = 45;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_CLOSE = 53;

    private static final int[] BORDER_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 17, 18, 26, 27, 35, 36, 44,
        46, 47, 48, 50, 51, 52
    };

    private static final int CONFIRM_SIZE = 9;
    private static final int SLOT_CONFIRM_QUESTION = 1;
    private static final int SLOT_CONFIRM_YES = 3;
    private static final int SLOT_CONFIRM_NO = 5;
    private static final int SLOT_CONFIRM_CLOSE = 8;

    private final CatStore store;
    private final CatEntityService entityService;
    private final Lang lang;

    private final Map<UUID, UUID> detailTargets = new HashMap<>();

    public CatDetailGuiManager(
        CatStore store,
        CatEntityService entityService,
        Lang lang
    ) {
        this.store = store;
        this.entityService = entityService;
        this.lang = lang;
    }

    public void openDetail(
        Player admin,
        UUID targetOwnerUuid
    ) {
        detailTargets.put(
            admin.getUniqueId(),
            targetOwnerUuid
        );

        buildDetail(
            admin,
            targetOwnerUuid
        );
    }

    public void openConfirm(
        Player admin,
        UUID targetOwnerUuid
    ) {
        detailTargets.put(
            admin.getUniqueId(),
            targetOwnerUuid
        );

        buildConfirm(
            admin,
            targetOwnerUuid
        );
    }

    public void clearState(UUID playerUuid) {
        if (playerUuid != null) {
            detailTargets.remove(playerUuid);
        }
    }

    public void handleDetailClick(
        Player admin,
        int rawSlot
    ) {
        UUID target =
            detailTargets.get(
                admin.getUniqueId()
            );

        if (rawSlot == SLOT_DELETE) {

            if (target != null) {
                openConfirm(admin, target);
            }

            return;
        }

        if (rawSlot == SLOT_BACK || rawSlot == SLOT_CLOSE) {

            detailTargets.remove(
                admin.getUniqueId()
            );

            admin.closeInventory();
        }
    }

    public void handleConfirmClick(
        Player admin,
        int rawSlot
    ) {

        UUID target =
            detailTargets.get(
                admin.getUniqueId()
            );

        if (target == null) {

            admin.closeInventory();
            return;
        }

        if (rawSlot == SLOT_CONFIRM_YES) {

            String catName =
                store.getCatName(target);

            String ownerName =
                ownerDisplayName(target);

            boolean removed =
                entityService.removePlayerCat(
                    target
                );

            detailTargets.remove(
                admin.getUniqueId()
            );

            admin.closeInventory();

            if (removed) {

                admin.sendMessage(
                    lang.forPlayer(admin)
                        .message(
                            "admin.ranking-delete-done",
                            ownerName,
                            catName
                        )
                );

                Player owner =
                    Bukkit.getPlayer(target);

                if (owner != null && owner.isOnline()) {

                    owner.sendMessage(
                        lang.forPlayer(owner)
                            .message(
                                "admin.ranking-delete-notify",
                                catName
                            )
                    );
                }

                return;
            }

            admin.sendMessage(
                lang.forPlayer(admin)
                    .message(
                        "admin.ranking-delete-failed",
                        ownerName
                    )
            );

            return;
        }

        if (rawSlot == SLOT_CONFIRM_NO) {

            buildDetail(
                admin,
                target
            );

            return;
        }

        if (rawSlot == SLOT_CONFIRM_CLOSE) {

            detailTargets.remove(
                admin.getUniqueId()
            );

            admin.closeInventory();
        }
    }

    private void buildDetail(
        Player admin,
        UUID targetOwnerUuid
    ) {

        /*
         * 目标猫可能在排行打开与点击之间、
         * 或确认面板停留期间被删除——
         * 数据缺失时不渲染幽灵详情。
         */
        if (!store.hasCat(targetOwnerUuid)) {

            detailTargets.remove(
                admin.getUniqueId()
            );

            admin.sendMessage(
                lang.forPlayer(admin).message(
                    "admin.ranking-target-missing"
                )
            );

            admin.closeInventory();

            return;
        }

        GuiHolder holder =
            new GuiHolder(
                Page.CAT_DETAIL,
                admin.getUniqueId()
            );

        Inventory inventory =
            Bukkit.createInventory(
                holder,
                INVENTORY_SIZE,
                lang.forPlayer(admin)
                    .text(
                        "gui.ranking-detail-title"
                    )
            );

        ItemStack border =
            item(
                Material.BLACK_STAINED_GLASS_PANE,
                "§0"
            );

        for (int slot : BORDER_SLOTS) {
            inventory.setItem(slot, border);
        }

        for (int slot : CONTENT_FILLER_SLOTS) {
            inventory.setItem(slot, border);
        }

        String ownerName =
            ownerDisplayName(targetOwnerUuid);

        String catName =
            store.getCatName(targetOwnerUuid);

        UUID catUuid =
            store.getCatUUID(targetOwnerUuid);

        String tier =
            store.getCatTier(targetOwnerUuid);

        CatPersonality personality =
            CatPersonality.fromCatId(catUuid);

        int level =
            store.getCatLevel(targetOwnerUuid);

        int experience =
            store.getCatExperience(targetOwnerUuid);

        int meowRank =
            store.getCatMeowRank(targetOwnerUuid);

        int meowPower =
            store.getCatMeowPower(targetOwnerUuid);

        int hunger =
            store.getCatHunger(targetOwnerUuid);

        int affection =
            store.getCatAffection(targetOwnerUuid);

        int health =
            store.getCatHealth(targetOwnerUuid);

        String behavior =
            store.getCatBehaviorMode(targetOwnerUuid);

        String variant =
            store.getCatVariant(targetOwnerUuid);

        long createdAt =
            store.getCatCreatedAt(targetOwnerUuid);

        List<String> skills =
            store.getCatSkills(targetOwnerUuid);

        inventory.setItem(
            SLOT_HEAD,
            headItem(
                targetOwnerUuid,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-head",
                    ownerName,
                    targetOwnerUuid.toString()
                )
            )
        );

        inventory.setItem(
            SLOT_OWNER,
            item(
                Material.PAPER,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-owner",
                    ownerName
                )
            )
        );

        inventory.setItem(
            SLOT_NAME,
            item(
                Material.NAME_TAG,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-name",
                    catName
                )
            )
        );

        inventory.setItem(
            SLOT_LEVEL,
            item(
                Material.BOOK,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-level",
                    String.valueOf(level),
                    String.valueOf(experience)
                )
            )
        );

        inventory.setItem(
            SLOT_MEOW,
            item(
                Material.DIAMOND,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-meow",
                    String.valueOf(meowRank),
                    String.valueOf(meowPower)
                )
            )
        );

        inventory.setItem(
            SLOT_TIER,
            item(
                Material.NETHER_STAR,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-tier",
                    tierDisplay(
                        admin,
                        tier
                    )
                )
            )
        );

        inventory.setItem(
            SLOT_PERSONALITY,
            item(
                Material.CAKE,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-personality",
                    lang.forPlayer(admin).text(
                        "personality-name."
                            + personality.name()
                            .toLowerCase(
                                java.util.Locale.ROOT
                            )
                    )
                )
            )
        );

        inventory.setItem(
            SLOT_HUNGER,
            item(
                Material.COOKED_BEEF,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-hunger",
                    String.valueOf(hunger)
                )
            )
        );

        inventory.setItem(
            SLOT_AFFECTION,
            item(
                Material.RED_DYE,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-affection",
                    String.valueOf(affection)
                )
            )
        );

        inventory.setItem(
            SLOT_HEALTH,
            item(
                Material.GOLDEN_APPLE,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-health",
                    String.valueOf(health)
                )
            )
        );

        inventory.setItem(
            SLOT_BEHAVIOR,
            item(
                Material.BONE,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-behavior",
                    behaviorName(admin, behavior)
                )
            )
        );

        inventory.setItem(
            SLOT_VARIANT,
            item(
                Material.OAK_SAPLING,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-variant",
                    variant == null
                        ? "?"
                        : variant
                )
            )
        );

        inventory.setItem(
            SLOT_CREATED,
            item(
                Material.CLOCK,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-created",
                    formatCreatedAt(createdAt)
                )
            )
        );

        inventory.setItem(
            SLOT_SKILLS_HEADER,
            item(
                Material.ENCHANTED_BOOK,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-skills-header"
                )
            )
        );

        for (int i = 0; i < SKILL_SLOTS.length; i++) {

            if (i < skills.size()) {

                inventory.setItem(
                    SKILL_SLOTS[i],
                    skillItem(
                        admin,
                        skills.get(i)
                    )
                );

            } else {

                inventory.setItem(
                    SKILL_SLOTS[i],
                    item(
                        Material.GRAY_STAINED_GLASS_PANE,
                        lang.forPlayer(admin).text(
                            "gui.ranking-detail-skill-locked"
                        )
                    )
                );
            }
        }

        inventory.setItem(
            SLOT_DELETE,
            item(
                Material.BARRIER,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-delete"
                ),
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-delete-lore",
                    ownerName,
                    catName
                )
            )
        );

        inventory.setItem(
            SLOT_BACK,
            item(
                Material.ARROW,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-back"
                )
            )
        );

        inventory.setItem(
            SLOT_CLOSE,
            item(
                Material.BARRIER,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-close"
                )
            )
        );

        admin.openInventory(inventory);
    }

    private void buildConfirm(
        Player admin,
        UUID targetOwnerUuid
    ) {

        String ownerName =
            ownerDisplayName(targetOwnerUuid);

        String catName =
            store.getCatName(targetOwnerUuid);

        GuiHolder holder =
            new GuiHolder(
                Page.CAT_DELETE_CONFIRM,
                admin.getUniqueId()
            );

        Inventory inventory =
            Bukkit.createInventory(
                holder,
                CONFIRM_SIZE,
                lang.forPlayer(admin).text(
                    "gui.ranking-confirm-title"
                )
            );

        ItemStack border =
            item(
                Material.BLACK_STAINED_GLASS_PANE,
                "§0"
            );

        for (int slot = 0; slot < CONFIRM_SIZE; slot++) {
            inventory.setItem(slot, border);
        }

        inventory.setItem(
            SLOT_CONFIRM_QUESTION,
            item(
                Material.PAPER,
                lang.forPlayer(admin).text(
                    "gui.ranking-confirm-question",
                    ownerName,
                    catName
                ),
                lang.forPlayer(admin).text(
                    "gui.ranking-confirm-warning"
                )
            )
        );

        inventory.setItem(
            SLOT_CONFIRM_YES,
            item(
                Material.RED_WOOL,
                lang.forPlayer(admin).text(
                    "gui.ranking-confirm-yes"
                )
            )
        );

        inventory.setItem(
            SLOT_CONFIRM_NO,
            item(
                Material.GREEN_WOOL,
                lang.forPlayer(admin).text(
                    "gui.ranking-confirm-no"
                )
            )
        );

        inventory.setItem(
            SLOT_CONFIRM_CLOSE,
            item(
                Material.BARRIER,
                lang.forPlayer(admin).text(
                    "gui.ranking-detail-close"
                )
            )
        );

        admin.openInventory(inventory);
    }

    private ItemStack headItem(
        UUID ownerUuid,
        String displayName
    ) {

        ItemStack head =
            new ItemStack(
                Material.PLAYER_HEAD,
                1
            );

        SkullMeta meta =
            (SkullMeta) head.getItemMeta();

        if (meta != null) {

            /*
             * 0.8.5 R4（实机日志第三轮）：同 RankingGuiManager——
             * setOwningPlayer 会经 getPlayerProfile() 调度异步补全，
             * 改用 createProfile + setOwnerProfile（零网络，Steve 占位）。
             */
            meta.setOwnerProfile(
                Bukkit.createProfile(
                    ownerUuid
                )
            );

            if (displayName != null) {
                meta.setDisplayName(displayName);
            }

            head.setItemMeta(meta);
        }

        return head;
    }

    private ItemStack skillItem(
        Player admin,
        String skillName
    ) {

        CatSkill skill =
            skillFromName(skillName);

        if (skill == null) {

            return item(
                Material.BOOK,
                "§f" + skillName
            );
        }

        String display =
            lang.forPlayer(admin).text(
                "skill-name."
                    + skill.name()
                    .toLowerCase(
                        java.util.Locale.ROOT
                    )
            );

        return item(
            skill.getIcon(),
            "§f" + display,
            lang.forPlayer(admin).text(
                "gui.ranking-detail-skill-tag",
                display
            )
        );
    }

    private String tierDisplay(
        Player admin,
        String tier
    ) {

        if (tier == null || tier.isBlank()) {
            return "?";
        }

        String key =
            "tier-name."
                + tier.toLowerCase(
                    java.util.Locale.ROOT
                );

        String translated =
            lang.forPlayer(admin)
                .text(key);

        return key.equals(translated)
            ? tier
            : translated;
    }

    private CatSkill skillFromName(String skillName) {

        if (skillName == null || skillName.isBlank()) {
            return null;
        }

        try {

            return CatSkill.valueOf(
                skillName
            );

        } catch (IllegalArgumentException e) {

            return null;
        }
    }

    private String behaviorName(
        Player admin,
        String behavior
    ) {

        if (behavior == null || behavior.isBlank()) {
            return "?";
        }

        String key =
            "gui.mode-"
                + behavior.toLowerCase(
                    java.util.Locale.ROOT
                );

        String translated =
            lang.forPlayer(admin)
                .text(key);

        return key.equals(translated)
            ? behavior
            : translated;
    }

    private String ownerDisplayName(
        UUID ownerUuid
    ) {

        String name =
            Bukkit.getOfflinePlayer(ownerUuid)
                .getName();

        if (name == null || name.isBlank()) {

            return ownerUuid
                .toString()
                .substring(0, 8);
        }

        return name;
    }

    private String formatCreatedAt(
        long createdAt
    ) {

        if (createdAt <= 0L) {
            return "?";
        }

        SimpleDateFormat format =
            new SimpleDateFormat("yyyy-MM-dd");

        format.setTimeZone(
            java.util.TimeZone.getTimeZone("UTC")
        );

        return format.format(
            new Date(createdAt)
        );
    }

    private ItemStack item(
        Material material,
        String displayName,
        String... lore
    ) {

        ItemStack stack =
            new ItemStack(material, 1);

        var meta = stack.getItemMeta();

        if (meta != null) {

            meta.setDisplayName(displayName);

            if (lore.length > 0) {
                meta.setLore(
                    new ArrayList<>(
                        List.of(lore)
                    )
                );
            }

            stack.setItemMeta(meta);
        }

        return stack;
    }
}
