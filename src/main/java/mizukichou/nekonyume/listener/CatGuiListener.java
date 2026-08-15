package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.NekoNYume;
import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.gui.CatGuiHolder;
import mizukichou.nekonyume.skill.SkillGuiHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CatGuiListener implements Listener {

    private final NekoNYume plugin;

    private final MiniMessage mm =
            MiniMessage.miniMessage();

    public CatGuiListener(
            NekoNYume plugin
    ) {

        this.plugin = plugin;
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
        plugin.getCatManager()
                .setCatBehaviorMode(
                        player,
                        mode
                );

        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> plugin.getCatGuiManager()
                                .open(
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

            plugin.getCatManager()
                    .refreshSkillSlot(
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
                plugin.getCatManager()
                        .loadCat(
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

            plugin.getCatSkillManager()
                    .activateSkill(
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
                        () -> plugin.getSkillGuiManager()
                                .open(
                                        player
                                )
                );
    }
}
