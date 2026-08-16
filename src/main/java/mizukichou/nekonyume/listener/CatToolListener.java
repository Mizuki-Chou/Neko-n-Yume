package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.gui.CatGuiManager;
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
 */
public class CatToolListener implements Listener {

    private final CatGuiManager guiManager;
    private final CatStore store;
    private final CatEntityService entityService;

    private final NamespacedKey toolKey;

    public CatToolListener(
            CatGuiManager guiManager,
            CatStore store,
            CatEntityService entityService,
            NamespacedKey toolKey
    ) {

        this.guiManager = guiManager;
        this.store = store;
        this.entityService = entityService;
        this.toolKey = toolKey;
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
                    "§d🎉 你获得了猫咪!",
                    "§7§o欢迎来到 猫と夢 · Neko n' Yume",
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
                "猫と夢 · 入门指南"
        );

        meta.setAuthor(
                "Neko n' Yume"
        );

        /*
         * 排版规范：
         * - 每一行都以显式颜色码开头（现代成书默认文字色为白色，
         *   在浅色/白色书页上完全不可读）；
         * - 正文 §0 纯黑、注释 §8 深灰、标题 §l§1 深蓝加粗、
         *   关键名词 §l§6 金色加粗、命令 §2 深绿、警示 §4 深红；
         * - 每页不超过 10 行，保证无需翻页滚动即可读完。
         */
        meta.setPages(
                List.of(

                        /*
                         * 第 1 页：欢迎
                         */
                        "§l§1🐱 欢迎来到 猫と夢!\n"
                                + "\n"
                                + "§0当你翻开这本书，\n"
                                + "§0一只毛茸茸的小生命\n"
                                + "§0已经悄悄来到你身边。\n"
                                + "\n"
                                + "§0它叫 §l§6Mikan§r§0，\n"
                                + "§0从今天起，它会一直陪着你。",

                        /*
                         * 第 2 页：它是谁
                         */
                        "§l§1✨ 它是谁?\n"
                                + "\n"
                                + "§0你的猫拥有：\n"
                                + "§2◆ §0名字 §8- 可以随时改\n"
                                + "§2◆ §0性格 §8- 独一无二\n"
                                + "§2◆ §0底蕴 §8- 决定天赋\n"
                                + "\n"
                                + "§8每只猫都只有一个，\n"
                                + "§8好好珍惜它吧。",

                        /*
                         * 第 3 页：日常互动
                         */
                        "§l§1🍖 每天都能做什么?\n"
                                + "\n"
                                + "§2✔ §0喂食§8：拿鱼肉右键它\n"
                                + "§2✔ §0抚摸§8：靠近后按潜行\n"
                                + "§2✔ §0喵丹§8：珍贵的成长道具\n"
                                + "\n"
                                + "§6每天前3次互动\n"
                                + "§6奖励最丰厚哦!",

                        /*
                         * 第 4 页：成长
                         */
                        "§l§1🌱 它会长大!\n"
                                + "\n"
                                + "§6经验§0 → 提升等级\n"
                                + "§5喵力§0 → 提升喵阶\n"
                                + "\n"
                                + "§8喂食、抚摸都有机会\n"
                                + "§8获得喵力。等级和喵阶\n"
                                + "§8越高，它就越强!",

                        /*
                         * 第 5 页：技能
                         */
                        "§l§1📖 技能面板\n"
                                + "\n"
                                + "§0打开面板点猫头，\n"
                                + "§0或输入：\n"
                                + "§2/nekoyume skill\n"
                                + "\n"
                                + "§8每个技能都有独特效果，\n"
                                + "§8右键槽位可以刷新技能。",

                        /*
                         * 第 6 页：战斗
                         */
                        "§l§1⚔ 它会保护你!\n"
                                + "\n"
                                + "§0跟随模式下，猫会主动\n"
                                + "§0迎击附近怪物；\n"
                                + "§0你攻击谁，它就帮你打谁。\n"
                                + "\n"
                                + "§4它不会真正死去，\n"
                                + "§4受伤后会休息120秒\n"
                                + "§4满血归来。",

                        /*
                         * 第 7 页：常用命令
                         */
                        "§l§1🎮 常用命令\n"
                                + "\n"
                                + "§2/nekoyume §0查看所有命令\n"
                                + "§2/nekoyume summon §0召唤猫\n"
                                + "§2/nekoyume mode §0切换模式\n"
                                + "§2/nekoyume gui §0打开面板",

                        /*
                         * 第 8 页：结尾
                         */
                        "§l§1🌙 那么——\n"
                                + "\n"
                                + "§0和你的猫咪一起，\n"
                                + "§0开始这场奇妙的旅程吧。\n"
                                + "\n"
                                + "§8—— 猫と夢\n"
                                + "§8Neko n' Yume"
                )
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