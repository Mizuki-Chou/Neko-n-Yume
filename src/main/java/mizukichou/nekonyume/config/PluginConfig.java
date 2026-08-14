package mizukichou.nekonyume.config;

import mizukichou.nekonyume.cat.CatMood;
import mizukichou.nekonyume.cat.MeowDanQuality;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件数值配置。
 *
 * <p>
 * 所有玩法数值集中在这里读取。
 * /nekoyumeadmin reload 时调用 reload() 重读。
 * </p>
 */
public class PluginConfig {

    private final JavaPlugin plugin;

    /*
     * 成长
     */
    private int petXpMin;
    private int petXpMax;
    private int levelCurveBase;

    /*
     * 好感
     */
    private int feedAffectionBase;
    private int petAffectionBase;

    /*
     * 喵力
     */
    private int petMeowChance;
    private int feedMeowChance;
    private int feedMeowChanceLimit;
    private int meowRankCurveOffset;

    /*
     * 饥饿
     */
    private long hungerIntervalMillis;

    /*
     * 每日限制
     */
    private int dailyPetLimit;

    /*
     * 食物表
     */
    private Map<Material, Integer> foodValues;

    /*
     * 礼物事件
     */
    private boolean giftEnabled;
    private CatMood giftMoodMin;
    private int giftBaseChance;
    private int giftChancePerRank;
    private int giftMaxChance;
    private int giftMaxTier;

    /*
     * 礼物档位表。
     * key = 档位编号（从 1 开始）
     */
    private Map<Integer, List<GiftItemEntry>> giftTiers;

    public PluginConfig(
            JavaPlugin plugin
    ) {

        this.plugin = plugin;

        reload();
    }

    /*
     * ============================================================
     * 重读配置
     * ============================================================
     *
     * 所有数值带默认值与钳制，
     * 管理员写错配置不会让插件崩溃。
     */

    public void reload() {

        FileConfiguration config =
                plugin.getConfig();

        /*
         * 成长
         */
        petXpMin =
                positive(
                        config.getInt(
                                "growth.pet-xp-min",
                                5
                        ),
                        1
                );

        petXpMax =
                positive(
                        config.getInt(
                                "growth.pet-xp-max",
                                30
                        ),
                        petXpMin
                );

        levelCurveBase =
                positive(
                        config.getInt(
                                "growth.level-curve-base",
                                100
                        ),
                        1
                );

        /*
         * 好感
         */
        feedAffectionBase =
                clamp(
                        config.getInt(
                                "affection.feed-base",
                                15
                        ),
                        0,
                        100
                );

        petAffectionBase =
                clamp(
                        config.getInt(
                                "affection.pet-base",
                                3
                        ),
                        0,
                        100
                );

        /*
         * 喵力
         */
        petMeowChance =
                clamp(
                        config.getInt(
                                "meow.pet-chance",
                                18
                        ),
                        0,
                        100
                );

        feedMeowChance =
                clamp(
                        config.getInt(
                                "meow.feed-chance",
                                8
                        ),
                        0,
                        100
                );

        feedMeowChanceLimit =
                positive(
                        config.getInt(
                                "meow.feed-chance-limit",
                                3
                        ),
                        1
                );

        meowRankCurveOffset =
                positive(
                        config.getInt(
                                "meow.rank-curve-offset",
                                19
                        ),
                        1
                );

        /*
         * 饥饿
         */
        hungerIntervalMillis =
                config.getLong(
                        "hunger.base-interval-seconds",
                        300
                ) * 1000L;

        if (hungerIntervalMillis <= 0) {

            hungerIntervalMillis =
                    300L * 1000L;
        }

        /*
         * 每日限制
         */
        dailyPetLimit =
                positive(
                        config.getInt(
                                "daily.pet-limit",
                                3
                        ),
                        1
                );

        /*
         * 食物表
         */
        loadFoodValues(
                config
        );

        /*
         * 礼物事件
         */
        loadGift(
                config
        );
    }

    /*
     * ============================================================
     * 食物表
     * ============================================================
     *
     * 从 config 读取 food.values。
     * 未知 / 非法条目记录警告并跳过。
     * 若配置结果为空，回退默认表。
     */

    private void loadFoodValues(
            FileConfiguration config
    ) {

        Map<Material, Integer> loaded =
                new EnumMap<>(Material.class);

        ConfigurationSection section =
                config.getConfigurationSection(
                        "food.values"
                );

        if (section != null) {

            for (String key :
                    section.getKeys(false)) {

                Material material =
                        Material.matchMaterial(
                                key
                        );

                if (material == null ||
                        material.isAir()) {

                    plugin.getLogger().warning(
                            "Unknown material in food.values: "
                                    + key
                    );

                    continue;
                }

                int value =
                        section.getInt(
                                key,
                                0
                        );

                if (value <= 0) {

                    plugin.getLogger().warning(
                            "Invalid food value for "
                                    + key
                                    + ", skipping."
                    );

                    continue;
                }

                loaded.put(
                        material,
                        value
                );
            }
        }

        if (loaded.isEmpty()) {

            loadDefaultFoods(
                    loaded
            );
        }

        foodValues = loaded;
    }

