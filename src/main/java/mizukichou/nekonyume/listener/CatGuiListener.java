package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.achievement.AchievementGuiHolder;
import mizukichou.nekonyume.achievement.AchievementGuiManager;
import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.cat.CatProgressionService;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.gui.CatGuiHolder;
import mizukichou.nekonyume.gui.CatGuiManager;
import mizukichou.nekonyume.gui.EquipGuiHolder;
import mizukichou.nekonyume.gui.EquipGuiManager;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.skill.CatSkillManager;
import mizukichou.nekonyume.skill.SkillGuiHolder;
import mizukichou.nekonyume.skill.SkillGuiManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * GUI 点击处理。
 *
 * <p>
 * 0.7.0：玩家文案改走 Lang（gui-click.* 节）。
 * </p>
 */
public class CatGuiListener implements Listener {

    /*
     * plugin 仅用于调度器（延迟重开面板）。
     */
    private final JavaPlugin plugin;

    private final CatEntityService entityService;
    private final CatGuiManager guiManager;
    private final SkillGuiManager skillGuiManager;
    private final EquipGuiManager equipGuiManager;
    private final AchievementGuiManager achievementGuiManager;
    private final CatCache cache;
    private final CatProgressionService progression;
    private final CatSkillManager skillManager;
    private final Lang lang;

    public CatGuiListener(
            JavaPlugin plugin,
            CatEntityService entityService,
            CatGuiManager guiManager,
            SkillGuiManager skillGuiManager,
            EquipGuiManager equipGuiManager,
            AchievementGuiManager achievementGuiManager,
            CatCache cache,
            CatProgressionService progression,
            CatSkillManager skillManager,
            Lang lang
    ) {

        this.plugin = plugin;
        this.entityService = entityService;
        this.guiManager = guiManager;
        this.skillGuiManager = skillGuiManager;
        this.equipGuiManager = equipGuiManager;
        this.achievementGuiManager = achievementGuiManager;
        this.cache = cache;
        this.progression = progression;
        this.skillManager = skillManager;
        this.lang = lang;
    }

    @EventHandler
    public void onInventoryClick(
            InventoryClickEvent event
    ) {

        /*
         * 技能面板。
         */
        if (event.getInventory()
                .getHolder()
                instanceof SkillGuiHolder skillHolder) {

            handleSkillGuiClick(
                    event,
                    skillHolder
            );

            return;
        }

        /*
         * 成就殿堂面板（只读，仅关闭按钮可用）。
         */
        if (event.getInventory()
                .getHolder()
                instanceof AchievementGuiHolder achievementHolder) {

            event.setCancelled(
                    true
            );

            if (!event.getWhoClicked()
                    .getUniqueId()
                    .equals(achievementHolder.getOwnerUuid())) {

                return;
            }

            if (event.getRawSlot() == 8) {

                event.getWhoClicked()
                        .closeInventory();
            }

            return;
        }

        /*
         * 装备界面。
         */
        if (event.getInventory()
                .getHolder()
                instanceof EquipGuiHolder equipHolder) {

            event.setCancelled(
                    true
            );

            if (!event.getWhoClicked()
                    .getUniqueId()
                    .equals(equipHolder.getOwnerUuid())) {

                return;
            }

            Player equipPlayer =
                    (Player) event.getWhoClicked();

            if (equipGuiManager.handleClick(
                    equipPlayer,
                    event.getRawSlot())) {

                /*
                 * 卸下成功后延迟重开面板，
                 * 避免在点击事件内直接操作背包。
                 */
                plugin.getServer()
                        .getScheduler()
                        .runTask(
                                plugin,
                                () -> {

                                    if (equipPlayer.isOnline()) {

                                        equipGuiManager.open(
                                                equipPlayer
                                        );
                                    }
                                }
                        );
            }

            /*
             * 0.8.0：背包快捷穿戴——装备位为空时，
             * 点击背包中的装备直接穿上。
             *
             * 仅处理空光标点击（避免与"放置光标物品"
             * 的意图冲突）；完成后同步槽位并重开面板。
             */
            if (event.getRawSlot()
                    >= event.getView()
                    .getTopInventory()
                    .getSize() &&
                    (event.getCursor() == null ||
                            event.getCursor()
                                    .getType()
                                    .isAir())) {

                ItemStack bottomItem =
                        event.getCurrentItem();

                if (equipGuiManager.equipFromInventory(
                        equipPlayer,
                        bottomItem
                )) {

                    event.setCurrentItem(
                            bottomItem != null &&
                                    bottomItem.getAmount() <= 0
                                    ? null
                                    : bottomItem
                    );

                    plugin.getServer()
                            .getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {

                                        if (equipPlayer.isOnline()) {

                                            equipGuiManager.open(
                                                    equipPlayer
                                            );
                                        }
                                    }
                            );
                }
            }

            return;
        }

