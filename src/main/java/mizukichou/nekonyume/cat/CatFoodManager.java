package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.config.PluginConfig;
import mizukichou.nekonyume.event.CatFedEvent;
import mizukichou.nekonyume.storage.CatStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 猫咪食物与喵丹管理。
 *
 * <p>
 * Step 5A-2：构造注入。
 * plugin 仅用于 NamespacedKey 与原始 config 读取；
 * 数据读写走 CatStore，猫加载走 CatCache，
 * 数值走 PluginConfig，成长走 CatProgressionService。
 * </p>
 */
public class CatFoodManager {

    private static final int MAX_HUNGER = 100;

    /*
     * ============================================================
     * 喵丹
     * ============================================================
     *
     * 稳定获得喵力的珍贵道具。
     * 五种品质：平凡 / 精良 / 独特 / 卓越 / 至极。
     *
     * 本轮只提供管理指令发放；
     * 合成配方以后单独立项。
     */

    private static final int MEOW_DAN_MAX_STACK = 64;

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
     * 喵力概率随机源。
     */
    private final Random random =
            new Random();

    /*
     * 食物 → 饱食度。
     *
     * 数据来自 config.yml 的 food.values，
     * 由 PluginConfig 读取。
     */
    private final Map<Material, Integer> foodValues =
            new EnumMap<>(Material.class);

    /*
     * 注入依赖。
     */
    private final JavaPlugin plugin;
    private final CatStore store;
    private final CatCache cache;
    private final PluginConfig config;
    private final CatProgressionService progression;

    /*
     * 喵丹 PDC 标记。
     * 存储品质枚举名（STRING）。
     */
    private final NamespacedKey meowDanKey;

    public CatFoodManager(
            JavaPlugin plugin,
            CatStore store,
            CatCache cache,
            PluginConfig config,
            CatProgressionService progression
    ) {

        this.plugin = plugin;
        this.store = store;
        this.cache = cache;
        this.config = config;
        this.progression = progression;

        this.meowDanKey =
                new NamespacedKey(
                        plugin,
                        "nekonyume_meowdan"
                );

        registerFoods();
    }

    /*
     * ============================================================
     * 注册食物
     * ============================================================
     */

    private void registerFoods() {

        foodValues.putAll(
                config.getFoodValues()
        );
    }

    /*
     * ============================================================
     * 重载食物表
     * ============================================================
     *
     * /nekoyume reload 时由主类调用。
     */

