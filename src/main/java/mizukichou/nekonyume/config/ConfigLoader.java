package mizukichou.nekonyume.config;

import mizukichou.nekonyume.cat.CatMood;
import mizukichou.nekonyume.cat.MeowDanQuality;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 配置解析器（纯函数）。
 *
 * <p>
 * 从 FileConfiguration 解析构建 ConfigSnapshot。
 * 所有数值带默认值与钳制，
 * 管理员写错配置不会让插件崩溃。
 * 本类不持有任何状态，可单元测试。
 * </p>
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static ConfigSnapshot load(
            FileConfiguration config,
            Logger logger
    ) {

        String language =
                config.getString(
                        "language",
                        "zh_cn"
                );

        ConfigSnapshot.Storage storage =
                new ConfigSnapshot.Storage(
                        config.getBoolean(
                                "storage.backup.enabled",
                                true
                        ),
                        positive(
                                config.getInt(
                                        "storage.backup.keep",
                                        5
                                ),
                                1
                        )
                );

        ConfigSnapshot.Items items =
                loadItems(
                        config
                );

        int petXpMin =
                positive(
                        config.getInt(
                                "growth.pet-xp-min",
                                5
                        ),
                        1
                );

        ConfigSnapshot.Growth growth =
                new ConfigSnapshot.Growth(
                        petXpMin,
                        positive(
                                config.getInt(
                                        "growth.pet-xp-max",
                                        30
                                ),
                                petXpMin
                        ),
                        positive(
                                config.getInt(
                                        "growth.level-curve-base",
                                        100
                                ),
                                1
                        )
                );

        ConfigSnapshot.Affection affection =
                new ConfigSnapshot.Affection(
                        clamp(
                                config.getInt(
                                        "affection.feed-base",
                                        15
                                ),
                                0,
                                100
                        ),
                        clamp(
                                config.getInt(
                                        "affection.pet-base",
                                        3
                                ),
                                0,
                                100
                        )
                );

        ConfigSnapshot.Meow meow =
                new ConfigSnapshot.Meow(
                        clamp(
                                config.getInt(
                                        "meow.pet-chance",
                                        18
                                ),
                                0,
                                100
                        ),
                        clamp(
                                config.getInt(
                                        "meow.feed-chance",
                                        8
                                ),
                                0,
                                100
                        ),
                        positive(
                                config.getInt(
                                        "meow.feed-chance-limit",
                                        3
                                ),
                                1
                        ),
                        positive(
                                config.getInt(
                                        "meow.rank-curve-offset",
                                        19
                                ),
                                1
                        )
                );

        long hungerIntervalMillis =
                config.getLong(
                        "hunger.base-interval-seconds",
                        300
                ) * 1000L;

        if (hungerIntervalMillis <= 0) {

            hungerIntervalMillis =
                    300L * 1000L;
        }

        ConfigSnapshot.Hunger hunger =
                new ConfigSnapshot.Hunger(
                        hungerIntervalMillis
                );

        ConfigSnapshot.Daily daily =
                new ConfigSnapshot.Daily(
                        positive(
                                config.getInt(
                                        "daily.pet-limit",
                                        3
                                ),
                                1
                        )
                );

        ConfigSnapshot.Food food =
                new ConfigSnapshot.Food(
                        loadFoodValues(
                                config,
                                logger
                        )
                );

        ConfigSnapshot.Gift gift =
                loadGift(
                        config,
                        logger
                );

        ConfigSnapshot.Achievements achievements =
                loadAchievements(
                        config
                );

        ConfigSnapshot.Skills skills =
                loadSkills(
                        config
                );

        ConfigSnapshot.Battle battle =
                new ConfigSnapshot.Battle(
                        config.getBoolean(
                                "battle.enabled",
                                true
                        ),
                        positive(
                                config.getInt(
                                        "battle.base-damage",
                                        5
                                ),
                                1
                        ),
                        positive(
                                config.getInt(
                                        "battle.per-rank-damage",
                                        1
                                ),
                                0
                        ),
                        positive(
                                config.getInt(
                                        "battle.attack-interval-ticks",
                                        40
                                ),
                                1
                        ),
                        positive(
                                config.getInt(
                                        "battle.aggro-radius",
                                        12
                                ),
                                1
                        ),
                        positive(
                                config.getInt(
                                        "battle.weakness-seconds",
                                        10
                                ),
                                1
                        ),
                        positive(
                                config.getInt(
                                        "battle.recovery-seconds",
                                        120
                                ),
                                1
                        ),
                        positive(
                                config.getInt(
                                        "battle.regen-interval-seconds",
                                        4
                                ),
                                1
                        ),
                        positive(
                                config.getInt(
                                        "battle.eternity-rebirth-seconds",
                                        180
                                ),
                                1
                        )
                );

        ConfigSnapshot.Aura aura =
                new ConfigSnapshot.Aura(
                        config.getBoolean(
                                "aura.enabled",
                                true
                        ),
                        positive(
                                config.getInt(
                                        "aura.base-radius",
                                        10
                                ),
                                1
                        ),
                        positive(
                                config.getInt(
                                        "aura.speed-unlock-level",
                                        5
                                ),
                                1
                        ),
                        positive(
                                config.getInt(
                                        "aura.strength-unlock-meow-rank",
                                        2
                                ),
                                0
                        ),
                        positive(
                                config.getInt(
                                        "aura.regen-unlock-level",
                                        15
                                ),
                                1
                        ),
                        clamp(
                                config.getInt(
                                        "aura.regen-affection",
                                        80
                                ),
                                0,
                                100
                        )
                );

        ConfigSnapshot.JoinMessage joinMessage =
                new ConfigSnapshot.JoinMessage(
                        config.getBoolean(
                                "join-message.enabled",
                                true
                        ),
                        new ArrayList<>(
                                config.getStringList(
                                        "join-message.messages"
                                )
                        )
                );

        ConfigSnapshot.MumaNight mumaNight =
                new ConfigSnapshot.MumaNight(
                        config.getDouble(
                                "muma-night.chance",
                                0.2
                        ),
                        config.getDouble(
                                "muma-night.health-multiplier",
                                4.0
                        ),
                        config.getDouble(
                                "muma-night.damage-multiplier",
                                2.5
                        ),
                        config.getDouble(
                                "muma-night.meowdan-drop-chance",
                                0.15
                        )
                );

        return new ConfigSnapshot(
                language,
                storage,
                items,
                growth,
                affection,
                meow,
                hunger,
                daily,
                food,
                gift,
                achievements,
                skills,
                battle,
                aura,
                joinMessage,
                mumaNight
        );
    }

    /*
     * ============================================================
     * 物品（喵丹）
     * ============================================================
     */

    private static ConfigSnapshot.Items loadItems(
            FileConfiguration config
    ) {

        int generation =
                positive(
                        config.getInt(
                                "items.meowdan.generation",
                                1
                        ),
                        1
                );

        Map<String, Integer> modelData =
                new LinkedHashMap<>();

        for (MeowDanQuality quality :
                MeowDanQuality.values()) {

            String key =
                    quality.name()
                            .toLowerCase(
                                    Locale.ROOT
                            );

            modelData.put(
                    key,
                    config.getInt(
                            "items.meowdan.custom-model-data."
                                    + key,
                            quality.getDefaultModelData()
                    )
            );
        }

        return new ConfigSnapshot.Items(
                generation,
                modelData
        );
    }

    /*
     * ============================================================
     * 食物表
     * ============================================================
     *
     * 未知 / 非法条目记录警告并跳过。
     * 若配置结果为空，回退默认表。
     */

    private static Map<Material, Integer> loadFoodValues(
            FileConfiguration config,
            Logger logger
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
                        matchMaterialSafe(
                                key
                        );

                if (material == null ||
                        isAir(material)) {

                    logger.warning(
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

                    logger.warning(
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

        return loaded;
    }

    private static void loadDefaultFoods(
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
     * 礼物
     * ============================================================
     */

    private static ConfigSnapshot.Gift loadGift(
            FileConfiguration config,
            Logger logger
    ) {

        boolean enabled =
                config.getBoolean(
                        "gift.enabled",
                        true
                );

        CatMood moodMin =
                parseMood(
                        config.getString(
                                "gift.mood-min",
                                "CALM"
                        )
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

                    logger.warning(
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
                                ),
                                logger
                        );

                if (!entries.isEmpty()) {

                    tiers.put(
                            tier,
                            entries
                    );
                }
            }
        }

        int maxTier =
                tiers.keySet()
                        .stream()
                        .max(Integer::compareTo)
                        .orElse(0);

        return new ConfigSnapshot.Gift(
                enabled,
                moodMin,
                clamp(
                        config.getInt(
                                "gift.base-chance",
                                20
                        ),
                        0,
                        100
                ),
                clamp(
                        config.getInt(
                                "gift.chance-per-rank",
                                5
                        ),
                        0,
                        100
                ),
                clamp(
                        config.getInt(
                                "gift.max-chance",
                                80
                        ),
                        0,
                        100
                ),
                tiers,
                maxTier
        );
    }

    private static List<GiftItemEntry> parseGiftEntries(
            List<Map<?, ?>> rawEntries,
            Logger logger
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

                    logger.warning(
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
                    matchMaterialSafe(
                            materialName
                    );

            if (material == null ||
                    isAir(material)) {

                logger.warning(
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

    private static CatMood parseMood(
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
     * 成就
     * ============================================================
     */

    private static ConfigSnapshot.Achievements loadAchievements(
            FileConfiguration config
    ) {

        boolean enabled =
                config.getBoolean(
                        "achievements.enabled",
                        true
                );

        Map<String, Integer> rewardXp =
                new LinkedHashMap<>();

        Map<String, Integer> rewardMeowPower =
                new LinkedHashMap<>();

        ConfigurationSection section =
                config.getConfigurationSection(
                        "achievements.rewards"
                );

        if (section != null) {

            for (String key :
                    section.getKeys(false)) {

                if (section.contains(key + ".xp")) {

                    rewardXp.put(
                            key,
                            section.getInt(
                                    key + ".xp",
                                    0
                            )
                    );
                }

                if (section.contains(key + ".meow-power")) {

                    rewardMeowPower.put(
                            key,
                            section.getInt(
                                    key + ".meow-power",
                                    0
                            )
                    );
                }
            }
        }

        return new ConfigSnapshot.Achievements(
                enabled,
                rewardXp,
                rewardMeowPower
        );
    }

    /*
     * ============================================================
     * 技能
     * ============================================================
     */

    private static ConfigSnapshot.Skills loadSkills(
            FileConfiguration config
    ) {

        Map<String, Map<String, Double>> values =
                new LinkedHashMap<>();

        ConfigurationSection section =
                config.getConfigurationSection(
                        "skills.values"
                );

        if (section != null) {

            for (String skillKey :
                    section.getKeys(false)) {

                ConfigurationSection skillSection =
                        section.getConfigurationSection(
                                skillKey
                        );

                if (skillSection == null) {
                    continue;
                }

                Map<String, Double> entry =
                        new LinkedHashMap<>();

                for (String valueKey :
                        skillSection.getKeys(false)) {

                    entry.put(
                            valueKey,
                            skillSection.getDouble(
                                    valueKey
                            )
                    );
                }

                values.put(
                        skillKey.toLowerCase(
                                Locale.ROOT
                        ),
                        Collections.unmodifiableMap(
                                entry
                        )
                );
            }
        }

        return new ConfigSnapshot.Skills(
                config.getString(
                        "skills.refresh.cost-type",
                        "meow-power"
                ),
                positive(
                        config.getInt(
                                "skills.refresh.cost",
                                10
                        ),
                        1
                ),
                positive(
                        config.getInt(
                                "skills.refresh.dream-slot-cost-multiplier",
                                5
                        ),
                        1
                ),
                values
        );
    }

    /*
     * ============================================================
     * 工具
     * ============================================================
     */

    /*
     * 安全"空气"判定（避免 Registry 依赖）：
     *
     * Material.isAir() 内部经 asBlockType() 惰性访问 Registry，
     * 在没有服务器实例的单元测试中会抛
     * IllegalStateException（No RegistryAccess implementation found）。
     * 空气仅三种：AIR / CAVE_AIR / VOID_AIR，
     * 用枚举常量直接比较即可，纯 JVM 逻辑，
     * 单元测试与生产环境行为一致。
     */
    private static boolean isAir(
            Material material
    ) {

        return material == Material.AIR ||
                material == Material.CAVE_AIR ||
                material == Material.VOID_AIR;
    }

    /*
     * 安全材质解析（避免 Registry 依赖）：
     *
     * Material.matchMaterial / getMaterial 内部访问 Bukkit Registry，
     * 在没有服务器实例的单元测试中会抛 IllegalStateException
     * （类初始化失败 → ExceptionInInitializerError / NoClassDefFoundError）。
     * 这里改为遍历枚举常量，纯 JVM 逻辑，
     * 单元测试与生产环境行为完全一致。
     */
    private static Material matchMaterialSafe(
            String name
    ) {

        if (name == null ||
                name.isBlank()) {

            return null;
        }

        for (Material material :
                Material.values()) {

            if (material.name()
                    .equalsIgnoreCase(name)) {

                return material;
            }
        }

        return null;
    }

    private static String mapString(
            Map<?, ?> map,
            String key
    ) {

        Object value =
                map.get(key);

        return value == null
                ? null
                : value.toString();
    }

    private static int mapInt(
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

    private static int clamp(
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

    private static int positive(
            int value,
            int min
    ) {

        return Math.max(
                min,
                value
        );
    }
}