        /*
         * 状态面板。
         */
        if (!(event.getInventory()
                .getHolder()
                instanceof CatGuiHolder holder)) {

            return;
        }

        /*
         * 面板内所有点击一律取消，
         * 物品不可被取出 / 移动。
         */
        event.setCancelled(true);

        /*
         * 只有面板主人能操作。
         */
        if (!event.getWhoClicked()
                .getUniqueId()
                .equals(holder.getOwnerUuid())) {

            return;
        }

        Player player =
                (Player) event.getWhoClicked();

        ItemStack clicked =
                event.getCurrentItem();

        if (clicked == null ||
                clicked.getType().isAir()) {

            return;
        }

        int slot =
                event.getRawSlot();

        CatBehaviorMode mode;

        switch (slot) {

            case 13 -> {

                /*
                 * 点击猫头 → 进入技能界面。
                 * （事件已在方法开头取消，无需额外处理）
                 */
                skillGuiManager.open(
                        player
                );

                return;
            }

            case 21 ->
                    mode = CatBehaviorMode.FOLLOW;

            case 22 ->
                    mode = CatBehaviorMode.SIT;

            case 23 ->
                    mode = CatBehaviorMode.FREE;

            case 39 -> {

                /*
                 * 技能界面入口。
                 */
                skillGuiManager.open(
                        player
                );

                return;
            }

            case 40 -> {

                /*
                 * 装备界面入口（0.8.0）。
                 */
                equipGuiManager.open(
                        player
                );

                return;
            }

            case 41 -> {

                /*
                 * 成就殿堂入口。
                 */
                achievementGuiManager.open(
                        player
                );

                return;
            }

            case 49 -> {

                player.closeInventory();
                return;
            }

            default -> {
                return;
            }
        }

        /*
         * 切换模式并刷新面板。
         */
        entityService.setCatBehaviorMode(
                player,
                mode
        );

        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {

                            if (player.isOnline()) {

                                guiManager.open(
                                        player
                                );
                            }
                        }
                );
    }

    /*
     * ============================================================
     * 技能面板点击
     * ============================================================
     *
     * 左键：施放主动技能
     * 右键：刷新技能槽
     */

    private void handleSkillGuiClick(
            InventoryClickEvent event,
            SkillGuiHolder holder
    ) {

        event.setCancelled(true);

        if (!event.getWhoClicked()
                .getUniqueId()
                .equals(holder.getOwnerUuid())) {

            return;
        }

        Player player =
                (Player) event.getWhoClicked();

        ItemStack clicked =
                event.getCurrentItem();

        if (clicked == null ||
                clicked.getType().isAir()) {

            return;
        }

        int slot =
                event.getRawSlot();

        if (slot == 8) {

            player.closeInventory();
            return;
        }

        /*
         * 槽位区：18 ~ 27。
         */
        if (slot < 18 ||
                slot > 27) {

            return;
        }

        int index =
                slot - 18;

        if (event.getClick()
                .isRightClick()) {

            progression.refreshSkillSlot(
                    player,
                    index
            );

            reopenSkillGui(
                    player
            );

            return;
        }

        /*
         * 左键：施放主动技能。
         */
        Cat cat =
                cache.loadCat(
                        player
                );

        if (cat == null) {
            return;
        }

        List<CatSkill> skills =
                cat.getSkills();

        if (index >= skills.size()) {
            return;
        }

        CatSkill skill =
                skills.get(index);

        if (skill.isActive()) {

            skillManager.activateSkill(
                    player,
                    skill
            );

            reopenSkillGui(
                    player
            );

        } else {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "gui.click-passive",
                            lang.forPlayer(player).text(
                                    "skill-name."
                                            + skill.name()
                                            .toLowerCase(
                                                    java.util.Locale.ROOT
                                            )
                            )
                    )
            );
        }
    }

    private void reopenSkillGui(
            Player player
    ) {

        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {

                            if (player.isOnline()) {

                                skillGuiManager.open(
                                        player
                                );
                            }
                        }
                );
    }

    /*
     * 拖拽拦截（0.8.0）：所有本插件面板（猫/技能/装备/成就）
     * 均取消拖拽，防止展示物品被拖出或槽位被拖入污染。
     */
    @EventHandler
    public void onInventoryDrag(
            InventoryDragEvent event
    ) {

        Object holder =
                event.getView()
                        .getTopInventory()
                        .getHolder();

        if (holder instanceof CatGuiHolder ||
                holder instanceof SkillGuiHolder ||
                holder instanceof EquipGuiHolder ||
                holder instanceof AchievementGuiHolder) {

            /*
             * 只拦涉及面板顶部的拖拽；
             * 纯背包内拖拽整理保持原版体验。
             */
            int topSize =
                    event.getView()
                            .getTopInventory()
                            .getSize();

            for (int rawSlot :
                    event.getRawSlots()) {

                if (rawSlot < topSize) {

                    event.setCancelled(
                            true
                    );

                    return;
                }
            }
        }
    }
}
