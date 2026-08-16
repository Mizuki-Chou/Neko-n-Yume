package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.cat.CatProgressionService;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.gui.CatGuiHolder;
import mizukichou.nekonyume.gui.CatGuiManager;
import mizukichou.nekonyume.skill.CatSkillManager;
import mizukichou.nekonyume.skill.SkillGuiHolder;
import mizukichou.nekonyume.skill.SkillGuiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class CatGuiListener implements Listener {

    /*
     * plugin 仅用于调度器（延迟重开面板）。
     */
    private final JavaPlugin plugin;

    private final CatEntityService entityService;
    private final CatGuiManager guiManager;
    private final SkillGuiManager skillGuiManager;
    private final CatCache cache;
    private final CatProgressionService progression;
    private final CatSkillManager skillManager;

    private final MiniMessage mm =
            MiniMessage.miniMessage();

    public CatGuiListener(
            JavaPlugin plugin,
            CatEntityService entityService,
            CatGuiManager guiManager,
            SkillGuiManager skillGuiManager,
            CatCache cache,
            CatProgressionService progression,
            CatSkillManager skillManager
    ) {

        this.plugin = plugin;
        this.entityService = entityService;
        this.guiManager = guiManager;
        this.skillGuiManager = skillGuiManager;
        this.cache = cache;
        this.progression = progression;
        this.skillManager = skillManager;
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

            case 18 ->
                    mode = CatBehaviorMode.FOLLOW;

            case 19 ->
                    mode = CatBehaviorMode.SIT;

            case 20 ->
                    mode = CatBehaviorMode.FREE;

            case 26 -> {

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
                        () -> guiManager.open(
                                player
                        )
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
                    mm.deserialize(
                            "<yellow>「</yellow>"
                    ).append(
                            Component.text(
                                    skill.getDisplayName()
                            )
                    ).append(
                            mm.deserialize(
                                    "<yellow>」是被动技能，会自动生效。</yellow>"
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
                        () -> skillGuiManager.open(
                                player
                        )
                );
    }
}
