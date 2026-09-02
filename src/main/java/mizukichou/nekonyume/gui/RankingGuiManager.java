package mizukichou.nekonyume.gui;

import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.ranking.CatRankEntry;
import mizukichou.nekonyume.ranking.CatRanking;
import mizukichou.nekonyume.ranking.CatRankingService;
import mizukichou.nekonyume.ranking.SortMode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 全服猫咪排行面板（0.8.5）。
 *
 * <p>
 * 布局（54 格）：
 * 外圈一圈玻璃边框；
 * 中间 4×7 =28 个猫咪格子（图标 = 主人头颅）；
 * 底部：上一页 / 排序切换 / 下一页 / 关闭。
 * 排行数据来自 {@link CatRankingService}（Splay 树分页）。
 * </p>
 */
public final class RankingGuiManager {

    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 28;

    private static final int SLOT_PREV = 46;
    private static final int SLOT_SORT = 49;
    private static final int SLOT_NEXT = 52;
    private static final int SLOT_CLOSE = 53;
    private static final int SLOT_EMPTY_HINT = 31;

    /*
     * 外圈玻璃（行 0、行 5、列 0、列 8），
     * 剔除四个控制按钮占位。
     */
    private static final int[] BORDER_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 35,
            36, 44,
            45, 47, 48, 50, 51
    };

    /*
     * 内容格：行 1-4 × 列 1-7。
     */
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final CatRankingService rankingService;
    private final CatDetailGuiManager detailGuiManager;
    private final Lang lang;

    /*
     * 每玩家面板状态（排序模式 + 页码）。
     * 主线程访问。
     */
    private final Map<UUID, State> states =
            new HashMap<>();

    public RankingGuiManager(
            CatRankingService rankingService,
            CatDetailGuiManager detailGuiManager,
            Lang lang
    ) {

        this.rankingService = rankingService;
        this.detailGuiManager = detailGuiManager;
        this.lang = lang;
    }

    /**
     * 打开（或刷新）排行面板。
     */
    /**
     * 打开（或刷新）排行面板。
     */
    public void open(
            Player player
    ) {

        open(
                player,
                false
        );
    }

    /**
     * 打开（或刷新）排行面板。
     *
     * @param adminMode true=管理员模式：
     *                   左键猫咪格子进入该猫详情面板（可强制删除）。
     */
    public void open(
            Player player,
            boolean adminMode
    ) {

        State state = states.computeIfAbsent(
                player.getUniqueId(),
                uuid -> new State()
        );

        state.adminMode = adminMode;

        openPage(
                player,
                state
        );
    }

    /**
     * 玩家退出时清理其面板状态（排序模式 / 页码）。
     */
    public void clearState(
            UUID playerUuid
    ) {

        if (playerUuid != null) {

            states.remove(
                    playerUuid
            );
        }
    }

    /**
     * 点击处理。
     * 调用方负责 setCancelled 与身份校验。
     */
    public void handleClick(
            Player player,
            int rawSlot,
            boolean leftClick
    ) {

        State state = states.get(
                player.getUniqueId()
        );

        if (state == null) {
            return;
        }

        if (rawSlot == SLOT_PREV) {

            state.pageIndex =
                    Math.max(
                            0,
                            state.pageIndex - 1
                    );

            openPage(
                    player,
                    state
            );

        } else if (rawSlot == SLOT_NEXT) {

            state.pageIndex++;

            openPage(
                    player,
                    state
            );

        } else if (rawSlot == SLOT_SORT) {

            state.mode =
                    state.mode.toggle();

            state.pageIndex = 0;

            openPage(
                    player,
                    state
            );

        } else if (rawSlot == SLOT_CLOSE) {

            player.closeInventory();
            states.remove(
                    player.getUniqueId()
            );

        } else if (leftClick && state.adminMode) {

            /*
             * 管理员模式：左键猫咪格子进入该猫详情面板。
             */
            CatRankEntry entry =
                    contentEntry(
                            state,
                            rawSlot
                    );

            if (entry != null) {

                detailGuiManager.openDetail(
                        player,
                        entry.ownerUuid()
                );
            }
        }
    }

    private CatRankEntry contentEntry(
            State state,
            int rawSlot
    ) {

        int index = -1;

        for (int i = 0; i < CONTENT_SLOTS.length; i++) {

            if (CONTENT_SLOTS[i] == rawSlot) {

                index = i;
                break;
            }
        }

        if (index < 0) {
            return null;
        }

        CatRanking ranking =
                rankingService.buildRanking(
                        state.mode,
                        this::ownerDisplayName
                );

        List<CatRankEntry> page =
                ranking.page(
                        state.pageIndex,
                        PAGE_SIZE
                );

        if (index >= page.size()) {
            return null;
        }

        return page.get(index);
    }

    /**
     * 组装并打开一页。
     */
    private void openPage(
            Player player,
            State state
    ) {

        CatRanking ranking =
                rankingService.buildRanking(
                        state.mode,
                        this::ownerDisplayName
                );

        int total =
                ranking.total();

        int maxPageIndex =
                total == 0
                ? 0 : (total - 1) / PAGE_SIZE;

        state.pageIndex = Math.min(
                Math.max(
                        0,
                        state.pageIndex
                ),
                maxPageIndex
        );

        GuiHolder holder =
                new GuiHolder(
                        Page.RANKING,
                        player.getUniqueId()
                );

        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        INVENTORY_SIZE,
                        lang.forPlayer(player).text(
                                "gui.ranking-title"
                        )
                );

        ItemStack border =
                item(
                        Material.BLACK_STAINED_GLASS_PANE,
                        "§0"
                );

        for (int slot : BORDER_SLOTS) {
            inventory.setItem(
                    slot,
                    border
            );
        }

        inventory.setItem(
                SLOT_PREV,
                item(
                        Material.ARROW,
                        lang.forPlayer(player).text(
                                "gui.ranking-prev"
                        )
                )
        );

        inventory.setItem(
                SLOT_SORT,
                sortButton(
                        player,
                        state,
                        total
                )
        );

        inventory.setItem(
                SLOT_NEXT,
                item(
                        Material.ARROW,
                        lang.forPlayer(player).text(
                                "gui.ranking-next"
                        )
                )
        );

        inventory.setItem(
                SLOT_CLOSE,
                item(
                        Material.BARRIER,
                        lang.forPlayer(player).text(
                                "gui.ranking-close"
                        )
                )
        );

        if (total == 0) {

            inventory.setItem(
                    SLOT_EMPTY_HINT,
                    item(
                            Material.NAME_TAG,
                            lang.forPlayer(player).text(
                                    "gui.ranking-empty"
                            )
                    )
            );

            player.openInventory(
                    inventory
            );
            return;
        }

        List<CatRankEntry> page =
                ranking.page(
                        state.pageIndex,
                        PAGE_SIZE
                );

        int rankBase =
                state.pageIndex * PAGE_SIZE;

        for (int i = 0;
             i < page.size();
             i++) {

            inventory.setItem(
                    CONTENT_SLOTS[i],
                    headItem(
                            player,
                            rankBase + i + 1,
                            page.get(i)
                    )
            );
        }

        player.openInventory(
                inventory
        );
    }

    /**
     * 猫咪格子：主人头颅 + 排名/主人/喵阶/等级四行 lore。
     */
    private ItemStack headItem(
            Player player,
            int rank,
            CatRankEntry entry
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
             * 0.8.5 R4（实机日志第三轮）：
             * setOwningPlayer 内部会先取 OfflinePlayer#getPlayerProfile()，
             * 该调用本身就会调度异步补全（getUpdatedProfile → Mojang HTTP），
             * 28 格排行依旧会打满 429 限流。
             * 真正零网络：createProfile 只造“含 uuid 的空档案”
             * （无属性、无补全调度），经 setOwnerProfile 原样写入头颅。
             * 代价：无皮肤（渲染 Steve）——排行头颅仅作身份标识，
             * 显示名与 lore 已足够区分玩家。
             */
            meta.setOwnerProfile(
                    Bukkit.createProfile(
                            entry.ownerUuid()
                    )
            );

meta.setDisplayName(
                    lang.forPlayer(player).text(
                            "gui.ranking-head",
                            entry.ownerName(),
                            entry.catName()
                    )
            );

            meta.setLore(
                    List.of(
                            lang.forPlayer(player).text(
                                    "gui.ranking-lore-rank",
                                    String.valueOf(rank)
                            ),
                            lang.forPlayer(player).text(
                                    "gui.ranking-lore-owner",
                                    entry.ownerName()
                            ),
                            lang.forPlayer(player).text(
                                    "gui.ranking-lore-meow",
                                    String.valueOf(
                                            entry.meowRank()
                                    ),
                                    String.valueOf(
                                            entry.meowPower()
                                    )
                            ),
                            lang.forPlayer(player).text(
                                    "gui.ranking-lore-level",
                                    String.valueOf(
                                            entry.level()
                                    ),
                                    String.valueOf(
                                            entry.experience()
                                    )
                            )
                    )
            );

            head.setItemMeta(
                    meta
            );
        }

        return head;
    }

    /**
     * 排序切换按钮（含当前页信息）。
     */
    private ItemStack sortButton(
            Player player,
            State state,
            int total
    ) {

        String modeName =
                lang.forPlayer(player).text(
                        state.mode == SortMode.MEOW_RANK
                        ? "gui.ranking-mode-meow"
                        : "gui.ranking-mode-level"
                );

        int maxPageIndex =
                total == 0
                ? 0 : (total - 1) / PAGE_SIZE;

        return item(
                Material.COMPARATOR,
                lang.forPlayer(player).text(
                        "gui.ranking-sort",
                        modeName
                ),
                lang.forPlayer(player).text(
                        "gui.ranking-page",
                        String.valueOf(
                                state.pageIndex + 1
                        ),
                        String.valueOf(
                                maxPageIndex + 1
                        )
                )
        );
    }

    /**
     * 主人显示名（离线玩家以 UUID 前 8 位兜底）。
     */
    private String ownerDisplayName(
            UUID ownerUuid
    ) {

        String name =
                Bukkit.getOfflinePlayer(
                        ownerUuid
                ).getName();

        if (name == null ||
                name.isBlank()) {

            return ownerUuid
                    .toString()
                    .substring(
                            0,
                            8
                    );
        }

        return name;
    }

    /**
     * 通用物品构建。
     */
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

    /**
     * 每玩家面板状态。
     */
    private static final class State {

        private SortMode mode =
                SortMode.MEOW_RANK;

        private int pageIndex;

        private boolean adminMode;
    }
}
