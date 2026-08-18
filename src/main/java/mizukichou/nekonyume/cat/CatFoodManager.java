package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.config.PluginConfig;
import mizukichou.nekonyume.event.CatFedEvent;
import mizukichou.nekonyume.event.CatTierUpgradeEvent;
import mizukichou.nekonyume.storage.CatStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
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
 * plugin 仅用于 NamespacedKey 与原始 config 读取。
 * </p>
 *
 * <p>
 * 喵丹：PDC 品质 + 批次；批次不匹配即过期。
 * 0.6.2：吃喵丹有概率提升底蕴。
 * </p>
 */
public class CatFoodManager {

    private static final int MAX_HUNGER = 100;

    private static final int MEOW_DAN_MAX_STACK = 64;

    private static final int MEOW_DAN_DEFAULT_GENERATION = 1;

    private final MiniMessage mm =
            MiniMessage.miniMessage();

    private final Random random =
            new Random();

    private final Map<Material, Integer> foodValues =
            new EnumMap<>(Material.class);

    private final JavaPlugin plugin;
    private final CatStore store;
    private final CatCache cache;
    private final PluginConfig config;
    private final CatProgressionService progression;

    private final NamespacedKey meowDanKey;
    private final NamespacedKey meowDanGenKey;

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

        this.meowDanGenKey =
                new NamespacedKey(
                        plugin,
                        "nekonyume_meowdan_gen"
                );

