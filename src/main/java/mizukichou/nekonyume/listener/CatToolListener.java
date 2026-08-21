package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.achievement.AchievementService;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.gui.CatGuiManager;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

/**
 * 快捷工具（逗猫棒）监听器。
 *
 * <p>
 * 持有 /nekoyume tool 发放的逗猫棒：
 * 右键空气 / 方块 / 任意实体 → 打开猫咪面板。
 * </p>
 *
 * <p>
 * 若玩家还没有猫咪（例如数据被移除但逗猫棒还在）：
 * 自动领取一只 + title 大字提示 + 赠送玩家向入门书。
 * </p>
 *
 * <p>
 * 0.7.0：title 与入门书文案改走 Lang（tool.* 与 book.* 节）。
 * </p>
 */
public class CatToolListener implements Listener {

    private final CatGuiManager guiManager;
    private final CatStore store;
    private final CatEntityService entityService;
    private final AchievementService achievementService;
    private final Lang lang;

    private final NamespacedKey toolKey;

    public CatToolListener(
            CatGuiManager guiManager,
            CatStore store,
            CatEntityService entityService,
            AchievementService achievementService,
            NamespacedKey toolKey,
            Lang lang
    ) {

        this.guiManager = guiManager;
        this.store = store;
        this.entityService = entityService;
        this.achievementService = achievementService;
        this.toolKey = toolKey;
        this.lang = lang;
    }

    @EventHandler
    public void onInteract(
            PlayerInteractEvent event
    ) {

        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {

            return;
        }

        /*
         * 只处理主手：
         * 避免双持两根逗猫棒时开两次面板。
         */
        if (event.getHand()
                != EquipmentSlot.HAND) {

            return;
        }

        if (!isTool(
                event.getItem()
        )) {

            return;
        }

        event.setCancelled(true);

        openPanel(
                event.getPlayer()
        );
    }

    @EventHandler
    public void onInteractEntity(
            PlayerInteractAtEntityEvent event
    ) {

        /*
         * 只处理主手。
         */
        if (event.getHand()
                != EquipmentSlot.HAND) {

            return;
        }

        ItemStack item =
                event.getPlayer()
                        .getInventory()
                        .getItemInMainHand();

        if (!isTool(item)) {
            return;
        }

        event.setCancelled(true);

        openPanel(
                event.getPlayer()
        );
    }

    private boolean isTool(
            ItemStack item
    ) {

        if (item == null ||
                !item.hasItemMeta()) {

            return false;
        }

        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(
                        toolKey,
                        PersistentDataType.BYTE
                );
    }

    private void openPanel(
            Player player
    ) {

        /*
         * 还没有猫：自动领取一只
         * （等价于 /nekoyume claim 的数据创建 + 实体召唤），
         * title 大字提示 + 赠送入门书。
         */
        if (!store.hasCat(
                player.getUniqueId()
        )) {

            store.createCat(
                    player.getUniqueId()
            );

            /*
             * 成就：领取动作立即判定「相遇即是缘」
             * （与 /nekoyume claim 路径一致）。
             */
            achievementService.checkAll(
                    player
            );

            String name =
                    store.getCatName(
                            player.getUniqueId()
                    );

            entityService.spawnCat(
                    player,
                    name,
                    summoned -> {
                        /*
                         * 实体生成结果无需额外处理：
                         * title 已给出领取反馈，
                         * 面板随后立即打开。
                         */
                    }
            );

            /*
             * title：主标题 + 副标题。
             * 时长参数：淡入10 / 停留60 / 淡出10（tick）。
             */
            player.sendTitle(
                    lang.forPlayer(player).text(
                            "tool.claim-title"
                    ),
                    lang.forPlayer(player).text(
                            "tool.claim-subtitle"
                    ),
                    10,
                    60,
                    10
            );

            /*
             * 入门书：给玩家看，不是给技术人员看。
             */
            giveTutorialBook(
                    player
            );
        }

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_CAT_AMBIENT,
                1.0f,
                1.4f
        );

        guiManager.open(
                player
        );
    }

    /*
     * ============================================================
     * 玩家入门书
     * ============================================================
     *
     * 文字面向玩家：活泼、口语化、零技术词汇。
     * 背包放不下时自动掉落在脚边。
     * 页面内容来自 lang/zh_cn.yml 的 book.page-1 ~ 8。
     */

    private void giveTutorialBook(
            Player player
    ) {

        ItemStack book =
                new ItemStack(
                        Material.WRITTEN_BOOK,
                        1
                );

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        if (meta == null) {
            return;
        }

        meta.setTitle(
                lang.forPlayer(player).text(
                        "book.title"
                )
        );

        meta.setAuthor(
                lang.forPlayer(player).text(
                        "book.author"
                )
        );

        List<String> pages =
                new java.util.ArrayList<>();

        for (int page = 1;
             page <= 8;
             page++) {

            pages.add(
                    lang.forPlayer(player).text(
                            "book.page-" + page
                    )
            );
        }

        meta.setPages(
                pages
        );

        book.setItemMeta(
                meta
        );

        /*
         * 发放：背包满则掉落脚边。
         */
        Map<Integer, ItemStack> leftover =
                player.getInventory()
                        .addItem(
                                book
                        );

        for (ItemStack left :
                leftover.values()) {

            player.getWorld()
                    .dropItemNaturally(
                            player.getLocation(),
                            left
                    );
        }
    }
}
