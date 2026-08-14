package mizukichou.nekonyume.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 插件数值配置。
 *
 * <p>
 * 所有玩法数值集中在这里读取。
 * /nekoyume reload 时调用 reload() 重读。
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

    /*
     * ============================================================
     * 工具
     * ============================================================
     */

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
