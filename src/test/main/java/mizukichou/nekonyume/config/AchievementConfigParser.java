package mizukichou.nekonyume.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 成就配置解析（从 ConfigLoader 拆分）。
 *
 * <p>
 * 总开关与各成就的 XP / 喵力奖励表。
 * </p>
 */
final class AchievementConfigParser {

    private AchievementConfigParser() {
    }

    static ConfigSnapshot.Achievements loadAchievements(
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
}