    private void loadDefaultFoods(
            Map<Material, Integer> target
    ) {

        target.put(Material.COD, 8);
        target.put(Material.SALMON, 10);
        target.put(Material.COOKED_COD, 15);
        target.put(Material.COOKED_SALMON, 18);
        target.put(Material.CHICKEN, 10);
        target.put(Material.COOKED_CHICKEN, 16);
        target.put(Material.BEEF, 12);
        target.put(Material.COOKED_BEEF, 20);
        target.put(Material.PORKCHOP, 12);
        target.put(Material.COOKED_PORKCHOP, 20);
        target.put(Material.MUTTON, 12);
        target.put(Material.COOKED_MUTTON, 18);
        target.put(Material.RABBIT, 10);
        target.put(Material.COOKED_RABBIT, 16);
        target.put(Material.GOLDEN_CARROT, 30);
        target.put(Material.APPLE, 12);
        target.put(Material.BREAD, 12);
        target.put(Material.CAKE, 25);
    }

    /*
     * ============================================================
     * 礼物事件
     * ============================================================
     *
     * 档位规则：每 5 阶一档。
     * 档位 = (喵阶 + 4) / 5，最小 1。
     *
     * tier-1 = 0~5
     * tier-2 = 6~10
     * tier-3 = 11~15
     * ...
     * 超出最高档位一律归入最高档。
     */

    private void loadGift(
            FileConfiguration config
    ) {

        giftEnabled =
                config.getBoolean(
                        "gift.enabled",
                        true
                );

        giftMoodMin =
                parseMood(
                        config.getString(
                                "gift.mood-min",
                                "CALM"
                        )
                );

        giftBaseChance =
                clamp(
                        config.getInt(
                                "gift.base-chance",
                                20
                        ),
                        0,
                        100
                );

        giftChancePerRank =
                clamp(
                        config.getInt(
                                "gift.chance-per-rank",
                                5
                        ),
                        0,
                        100
                );

        giftMaxChance =
                clamp(
                        config.getInt(
                                "gift.max-chance",
                                80
                        ),
                        0,
                        100
                );

        Map<Integer, List<GiftItemEntry>> tiers =
                new LinkedHashMap<>();

        ConfigurationSection section =
                config.getConfigurationSection(
                        "gift.tiers"
                );

        if (section != null) {

            for (String key :
                    section.getKeys(false)) {

                /*
                 * 档位键：tier-1 / tier-2 ...
                 */
                String number =
                        key.replace(
                                "tier-",
                                ""
                        );

                int tier;

                try {

                    tier =
                            Integer.parseInt(
                                    number
                            );

                } catch (NumberFormatException e) {

                    plugin.getLogger().warning(
                            "Invalid gift tier key: "
                                    + key
                    );

                    continue;
                }

                if (tier <= 0) {
                    continue;
                }

                List<GiftItemEntry> entries =
                        parseGiftEntries(
                                section.getMapList(
                                        key
                                )
                        );

                if (!entries.isEmpty()) {

                    tiers.put(
                            tier,
                            entries
                    );
                }
            }
        }

        giftTiers = tiers;

        giftMaxTier =
                tiers.keySet()
                        .stream()
                        .max(Integer::compareTo)
                        .orElse(0);
    }

    private List<GiftItemEntry> parseGiftEntries(
            List<Map<?, ?>> rawEntries
    ) {

        List<GiftItemEntry> entries =
                new ArrayList<>();

        for (Map<?, ?> entryMap :
                rawEntries) {

            int weight =
                    mapInt(
                            entryMap,
                            "weight",
                            1
                    );

            if (weight <= 0) {
                continue;
            }

            int minAmount =
                    mapInt(
                            entryMap,
                            "min",
                            mapInt(
                                    entryMap,
                                    "amount",
                                    1
                            )
                    );

            int maxAmount =
                    mapInt(
                            entryMap,
                            "max",
                            minAmount
                    );

            minAmount =
                    Math.max(
                            1,
                            minAmount
                    );

            maxAmount =
                    Math.max(
                            minAmount,
                            maxAmount
                    );

            /*
             * 喵丹条目。
             */
            String meowDanName =
                    mapString(
                            entryMap,
                            "meowdan"
                    );

            if (meowDanName != null &&
                    !meowDanName.isBlank()) {

                MeowDanQuality quality =
                        MeowDanQuality.fromInput(
                                meowDanName
                        );

                if (quality == null) {

                    plugin.getLogger().warning(
                            "Unknown meowdan quality in gift: "
                                    + meowDanName
                    );

                    continue;
                }

                entries.add(
                        new GiftItemEntry(
                                null,
                                quality,
                                minAmount,
                                maxAmount,
                                weight
                        )
                );

                continue;
            }

            /*
             * 原版物品条目。
             */
            String materialName =
                    mapString(
                            entryMap,
                            "material"
                    );

            Material material =
                    Material.matchMaterial(
                            materialName
                    );

            if (material == null ||
                    material.isAir()) {

                plugin.getLogger().warning(
                        "Unknown material in gift: "
                                + materialName
                );

                continue;
            }

            entries.add(
                    new GiftItemEntry(
                            material,
                            null,
                            minAmount,
                            maxAmount,
                            weight
                    )
            );
        }

        return entries;
    }

