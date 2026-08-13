package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.NekoNYume;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class CatFoodManager {

    private static final int MAX_HUNGER = 100;

    /*
     * 每次成功喂食增加的好感度。
     */
    private static final int FEED_AFFECTION_GAIN = 15;

    /*
     * MiniMessage 实例。
     *
     * 全局消息格式统一为 MiniMessage；
     * 玩家可控文本一律用 Component.text 拼接，
     * 避免标签注入。
     */
    private final MiniMessage mm =
            MiniMessage.miniMessage();

    /*
     * 食物 → 饱食度。
     *
     * 这里是 Neko n' Yume 自己的食物规则，
     * 不再依赖 Minecraft 原版 Food Component。
     */
    private final Map<Material, Integer> foodValues =
            new EnumMap<>(Material.class);

    private final NekoNYume plugin;

    public CatFoodManager(
            NekoNYume plugin
    ) {

        this.plugin = plugin;

        registerFoods();
    }

    /*
     * ============================================================
     * 注册食物
     * ============================================================
     */

    private void registerFoods() {

        /*
         * 鱼类
         */
        foodValues.put(
                Material.COD,
                8
        );

        foodValues.put(
                Material.SALMON,
                10
        );

        /*
         * 熟鱼
         */
        foodValues.put(
                Material.COOKED_COD,
                15
        );

        foodValues.put(
                Material.COOKED_SALMON,
                18
        );

        /*
         * 鸡肉
         */
        foodValues.put(
                Material.CHICKEN,
                10
        );

        foodValues.put(
                Material.COOKED_CHICKEN,
                16
        );

        /*
         * 牛肉
         */
        foodValues.put(
                Material.BEEF,
                12
        );

        foodValues.put(
                Material.COOKED_BEEF,
                20
        );

        /*
         * 猪肉
         */
        foodValues.put(
                Material.PORKCHOP,
                12
        );

        foodValues.put(
                Material.COOKED_PORKCHOP,
                20
        );

        /*
         * 羊肉
         */
        foodValues.put(
                Material.MUTTON,
                12
        );

        foodValues.put(
                Material.COOKED_MUTTON,
                18
        );

        /*
         * 兔肉
         */
        foodValues.put(
                Material.RABBIT,
                10
        );

        foodValues.put(
                Material.COOKED_RABBIT,
                16
        );

        /*
         * 金胡萝卜
         */
        foodValues.put(
                Material.GOLDEN_CARROT,
                30
        );

        /*
         * 苹果
         */
        foodValues.put(
                Material.APPLE,
                12
        );

        /*
         * 面包
         */
        foodValues.put(
                Material.BREAD,
                12
        );

        /*
         * 蛋糕
         *
         * 保留你原来的设定。
         *
         * 注意：
         * Cake 在原版中属于方块，不一定能通过
         * 普通手持 ItemStack 的右键事件作为食物处理。
         */
        foodValues.put(
                Material.CAKE,
                25
        );
    }

    /*
     * ============================================================
     * 判断是否为 Neko n' Yume 猫咪食物
     * ============================================================
     */

    public boolean isFood(
            ItemStack item
    ) {

        if (item == null) {
            return false;
        }

        if (item.getType().isAir()) {
            return false;
        }

        return foodValues.containsKey(
                item.getType()
        );
    }

    /*
     * ============================================================
     * 获取食物饱食度
     * ============================================================
     */

    public int getFoodValue(
            ItemStack item
    ) {

        if (!isFood(item)) {
            return 0;
        }

        return foodValues.getOrDefault(
                item.getType(),
                0
        );
    }

    /*
     * ============================================================
     * 获取当前注册的食物
     * ============================================================
     *
     * 返回只读 Map，方便以后 GUI / 命令查看食物数据。
     */

    public Map<Material, Integer> getFoodValues() {

        return Collections.unmodifiableMap(
                foodValues
        );
    }

    /*
     * ============================================================
     * 喂猫
     * ============================================================
     *
     * true  = 成功喂食
     * false = 不能喂
     *
     * 现在 Cat 是运行时唯一真相。
     */

    public boolean feedCat(
            Player player,
            ItemStack item
    ) {

        if (player == null ||
                item == null) {

            return false;
        }

        /*
         * 必须是 Neko n' Yume 注册食物。
         */
        if (!isFood(item)) {
            return false;
        }

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 玩家必须拥有猫。
         */
        if (!plugin.getDataManager()
                .hasCat(
                        playerUUID
                )) {

            return false;
        }

        /*
         * ========================================================
         * 获取运行时 Cat
         * ========================================================
         */

        Cat cat =
                plugin.getCatManager()
                        .loadCat(
                                player
                        );

        if (cat == null) {
            return false;
        }

        /*
         * ========================================================
         * 当前饱食度
         * ========================================================
         */

        int currentHunger =
                cat.getHunger();

        /*
         * 已经吃饱。
         */
        if (currentHunger >= MAX_HUNGER) {

            player.sendMessage(
                    mm.deserialize(
                            "<yellow>🐱 你的猫咪已经吃饱了!</yellow>"
                    )
            );

            return false;
        }

        /*
         * ========================================================
         * 食物价值
         * ========================================================
         */

        int foodValue =
                getFoodValue(
                        item
                );

        if (foodValue <= 0) {
            return false;
        }

        /*
         * ========================================================
         * 计算实际增加值
         * ========================================================
         *
         * 例如：
         *
         * 当前 95
         * 食物 +20
         *
         * 实际只能 +5。
         */

        int newHunger =
                Math.min(
                        MAX_HUNGER,
                        currentHunger
                                + foodValue
                );

        int actualHungerGain =
                newHunger
                        - currentHunger;

        if (actualHungerGain <= 0) {
            return false;
        }

        /*
         * ========================================================
         * 修改运行时 Cat
         * ========================================================
         */

        cat.setHunger(
                newHunger
        );

        /*
         * 喂食增加好感度。
         */
        cat.addAffection(
                FEED_AFFECTION_GAIN
        );

        /*
         * 记录最后喂食时间。
         */
        cat.markFed();

        /*
         * ========================================================
         * 喂食后重新开始饥饿计时
         * ========================================================
         */

        plugin.getDataManager()
                .setCatHungerLastUpdate(
                        playerUUID,
                        System.currentTimeMillis()
                );

        /*
         * ========================================================
         * 持久化运行时状态
         * ========================================================
         */

        plugin.getDataManager()
                .setCatHunger(
                        playerUUID,
                        cat.getHunger()
                );

        plugin.getDataManager()
                .setCatAffection(
                        playerUUID,
                        cat.getAffection()
                );

        plugin.getDataManager()
                .setCatLastFedAt(
                        playerUUID,
                        cat.getLastFedAt()
                );

        /*
         * ========================================================
         * 消耗食物
         * ========================================================
         *
         * 创造模式不消耗。
         */

        if (player.getGameMode() != GameMode.CREATIVE) {

            if (item.getAmount() <= 1) {

                item.setAmount(0);

            } else {

                item.setAmount(
                        item.getAmount() - 1
                );
            }
        }

        /*
         * ========================================================
         * 获取显示信息
         * ========================================================
         */

        String catName =
                cat.getName();

        String foodName =
                getFoodName(
                        item.getType()
                );

        /*
         * ========================================================
         * 玩家提示
         * ========================================================
         *
         * catName 是玩家可控文本，
         * 用 Component.text 拼接，
         * 避免 MiniMessage 标签注入。
         *
         * foodName 来自内部常量表，可以安全走 MiniMessage。
         */

        player.sendMessage(
                mm.deserialize(
                        "<light_purple>🐱 </light_purple>"
                ).append(
                        Component.text(
                                catName
                        )
                ).append(
                        mm.deserialize(
                                "<white> 吃掉了 <yellow>"
                                        + foodName
                                        + "</yellow>!</white>"
                        )
                )
        );

        player.sendMessage(
                mm.deserialize(
                        "<gold>🍖 饱食度 <green>+"
                                + actualHungerGain
                                + " <gray>("
                                + cat.getHunger()
                                + "/"
                                + MAX_HUNGER
                                + ")</gray>"
                )
        );

        player.sendMessage(
                mm.deserialize(
                        "<red>❤ 好感度 <green>+"
                                + FEED_AFFECTION_GAIN
                                + " <gray>("
                                + cat.getAffection()
                                + "/100)</gray>"
                )
        );

        return true;
    }

    /*
     * ============================================================
     * 获取食物显示名称
     * ============================================================
     */

    private String getFoodName(
            Material material
    ) {

        return switch (material) {

            case COD ->
                    "生鳕鱼";

            case SALMON ->
                    "生鲑鱼";

            case COOKED_COD ->
                    "熟鳕鱼";

            case COOKED_SALMON ->
                    "熟鲑鱼";

            case CHICKEN ->
                    "生鸡肉";

            case COOKED_CHICKEN ->
                    "熟鸡肉";

            case BEEF ->
                    "生牛肉";

            case COOKED_BEEF ->
                    "牛排";

            case PORKCHOP ->
                    "生猪排";

            case COOKED_PORKCHOP ->
                    "熟猪排";

            case MUTTON ->
                    "生羊肉";

            case COOKED_MUTTON ->
                    "熟羊肉";

            case RABBIT ->
                    "生兔肉";

            case COOKED_RABBIT ->
                    "熟兔肉";

            case GOLDEN_CARROT ->
                    "金胡萝卜";

            case APPLE ->
                    "苹果";

            case BREAD ->
                    "面包";

            case CAKE ->
                    "蛋糕";

            default ->
                    material.name();
        };
    }
}