    public void reloadFoods() {

        foodValues.clear();

        registerFoods();
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
     * 喵丹 - PDC 标记
     * ============================================================
     */

    public NamespacedKey getMeowDanKey() {
        return meowDanKey;
    }

    /*
     * ============================================================
     * 喵丹 - 创建
     * ============================================================
     */

    public ItemStack createMeowDan(
            MeowDanQuality quality,
            int amount
    ) {

        if (quality == null) {
            quality = MeowDanQuality.COMMON;
        }

        int safeAmount =
                Math.max(
                        1,
                        Math.min(
                                amount,
                                MEOW_DAN_MAX_STACK
                        )
                );

        ItemStack item =
                new ItemStack(
                        Material.GOLD_NUGGET,
                        safeAmount
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            meta.setDisplayName(
                    quality.getFullDisplayName()
            );

            meta.setLore(
                    Arrays.asList(
                            quality.getColorCode()
                                    + "右键你的猫咪使用",
                            quality.getColorCode()
                                    + "喵力 +"
                                    + quality.getMeowPowerGain()
                                    + " · 好感 +"
                                    + quality.getAffectionGain()
                                    + " · 经验 +"
                                    + quality.getXpGain()
                    )
            );

            /*
             * 自定义材质编号。
             * 可在 config.yml 的
             * items.meowdan.custom-model-data.<品质> 中覆盖。
             */
            meta.setCustomModelData(
                    plugin.getConfig()
                            .getInt(
                                    "items.meowdan.custom-model-data."
                                            + quality.name()
                                            .toLowerCase(
                                                    Locale.ROOT
                                            ),
                                    quality.getDefaultModelData()
                            )
            );

            /*
             * PDC 存储品质枚举名。
             */
            meta.getPersistentDataContainer()
                    .set(
                            meowDanKey,
                            PersistentDataType.STRING,
                            quality.name()
                    );

            item.setItemMeta(
                    meta
            );
        }

        return item;
    }

    /*
     * ============================================================
     * 喵丹 - 判定
     * ============================================================
     *
     * 只有携带 PDC 标记的物品才是喵丹。
     * 玩家改名 / 伪造外观的物品无效。
     */

    public boolean isMeowDan(
            ItemStack item
    ) {

        if (item == null ||
                item.getType().isAir()) {

            return false;
        }

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer()
                .has(
                        meowDanKey,
                        PersistentDataType.STRING
                );
    }

    /*
     * ============================================================
     * 喵丹 - 读取品质
     * ============================================================
     *
     * 未知 / 非法品质返回 null，
     * 调用方应拒绝使用。
     */

    public MeowDanQuality getMeowDanQuality(
            ItemStack item
    ) {

        if (!isMeowDan(item)) {
            return null;
        }

        String qualityName =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(
                                meowDanKey,
                                PersistentDataType.STRING
                        );

        if (qualityName == null) {
            return null;
        }

        for (MeowDanQuality quality :
                MeowDanQuality.values()) {

            if (quality.name()
                    .equalsIgnoreCase(qualityName)) {

                return quality;
            }
        }

        return null;
    }

    /*
     * ============================================================
     * 喵丹 - 使用
     * ============================================================
     *
     * 效果由品质决定。
     * 不计入每日前 3 次喂食机会。
     */

    public boolean feedMeowDan(
            Player player,
            ItemStack item
    ) {

        if (player == null ||
                item == null) {

            return false;
        }

        MeowDanQuality quality =
                getMeowDanQuality(
                        item
                );

        if (quality == null) {
            return false;
        }

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 玩家必须拥有猫。
         */
        if (!store.hasCat(playerUUID)) {
            return false;
        }

        Cat cat =
                cache.loadCat(player);

        if (cat == null) {
            return false;
        }

        /*
         * 好感。
         */
        int oldAffection =
                cat.getAffection();

        cat.addAffection(
                quality.getAffectionGain()
        );

        int actualAffectionGain =
                cat.getAffection()
                        - oldAffection;

        store.setCatAffection(
                playerUUID,
                cat.getAffection()
        );

        /*
         * 经验（统一入口，含升级反馈）。
         */
        progression.gainExperience(
                player,
                cat,
                quality.getXpGain()
        );

        /*
         * 喵力（统一入口，含喵光一闪与升阶反馈）。
         */
        progression.grantMeowPower(
                player,
                cat,
                quality.getMeowPowerGain()
        );

        /*
         * 消耗喵丹。
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
         * 音效。
         */
        player.playSound(
                player.getLocation(),
                Sound.ENTITY_GENERIC_EAT,
                1.0f,
                1.0f
        );

        /*
         * 提示。
         * 名字是玩家可控文本，用 Component.text 拼接。
         */
        player.sendMessage(
                mm.deserialize(
                        "<gradient:#c4b5fd:#a78bfa>✨ </gradient>"
                ).append(
                        Component.text(
                                cat.getName()
                        )
                ).append(
                        mm.deserialize(
                                "<white> 吃下了 </white>"
                        )
                ).append(
                        Component.text(
                                quality.getFullDisplayName()
                        )
                ).append(
                        mm.deserialize(
                                "<white>!</white>"
                        )
                )
        );

        if (actualAffectionGain > 0) {

            player.sendMessage(
                    mm.deserialize(
                            "<red>❤ 好感度 <green>+"
                                    + actualAffectionGain
                                    + " <gray>("
                                    + cat.getAffection()
                                    + "/100)</gray>"
                    )
            );
        }

        return true;
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
     *
     * 数值（基础好感 / 喵力概率 / 每日机会次数）
     * 全部来自 config.yml。
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
        if (!store.hasCat(playerUUID)) {
            return false;
        }

        /*
         * ========================================================
         * 获取运行时 Cat
         * ========================================================
         */

        Cat cat =
                cache.loadCat(player);

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
         * 性格修正：
         *
         * 挑食猫的食物效果打折扣。
         *
         * 经验与饱食度使用修正后的实际价值，
         * 保持一致。
         */
        CatPersonality personality =
                cat.getPersonality();

        int effectiveFoodValue =
                (int) Math.round(
                        foodValue
                                * personality
                                .getFoodValueMultiplier()
                );

        if (effectiveFoodValue <= 0) {
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
                                + effectiveFoodValue
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
         * 喂食增加好感度：
         * 基础（config: affection.feed-base）+ 性格额外加成。
         *
         * 计算实际增加值，
         * 好感度封顶 100 时如实反馈。
         */
        int oldAffection =
                cat.getAffection();

        cat.addAffection(
                config.getFeedAffectionBase()
                        + personality
                        .getFeedAffectionBonus()
        );

        int actualAffectionGain =
                cat.getAffection()
                        - oldAffection;

        /*
         * 记录最后喂食时间。
         */
        cat.markFed();

        /*
         * ========================================================
         * 喂食后重新开始饥饿计时
         * ========================================================
         */

        store.setCatHungerLastUpdate(
                playerUUID,
                System.currentTimeMillis()
        );

        /*
         * ========================================================
         * 持久化运行时状态
         * ========================================================
         */

        store.setCatHunger(
                playerUUID,
                cat.getHunger()
        );

        store.setCatAffection(
                playerUUID,
                cat.getAffection()
        );

        store.setCatLastFedAt(
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
         * 经验
         * ========================================================
         *
         * 喂食经验 = 实际食物价值。
         * 统一走 CatProgressionService.gainExperience()。
         */

        int xpGain =
                effectiveFoodValue;

        progression.gainExperience(
                player,
                cat,
                xpGain
        );

        /*
         * ========================================================
         * 喵力概率
         * ========================================================
         *
         * 每天前 N 次成功喂食才有机会
         * （N = config: meow.feed-chance-limit）。
         *
         * 基础概率 config: meow.feed-chance
         * + 性格偏移（百分点）。
         */

        int meowGain = 0;

        int feedCount =
                store.getCatFeedCount(
                        playerUUID
                );

        if (feedCount <
                config.getFeedMeowChanceLimit()) {

            int chance =
                    config.getFeedMeowChance()
                            + personality
                            .getFeedMeowChanceBonus();

            if (chance > 0 &&
                    random.nextInt(100) < chance) {

                meowGain = 1;

                progression.grantMeowPower(
                        player,
                        cat,
                        1
                );
            }
        }

        /*
         * 成功喂食计数 +1。
         * （无论是否获得喵力）
         */
        store.addCatFeedCount(
                playerUUID
        );

        /*
         * ========================================================
         * 触发事件
         * ========================================================
         */

        Bukkit.getPluginManager()
                .callEvent(
                        new CatFedEvent(
                                player,
                                cat,
                                item,
                                actualHungerGain,
                                actualAffectionGain,
                                xpGain,
                                meowGain
                        )
                );

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
                                + actualAffectionGain
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
