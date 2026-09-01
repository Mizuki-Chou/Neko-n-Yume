package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.CatEquipItem;
import mizukichou.nekonyume.lang.Lang;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 右键喂食监听。
 *
 * <p>
 * 0.7.0：文案改走 Lang（feed.not-your-cat / feed.meowdan-expired）。
 * </p>
 */
public class CatFoodListener implements Listener {

    private final CatFoodManager foodManager;
    private final Lang lang;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    public CatFoodListener(
            CatFoodManager foodManager,
            NamespacedKey catKey,
            NamespacedKey ownerKey,
            Lang lang
    ) {

        this.foodManager = foodManager;
        this.catKey = catKey;
        this.ownerKey = ownerKey;
        this.lang = lang;
    }

    /*
     * 0.8.1 R5（社区上报）：
     * ignoreCancelled = true——区域保护/交互限制插件取消事件时，
     * 本插件绝不再继续喂食/消耗物品，遵守跨插件事件契约。
     */
    @EventHandler(
            priority = EventPriority.NORMAL,
            ignoreCancelled = true
    )
    public void onCatFeed(
            PlayerInteractAtEntityEvent event
    ) {

        /*
         * ============================================================
         * 1. 只处理 Bukkit 猫
         * ============================================================
         */

        if (!(event.getRightClicked()
                instanceof Cat cat)) {

            return;
        }

        Player player =
                event.getPlayer();

        /*
         * ============================================================
         * 2. 只处理 Neko n' Yume 的猫
         * ============================================================
         */

        if (!cat.getPersistentDataContainer()
                .has(
                        catKey,
                        PersistentDataType.BYTE
                )) {

            return;
        }

        /*
         * ============================================================
         * 3. 主人校验（安全关键）
         * ============================================================
         *
         * 一旦确认这是我们的猫：
         * 非主人的右键必须立即取消事件。
         *
         * 否则原版会对驯服猫执行喂食 / 繁殖，
         * 出现 love mode 爱心，
         * 甚至两只猫同时进入 love mode 时
         * 繁殖出没有 PDC 标记的小猫。
         */

        String ownerUUID =
                cat.getPersistentDataContainer()
                        .get(
                                ownerKey,
                                PersistentDataType.STRING
                        );

        boolean isOwner =
                ownerUUID != null &&
                        ownerUUID.equals(
                                player.getUniqueId()
                                        .toString()
                        );

        if (!isOwner) {

            event.setCancelled(
                    true
            );

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "feed.not-your-cat"
                    )
            );

