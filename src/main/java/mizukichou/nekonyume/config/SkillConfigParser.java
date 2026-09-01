package mizukichou.nekonyume.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 技能配置解析（从 ConfigLoader 拆分）。
 *
 * <p>
 * 技能数值表（技能 → 参数 → 数值）与刷新花费规则。
 * </p>
 */
final class SkillConfigParser {

    private SkillConfigParser() {
    }

    static ConfigSnapshot.Skills loadSkills(
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

                    /*
                     * 0.8.1 修复（R3）：
                     * NaN / Infinity 技能数值守卫，
                     * 非有限值统一回退 0.0（等效于未配置）。
                     */
                    double rawValue =
                            skillSection.getDouble(
                                    valueKey
                            );

                    /*
                     * 0.8.4 R21（社区上报 M-NEW-08）：
                     * 数学合法 ≠ 业务合法——按键名钳制业务范围，
                     * 负持续时间/零半径等不再进入 Bukkit API。
                     * duration/cooldown ∈ [1, 3600] 秒
                     * radius ∈ [1, 64] 格
                     * power ∈ [0, 100000]
                     */
                    double finiteValue =
                            ConfigParseSupport.finite(
                                    rawValue,
                                    0.0
                            );

                    String keyLower =
                            valueKey.toLowerCase(
                                    Locale.ROOT
                            );

                    double clampedValue;

                    if (keyLower.contains("duration") ||
                            keyLower.contains("cooldown")) {

                        clampedValue =
                                Math.max(
                                        1.0,
                                        Math.min(
                                                3600.0,
                                                finiteValue
                                        )
                                );

                    } else if (keyLower.contains("radius")) {

                        clampedValue =
                                Math.max(
                                        1.0,
                                        Math.min(
                                                64.0,
                                                finiteValue
                                        )
                                );

                    } else if (keyLower.contains("power")) {

                        clampedValue =
                                Math.max(
                                        0.0,
                                        Math.min(
                                                100000.0,
                                                finiteValue
                                        )
                                );

                    } else {

                        clampedValue =
                                finiteValue;
                    }

                    entry.put(
                            valueKey,
                            clampedValue
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
                ConfigParseSupport.positive(
                        config.getInt(
                                "skills.refresh.cost",
                                10
                        ),
                        1
                ),
                ConfigParseSupport.positive(
                        config.getInt(
                                "skills.refresh.dream-slot-cost-multiplier",
                                3
                        ),
                        1
                ),
                values
        );
    }
}
