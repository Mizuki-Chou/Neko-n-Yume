package mizukichou.nekonyume.config;

import mizukichou.nekonyume.cat.MeowDanQuality;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 物品与食物配置解析（从 ConfigLoader 拆分）。
 *
 * <p>
 * 喵丹批次号 / 自定义模型数据 / 食物价值表。
 * 未知 / 非法条目记录警告并跳过；配置为空时回退默认食物表。
 * </p>
 */
final class ItemConfigParser {

    private ItemConfigParser() {
    }

    static ConfigSnapshot.Items loadItems(
            FileConfiguration config
    ) {

        int generation =
                ConfigParseSupport.positive(
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

    static Map<Material, Integer> loadFoodValues(
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
                        ConfigParseSupport.matchMaterialSafe(
                                key
                        );

                if (material == null ||
                        ConfigParseSupport.isAir(material)) {

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
}