            return;
        }

        /*
         * ============================================================
         * 4. 只处理主手
         * ============================================================
         *
         * 副手持食物不触发，避免双持时重复处理。
         */

        if (event.getHand()
                != EquipmentSlot.HAND) {

            return;
        }

        ItemStack item =
                player.getInventory()
                        .getItemInMainHand();

        if (item == null ||
                item.getType().isAir()) {

            /*
             * 空手右键：
             * 不取消事件，
             * 保留原版「空手右键切换坐姿」。
             */
            return;
        }

        /*
         * ============================================================
         * 4b. 装备穿戴判定（0.8.0）
         * ============================================================
         *
         * 装备走独立逻辑：唯一装备位，替换时旧装备归还。
         */

        if (foodManager.isEquipment(item)) {

            event.setCancelled(true);

            CatEquipItem equip =
                    foodManager.getEquipment(
                            item
                    );

            if (foodManager.equipCat(
                    player,
                    cat,
                    equip,
                    item
            )) {

                player.getWorld()
                        .playSound(
                                cat.getLocation(),
                                Sound.ENTITY_CAT_PURR,
                                1.0f,
                                1.2f
                        );
            }

            return;
        }

        /*
         * ============================================================
         * 5a. 喵丹优先判定
         * ============================================================
         *
         * 喵丹不算普通食物，
         * 走独立的使用逻辑。
         */

        if (foodManager.isMeowDan(item)) {

            /*
             * 阻止原版右键默认行为。
             */
            event.setCancelled(true);

            boolean used =
                    foodManager.feedMeowDan(
                            player,
                            item
                    );

            if (used) {

                player.getWorld()
                        .playSound(
                                cat.getLocation(),
                                Sound.ENTITY_CAT_EAT,
                                1.0f,
                                1.0f
                        );
            }

            return;
        }

        /*
         * ============================================================
         * 5a2. 经验丸判定（0.7.4）
         * ============================================================
         *
         * 经验丸不是食物（不影响饱食），
         * 走独立的使用逻辑：只加经验。
         */

        if (foodManager.isXpPill(item)) {

            event.setCancelled(true);

            boolean used =
                    foodManager.feedXpPill(
                            player,
                            item
                    );

            if (used) {

                player.getWorld()
                        .playSound(
                                cat.getLocation(),
                                Sound.ENTITY_CAT_EAT,
                                1.0f,
                                1.0f
                        );
            }

            return;
        }

        /*
         * ============================================================
         * 5b. 过期喵丹：明确提示 + 取消事件 + 不消耗
         * ============================================================
         */

        if (foodManager.isLegacyMeowDan(item)) {

            event.setCancelled(true);

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "feed.meowdan-expired"
                    )
            );

            return;
        }

        /*
         * ============================================================
         * 5c. 交给 CatFoodManager 判断和处理
         * ============================================================
         *
         * 不再在 Listener 里自己操作 hunger / affection。
         */

        if (!foodManager.isFood(item)) {

            /*
             * 非食物：
             * 不取消事件（保留原版交互）。
             */
            return;
        }

        /*
         * 阻止 Minecraft 对这次右键猫咪的默认处理。
         *
         * 注意：
         * 即使猫咪已经吃饱（feedCat 返回 false），
         * 也必须取消事件。
         * 否则玩家手持鱼类右键驯服猫时，
         * 原版会尝试喂食并消耗物品，
         * 与"已经吃饱"提示相互矛盾。
         */
        event.setCancelled(true);

        boolean success =
                foodManager.feedCat(
                        player,
                        item
                );

        /*
         * ============================================================
         * 6. 喂食成功 → 播放音效
         * ============================================================
         *
         * 成功即播放。
         * 创造模式不消耗物品，但同样播放音效。
         */

        if (success) {

            player.getWorld()
                    .playSound(
                            cat.getLocation(),
                            Sound.ENTITY_CAT_EAT,
                            1.0f,
                            1.0f
                    );
        }
    }

    /*
     * ============================================================
     * 装备守卫（0.8.0）：装备物品禁止原版交互
     * ============================================================
     *
     * 五类装备的材质都携带原版交互：
     * - 项圈（拴绳）：可拴栅栏/生物；
     * - 铃铛：可直接放置；
     * - 围巾（羊毛）：可直接放置；
     * - 名牌：可右键命名生物（消耗）；
     * - 毛线球（线）：可在两棵绊线钩间拉线。
     *
     * 一旦被原版行为消耗，物品的 PDC 身份与属性（含至极觉醒
     * 附加属性）即永久丢失。因此统一拦截：放置、右键方块、
     * 右键实体三类事件，保证装备只能通过“右键自己的猫”穿戴。
     */

    /*
     * 方块放置（铃铛/围巾等）。
     * LOWEST 优先取消，阻止方块进入世界。
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onEquipPlaceBlock(
            BlockPlaceEvent event
    ) {

        if (!foodManager.isEquipment(
                event.getItemInHand()
        )) {

            return;
        }

        event.setCancelled(true);

        event.getPlayer()
                .sendMessage(
                        lang.forPlayer(
                                        event.getPlayer()
                                )
                                .message(
                                        "equip.no-place"
                                )
                );
    }

    /*
     * 右键方块（拴绳拴栅栏、毛线球拉绊线等）。
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onEquipUseBlock(
            PlayerInteractEvent event
    ) {

        if (event.getAction()
                != Action.RIGHT_CLICK_BLOCK) {

            return;
        }

        if (event.getHand()
                != EquipmentSlot.HAND) {

            return;
        }

        ItemStack item =
                event.getItem();

        if (!foodManager.isEquipment(
                item
        )) {

            return;
        }

        event.setCancelled(true);

        event.getPlayer()
                .sendMessage(
                        lang.forPlayer(
                                        event.getPlayer()
                                )
                                .message(
                                        "equip.no-place"
                                )
                );
    }

    /*
     * 右键实体（名牌命名、拴绳拴生物等）。
     * 只拦截主手；对自己猫的穿戴流程不受影响。
     *
     * 0.8.1 修复（P0）：
     * PlayerInteractEntityEvent 先于 PlayerInteractAtEntityEvent 触发，
     * 且取消前者会让 Paper 不再触发后者——若这里无差别取消，
     * onCatFeed 中的装备穿戴流程（equipCat）将永远无法执行。
     * 因此：目标是自己的猫时放行，交给 AtEntity 事件处理；
     * 其余实体（原版命名/拴绳会消耗装备物品）继续拦截。
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onEquipUseEntity(
            PlayerInteractEntityEvent event
    ) {

        if (event.getHand()
                != EquipmentSlot.HAND) {

            return;
        }

        /*
         * 自己的猫：放行给 PlayerInteractAtEntityEvent 的穿戴流程。
         */
        if (event.getRightClicked()
                instanceof Cat cat &&
                cat.getPersistentDataContainer()
                        .has(
                                catKey,
                                PersistentDataType.BYTE
                        )) {

            String ownerUUID =
                    cat.getPersistentDataContainer()
                            .get(
                                    ownerKey,
                                    PersistentDataType.STRING
                            );

            if (event.getPlayer()
                    .getUniqueId()
                    .toString()
                    .equals(ownerUUID)) {

                return;
            }
        }

        ItemStack item =
                event.getPlayer()
                        .getInventory()
                        .getItemInMainHand();

        if (!foodManager.isEquipment(
                item
        )) {

            return;
        }

        event.setCancelled(true);

        event.getPlayer()
                .sendMessage(
                        lang.forPlayer(
                                        event.getPlayer()
                                )
                                .message(
                                        "equip.no-place"
                                )
                );
    }
}
