package mizukichou.nekonyume.config;

import mizukichou.nekonyume.cat.CatMood;
import mizukichou.nekonyume.cat.MeowDanQuality;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 每日礼物配置解析（从 ConfigLoader 拆分）。
 *
 * <p>
 * 档位（tier-N）→ 条目列表（原版物品 / 喵丹），
 * 含心情门槛、概率、条目权重与数量范围。
 * </p>
 */
final class GiftConfigParser {

    private GiftConfigParser() {
    }

    static ConfigSnapshot.Gift loadGift(
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
                ConfigParseSupport.clamp(
                        config.getInt(
                                "gift.base-chance",
                                20
                        ),
                        0,
                        100
                ),
                ConfigParseSupport.clamp(
                        config.getInt(
                                "gift.chance-per-rank",
                                5
                        ),
                        0,
                        100
                ),
                ConfigParseSupport.clamp(
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
                    ConfigParseSupport.mapInt(
                            entryMap,
                            "weight",
                            1
                    );

            if (weight <= 0) {
                continue;
            }

            int minAmount =
                    ConfigParseSupport.mapInt(
                            entryMap,
                            "min",
                            ConfigParseSupport.mapInt(
                                    entryMap,
                                    "amount",
                                    1
                            )
                    );

            int maxAmount =
                    ConfigParseSupport.mapInt(
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
                    ConfigParseSupport.mapString(
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
                    ConfigParseSupport.mapString(
                            entryMap,
                            "material"
                    );

            Material material =
                    ConfigParseSupport.matchMaterialSafe(
                            materialName
                    );

            if (material == null ||
                    ConfigParseSupport.isAir(material)) {

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

    private static CatMood parseMood(String name) {

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
}
