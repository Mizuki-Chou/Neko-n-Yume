package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.event.CatFedEvent;
import mizukichou.nekonyume.event.CatTierUpgradeEvent;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.storage.CatStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
 * plugin 仅用于 NamespacedKey；
 * 数值全部来自 ConfigManager 快照；
 * 玩家文案全部来自 Lang。
 * </p>
 *
 * <p>
 * 喵丹：PDC 品质 + 批次；批次不匹配即过期。
 * 0.6.2：吃喵丹有概率提升底蕴。
 * 0.7.1：喵丹物品文案按接收者语言生成；
 * 含 § 色码的喵丹名进聊天消息前经 LegacyComponentSerializer 转换。
 * </p>
 */
public class CatFoodManager {

    private static final int MAX_HUNGER = 100;

    private static final int MEOW_DAN_MAX_STACK = 64;

    private final Random random =
            new Random();

    private final Map<Material, Integer> foodValues =
            new EnumMap<>(Material.class);

    private final JavaPlugin plugin;
    private final CatStore store;
    private final CatCache cache;
    private final ConfigManager configManager;
    private final CatProgressionService progression;
    private final Lang lang;

    /*
     * 含 § 色码的文本（喵丹名）进聊天组件前
     * 必须经 LegacyComponentSerializer 转换，
     * 避免 LegacyFormattingDetected 警告。
     */
    private final LegacyComponentSerializer legacySerializer =
            LegacyComponentSerializer.legacySection();

    private final NamespacedKey meowDanKey;
    private final NamespacedKey meowDanGenKey;

    public CatFoodManager(
            JavaPlugin plugin,
            CatStore store,
            CatCache cache,
            ConfigManager configManager,
            CatProgressionService progression,
            Lang lang
    ) {

        this.plugin = plugin;
        this.store = store;
        this.cache = cache;
        this.configManager = configManager;
        this.progression = progression;
        this.lang = lang;

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
                configManager.snapshot()
                        .getFood()
                        .getValues()
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

    /*
     * 生成喵丹物品。
     *
     * player 决定物品文案语言（null = 默认语言）：
     * 命令发放传目标玩家；
     * 配方预览 / 怪物掉落传 null。
     */
    public ItemStack createMeowDan(
            MeowDanQuality quality,
            int amount,
            Player player
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
                    lang.forPlayer(player).text(
                            "meowdan-name."
                                    + quality.name()
                                    .toLowerCase(
                                            java.util.Locale.ROOT
                                    )
                    )
            );

            meta.setLore(
                    Arrays.asList(
                            quality.getColorCode()
                                    + lang.forPlayer(player).text(
                                    "feed.meowdan-lore-use"
                            ),
                            quality.getColorCode()
                                    + lang.forPlayer(player).text(
                                    "feed.meowdan-lore-values",
                                    String.valueOf(
                                            quality.getMeowPowerGain()
                                    ),
                                    String.valueOf(
                                            quality.getAffectionGain()
                                    ),
                                    String.valueOf(
                                            quality.getXpGain()
                                    )
                            )
                    )
            );

            meta.setCustomModelData(
                    configManager.snapshot()
                            .getItems()
                            .meowdanCustomModelData(
                                    quality
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

        return configManager.snapshot()
                .getItems()
                .getMeowdanGeneration();
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
                lang.forPlayer(player).messageComponents(
                        "feed.meowdan-eat",
                        Component.text(
                                cat.getName()
                        ),
                        legacySerializer.deserialize(
                                lang.forPlayer(player).text(
                                        "meowdan-name."
                                                + quality.name()
                                                .toLowerCase(
                                                        java.util.Locale.ROOT
                                                )
                                )
                        )
                )
        );

        if (actualAffectionGain > 0) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "feed.meowdan-affection",
                            String.valueOf(
                                    actualAffectionGain
                            ),
                            String.valueOf(
                                    cat.getAffection()
                            )
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
                    lang.forPlayer(player).message(
                            "feed.tier-upgrade-fail"
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
                lang.forPlayer(player).message(
                        "feed.tier-upgrade",
                        cat.getName(),
                        lang.forPlayer(player).text(
                                "tier-name."
                                        + newTier.name()
                                        .toLowerCase(
                                                java.util.Locale.ROOT
                                        )
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
                    lang.forPlayer(player).message(
                            "feed.full"
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

        ConfigSnapshot config =
                configManager.snapshot();

        cat.addAffection(
                config.getAffection()
                        .getFeedBase()
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

        ConfigSnapshot.Meow meowConfig =
                config.getMeow();

        if (feedCount <
                meowConfig.getFeedChanceLimit()) {

            int chance =
                    meowConfig.getFeedChance()
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

        player.sendMessage(
                lang.forPlayer(player).message(
                        "feed.ate-food",
                        cat.getName(),
                        lang.forPlayer(player).text(
                                "food-name."
                                        + item.getType()
                                        .name()
                                        .toLowerCase(
                                                java.util.Locale.ROOT
                                        )
                        )
                )
        );

        player.sendMessage(
                lang.forPlayer(player).message(
                        "feed.hunger-up",
                        String.valueOf(
                                actualHungerGain
                        ),
                        String.valueOf(
                                cat.getHunger()
                        )
                )
        );

        player.sendMessage(
                lang.forPlayer(player).message(
                        "feed.affection-up",
                        String.valueOf(
                                actualAffectionGain
                        ),
                        String.valueOf(
                                cat.getAffection()
                        )
                )
        );

        return true;
    }

}
