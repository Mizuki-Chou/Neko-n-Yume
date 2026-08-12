package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.NekoNYume;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CatFoodManager {

    private static final int MAX_HUNGER = 100;

    /*
     * 每次成功喂食增加的好感度
     */
    private static final int FEED_AFFECTION_GAIN = 15;

    /*
     * 食物 → 饱食度
     */
    private final Map<Material, Integer> foodValues =
            new HashMap<>();

    private final NekoNYume plugin;

    public CatFoodManager(NekoNYume plugin) {

        this.plugin = plugin;

        registerFoods();
    }

    /*
     * =========================
     * 注册食物
     * =========================
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
         */

        foodValues.put(
                Material.CAKE,
                25
        );
    }

    /*
     * =========================
     * 判断是否为猫咪食物
     * =========================
     */

    public boolean isFood(ItemStack item) {

        if (item == null) {
            return false;
        }

        if (item.getType() == Material.AIR) {
            return false;
        }

        return foodValues.containsKey(
                item.getType()
        );
    }

    /*
     * =========================
     * 获取食物饱食度
     * =========================
     */

    public int getFoodValue(ItemStack item) {

        if (!isFood(item)) {
            return 0;
        }

        return foodValues.get(
                item.getType()
        );
    }

    /*
     * =========================
     * 喂猫
     * =========================
     *
     * true  = 成功喂食
     * false = 不能喂
     */

    public boolean feedCat(
            Player player,
            ItemStack item
    ) {

        if (!isFood(item)) {
            return false;
        }

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 玩家必须拥有猫
         */

        if (!plugin.getDataManager()
                .hasCat(playerUUID)) {

            return false;
        }

        /*
         * 当前饱食度
         */

        int currentHunger =
                plugin.getDataManager()
                        .getCatHunger(
                                playerUUID
                        );

        /*
         * 如果已经满了
         */

        if (currentHunger >= MAX_HUNGER) {

            player.sendMessage(
                    "§e🐱 你的猫咪已经吃饱了!"
            );

            return false;
        }

        /*
         * 获取食物价值
         */

        int foodValue =
                getFoodValue(item);

        /*
         * 增加饱食度
         */

        plugin.getDataManager()
                .addCatHunger(
                        playerUUID,
                        foodValue
                );

        /*
         * 实际增加后的饱食度
         */

        int newHunger =
                plugin.getDataManager()
                        .getCatHunger(
                                playerUUID
                        );

        /*
         * 喂食成功：
         * 好感度 +15
         */

        plugin.getDataManager()
                .addCatAffection(
                        playerUUID,
                        FEED_AFFECTION_GAIN
                );

        /*
         * 获取增加后的好感度
         */

        int newAffection =
                plugin.getDataManager()
                        .getCatAffection(
                                playerUUID
                        );

        /*
         * 喂食后重新开始饥饿计时
         */

        plugin.getDataManager()
                .setCatHungerLastUpdate(
                        playerUUID,
                        System.currentTimeMillis()
                );

        /*
         * 消耗一个食物
         */

        item.setAmount(
                item.getAmount() - 1
        );

        /*
         * 获取猫咪名字
         */

        String catName =
                plugin.getDataManager()
                        .getCatName(
                                playerUUID
                        );

        /*
         * 提示玩家
         */

        player.sendMessage(
                "§d🐱 " + catName
                        + " §f吃掉了 §e"
                        + getFoodName(
                        item.getType()
                )
                        + "§f!"
        );

        player.sendMessage(
                "§6🍖 饱食度 §a+"
                        + foodValue
                        + " §7("
                        + newHunger
                        + "/"
                        + MAX_HUNGER
                        + ")"
        );

        player.sendMessage(
                "§c❤ 好感度 §a+"
                        + FEED_AFFECTION_GAIN
                        + " §7("
                        + newAffection
                        + "/100)"
        );

        return true;
    }

    /*
     * =========================
     * 获取食物显示名称
     * =========================
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