        registerFoods();
    }

    private void registerFoods() {

        foodValues.putAll(
                config.getFoodValues()
        );
    }

    public void reloadFoods() {

        foodValues.clear();

        registerFoods();
    }

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

    public Map<Material, Integer> getFoodValues() {

        return Collections.unmodifiableMap(
                foodValues
        );
    }

    public NamespacedKey getMeowDanKey() {
        return meowDanKey;
    }

    /*
     * 品质顺序（与枚举声明顺序无关）：
     * 平凡 → 精良 → 独特 → 卓越 → 至极。
     */
    public static java.util.List<MeowDanQuality>
    orderedQualities() {

        java.util.List<MeowDanQuality> list =
                new java.util.ArrayList<>(
                        java.util.List.of(
                                MeowDanQuality.values()
                        )
                );

        list.sort(
                java.util.Comparator
                        .comparingInt(
                                MeowDanQuality::getMeowPowerGain
                        )
                        .thenComparingInt(
                                MeowDanQuality::getXpGain
                        )
        );

        return list;
    }

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

            meta.getPersistentDataContainer()
                    .set(
                            meowDanKey,
                            PersistentDataType.STRING,
                            quality.name()
                    );

            meta.getPersistentDataContainer()
                    .set(
                            meowDanGenKey,
                            PersistentDataType.INTEGER,
                            currentMeowDanGeneration()
                    );

            item.setItemMeta(
                    meta
            );
        }

        return item;
    }

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

        if (!meta.getPersistentDataContainer()
                .has(
                        meowDanKey,
                        PersistentDataType.STRING
                )) {

            return false;
        }

        Integer generation =
                meta.getPersistentDataContainer()
                        .get(
                                meowDanGenKey,
                                PersistentDataType.INTEGER
                        );

        return generation != null &&
                generation == currentMeowDanGeneration();
    }

    public boolean isLegacyMeowDan(
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
                ) &&
                !isMeowDan(item);
    }

    private int currentMeowDanGeneration() {

        return plugin.getConfig()
                .getInt(
                        "items.meowdan.generation",
                        MEOW_DAN_DEFAULT_GENERATION
                );
    }

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

        if (!store.hasCat(playerUUID)) {
            return false;
        }

        Cat cat =
                cache.loadCat(player);

        if (cat == null) {
            return false;
        }

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

        progression.gainExperience(
                player,
                cat,
                quality.getXpGain()
        );

        progression.grantMeowPower(
                player,
                cat,
                quality.getMeowPowerGain()
        );

        if (player.getGameMode() != GameMode.CREATIVE) {

            if (item.getAmount() <= 1) {

                item.setAmount(0);

            } else {

                item.setAmount(
                        item.getAmount() - 1
                );
            }
        }

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_GENERIC_EAT,
                1.0f,
                1.0f
        );

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

        /*
         * 底蕴升阶判定（0.6.2）。
         */
        tryTierUpgrade(
                player,
                cat,
                quality,
                playerUUID
        );

        return true;
    }

    /*
     * ============================================================
     * 底蕴升阶（吃喵丹触发，0.6.2）
     * ============================================================
     *
     * 普通：卓越 50% → 稀有；至极 100% → 稀有
     * 稀有：卓越无效；至极 50% → 独特
     * 独特：至极 20% → 梦幻
     * 梦幻：封顶
     */
    private void tryTierUpgrade(
            Player player,
            Cat cat,
            MeowDanQuality quality,
            UUID playerUUID
    ) {

        java.util.List<MeowDanQuality> ordered =
                orderedQualities();

        int qualityIndex =
                ordered.indexOf(
                        quality
                );

        boolean isEpic =
                qualityIndex == 3;

        boolean isLegendary =
                qualityIndex >= 4;

        CatTier newTier = null;
        int chance = 0;

        switch (cat.getTier()) {

            case COMMON -> {

                if (isLegendary) {

                    newTier = CatTier.RARE;
                    chance = 100;

                } else if (isEpic) {

                    newTier = CatTier.RARE;
                    chance = 50;
                }
            }

            case RARE -> {

                if (isLegendary) {

                    newTier = CatTier.UNIQUE;
                    chance = 50;
                }
            }

            case UNIQUE -> {

                if (isLegendary) {

                    newTier = CatTier.DREAM;
                    chance = 20;
                }
            }

            case DREAM -> {
                return;
            }
        }

        if (newTier == null) {
            return;
        }

        boolean success =
                chance >= 100 ||
                        random.nextInt(100) < chance;

        if (!success) {

            player.sendMessage(
                    mm.deserialize(
                            "<gray>喵丹的力量在体内流转，但底蕴没有变化…</gray>"
                    )
            );

            return;
        }

        CatTier fromTier =
                cat.getTier();

        cat.setTier(
                newTier
        );

        store.setCatTier(
                playerUUID,
                newTier.name()
        );

        progression.syncSkillSlots(
                player,
                cat
        );

        player.sendMessage(
                mm.deserialize(
                        "<gradient:#c4b5fd:#a78bfa>🌟 底蕴升华!</gradient>"
                ).append(
                        Component.text(
                                " " + cat.getName()
                        )
                ).append(
                        mm.deserialize(
                                "<white> 的底蕴提升到了 </white>"
                        )
                ).append(
                        Component.text(
                                newTier.getDisplayName()
                        )
                )
        );

        player.playSound(
                player.getLocation(),
                Sound.UI_TOAST_CHALLENGE_COMPLETE,
                1.0f,
                1.0f
        );

        UUID entityUuid =
                cat.getEntityUuid();

        if (entityUuid != null) {

            Entity entity =
                    Bukkit.getEntity(
                            entityUuid
                    );

            if (entity != null &&
                    entity.isValid()) {

                entity.getWorld()
                        .spawnParticle(
                                Particle.END_ROD,
                                entity.getLocation()
                                        .add(0, 1, 0),
                                50,
                                0.5,
                                0.5,
                                0.5,
                                0.05
                        );
            }
        }

        /*
         * 事后通知事件：成就系统等第三方监听。
         */
        Bukkit.getPluginManager()
                .callEvent(
                        new CatTierUpgradeEvent(
                                player,
                                cat,
                                fromTier,
                                newTier
                        )
                );
    }

    /*
     * ============================================================
     * 喂猫
     * ============================================================
     */

    public boolean feedCat(
            Player player,
            ItemStack item
    ) {

        if (player == null ||
                item == null) {

            return false;
        }

        if (!isFood(item)) {
            return false;
        }

        UUID playerUUID =
                player.getUniqueId();

        if (!store.hasCat(playerUUID)) {
            return false;
        }

        Cat cat =
                cache.loadCat(player);

        if (cat == null) {
            return false;
        }

        int currentHunger =
                cat.getHunger();

        if (currentHunger >= MAX_HUNGER) {

            player.sendMessage(
                    mm.deserialize(
                            "<yellow>🐱 你的猫咪已经吃饱了!</yellow>"
                    )
            );

            return false;
        }

        int foodValue =
                getFoodValue(
                        item
                );

        if (foodValue <= 0) {
            return false;
        }

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

        cat.setHunger(
                newHunger
        );

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

        cat.markFed();

        store.setCatHungerLastUpdate(
                playerUUID,
                System.currentTimeMillis()
        );

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

        if (player.getGameMode() != GameMode.CREATIVE) {

            if (item.getAmount() <= 1) {

                item.setAmount(0);

            } else {

                item.setAmount(
                        item.getAmount() - 1
                );
            }
        }

        int xpGain =
                effectiveFoodValue;

        progression.gainExperience(
                player,
                cat,
                xpGain
        );

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

        store.addCatFeedCount(
                playerUUID
        );

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

        String catName =
                cat.getName();

        String foodName =
                getFoodName(
                        item.getType()
                );

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