    private CatMood parseMood(
            String name
    ) {

        if (name == null ||
                name.isBlank()) {

            return CatMood.CALM;
        }

        for (CatMood mood :
                CatMood.values()) {

            if (mood.name()
                    .equalsIgnoreCase(name)) {

                return mood;
            }
        }

        return CatMood.CALM;
    }

    /*
     * ============================================================
     * 礼物条目
     * ============================================================
     *
     * material 与 meowDanQuality 二选一，
     * 另一个为 null。
     */

    public static final class GiftItemEntry {

        private final Material material;
        private final MeowDanQuality meowDanQuality;
        private final int minAmount;
        private final int maxAmount;
        private final int weight;

        public GiftItemEntry(
                Material material,
                MeowDanQuality meowDanQuality,
                int minAmount,
                int maxAmount,
                int weight
        ) {

            this.material = material;
            this.meowDanQuality = meowDanQuality;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.weight = weight;
        }

        public Material getMaterial() {
            return material;
        }

        public MeowDanQuality getMeowDanQuality() {
            return meowDanQuality;
        }

        public boolean isMeowDan() {
            return meowDanQuality != null;
        }

        public int getMinAmount() {
            return minAmount;
        }

        public int getMaxAmount() {
            return maxAmount;
        }

        public int getWeight() {
            return weight;
        }
    }

    /*
     * ============================================================
     * Getter
     * ============================================================
     */

    public int getPetXpMin() {
        return petXpMin;
    }

    public int getPetXpMax() {
        return petXpMax;
    }

    public int getLevelCurveBase() {
        return levelCurveBase;
    }

    public int getFeedAffectionBase() {
        return feedAffectionBase;
    }

    public int getPetAffectionBase() {
        return petAffectionBase;
    }

    public int getPetMeowChance() {
        return petMeowChance;
    }

    public int getFeedMeowChance() {
        return feedMeowChance;
    }

    public int getFeedMeowChanceLimit() {
        return feedMeowChanceLimit;
    }

    public int getMeowRankCurveOffset() {
        return meowRankCurveOffset;
    }

    public long getHungerIntervalMillis() {
        return hungerIntervalMillis;
    }

    public int getDailyPetLimit() {
        return dailyPetLimit;
    }

    public Map<Material, Integer> getFoodValues() {

        return Collections.unmodifiableMap(
                foodValues
        );
    }

    public boolean isGiftEnabled() {
        return giftEnabled;
    }

    public CatMood getGiftMoodMin() {
        return giftMoodMin;
    }

    public int getGiftBaseChance() {
        return giftBaseChance;
    }

    public int getGiftChancePerRank() {
        return giftChancePerRank;
    }

    public int getGiftMaxChance() {
        return giftMaxChance;
    }

    public int getGiftMaxTier() {
        return giftMaxTier;
    }

    /*
     * 按喵阶计算档位（纯函数，可单元测试）。
     *
     * tier-1 = 0~5
     * tier-2 = 6~10
     * ...
     */
    public static int computeGiftTier(
            int meowRank
    ) {

        if (meowRank < 0) {
            meowRank = 0;
        }

        return Math.max(
                1,
                (meowRank + 4) / 5
        );
    }

    /*
     * 按喵阶计算档位。
     */
    public int giftTierForRank(
            int meowRank
    ) {

        return computeGiftTier(
                meowRank
        );
    }


    /*
     * 获取指定档位的礼物条目。
     * 档位缺失时返回空列表。
     */
    public List<GiftItemEntry> getGiftTierExact(
            int tier
    ) {

        List<GiftItemEntry> entries =
                giftTiers.get(
                        tier
                );

        if (entries == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(
                entries
        );
    }

    /*
     * ============================================================
     * 工具
     * ============================================================
     */

    private String mapString(
            Map<?, ?> map,
            String key
    ) {

        Object value =
                map.get(key);

        return value == null
                ? null
                : value.toString();
    }

    private int mapInt(
            Map<?, ?> map,
            String key,
            int defaultValue
    ) {

        String value =
                mapString(
                        map,
                        key
                );

        if (value == null) {
            return defaultValue;
        }

        try {

            return Integer.parseInt(
                    value.trim()
            );

        } catch (NumberFormatException e) {

            return defaultValue;
        }
    }

    private int clamp(
            int value,
            int min,
            int max
    ) {

        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    private int positive(
            int value,
            int min
    ) {

        return Math.max(
                min,
                value
        );
    }
}
