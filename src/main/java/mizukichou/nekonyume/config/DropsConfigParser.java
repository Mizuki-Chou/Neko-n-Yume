package mizukichou.nekonyume.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.logging.Logger;

/**
 * 掉落配置解析（0.8.0）。
 *
 * <p>
 * 负责 config.yml 的 drops 节：平时（general）与梦魔夜（muma-night）
 * 两套独立的掉落开关与概率。全部数值钳制到安全区间，
 * 缺失 / 非法一律回退默认，绝不抛异常。
 * </p>
 *
 * <p>
 * 兼容性：drops.muma-night 的三个旧键缺失时，
 * 回退读取旧版 muma-night.meowdan-drop-chance 等键
 * （0.7.x 配置可直接升级，无需手动迁移）。
 * </p>
 */
public final class DropsConfigParser {

    /*
     * 平时（梦魔夜以外）默认：关闭；
     * 开启后仅前三种喵丹可掉（权重 80/16/3），
     * 喵丹概率 0.05（低于梦魔夜），经验丸与装备袋为 0。
     */
    private static final boolean DEFAULT_GENERAL_ENABLED = false;
    private static final double DEFAULT_GENERAL_MEOWDAN_CHANCE = 0.05;
    private static final int[] DEFAULT_GENERAL_WEIGHTS = {80, 16, 3, 0, 0};
    private static final double DEFAULT_GENERAL_XP_PILL_CHANCE = 0.0;
    private static final double DEFAULT_GENERAL_ELITE_XP_PILL_CHANCE = 0.0;
    private static final double DEFAULT_GENERAL_BAG_CHANCE = 0.0;

    /*
     * 梦魔夜默认：开启；数值与 0.7.x 旧版完全一致。
     */
    private static final boolean DEFAULT_MUMA_ENABLED = true;
    private static final double DEFAULT_MUMA_MEOWDAN_CHANCE = 0.15;
    private static final int[] DEFAULT_MUMA_WEIGHTS = {80, 16, 3, 1, 0};
    private static final double DEFAULT_MUMA_XP_PILL_CHANCE = 0.03;
    private static final double DEFAULT_MUMA_ELITE_XP_PILL_CHANCE = 0.01;
    private static final double DEFAULT_MUMA_BAG_CHANCE = 0.02;

    private DropsConfigParser() {
    }

    public static ConfigSnapshot.Drops load(
            FileConfiguration config,
            Logger logger
    ) {

        ConfigSnapshot.Drops.DropSet general =
                loadGeneral(
                        config,
                        logger
                );

        ConfigSnapshot.Drops.DropSet mumaNight =
                loadMumaNight(
                        config,
                        logger
                );

        return new ConfigSnapshot.Drops(
                general,
                mumaNight
        );
    }

    private static ConfigSnapshot.Drops.DropSet loadGeneral(
            FileConfiguration config,
            Logger logger
    ) {

        boolean enabled =
                config.getBoolean(
                        "drops.general.enabled",
                        DEFAULT_GENERAL_ENABLED
                );

        double meowdanChance =
                ConfigParseSupport.unit(
                        config.getDouble(
                                "drops.general.meowdan-chance",
                                DEFAULT_GENERAL_MEOWDAN_CHANCE
                        )
                );

        int[] weights =
                parseWeights(
                        config,
                        "drops.general.meowdan-quality-weights",
                        DEFAULT_GENERAL_WEIGHTS,
                        logger
                );

        double xpPillChance =
                ConfigParseSupport.unit(
                        config.getDouble(
                                "drops.general.xp-pill-chance",
                                DEFAULT_GENERAL_XP_PILL_CHANCE
                        )
                );

        double eliteXpPillChance =
                ConfigParseSupport.unit(
                        config.getDouble(
                                "drops.general.elite-xp-pill-chance",
                                DEFAULT_GENERAL_ELITE_XP_PILL_CHANCE
                        )
                );

        double equipBagChance =
                ConfigParseSupport.unit(
                        config.getDouble(
                                "drops.general.equip-bag-chance",
                                DEFAULT_GENERAL_BAG_CHANCE
                        )
                );

        return new ConfigSnapshot.Drops.DropSet(
                enabled,
                meowdanChance,
                weights,
                xpPillChance,
                eliteXpPillChance,
                equipBagChance
        );
    }

    private static ConfigSnapshot.Drops.DropSet loadMumaNight(
            FileConfiguration config,
            Logger logger
    ) {

        boolean enabled =
                config.getBoolean(
                        "drops.muma-night.enabled",
                        DEFAULT_MUMA_ENABLED
                );

        /*
         * 旧键回退：0.7.x 配置将概率写在 muma-night 节下。
         * 新键缺失时沿用旧键，保证升级后掉落行为不变。
         */
        double meowdanChance =
                ConfigParseSupport.unit(
                        config.getDouble(
                                "drops.muma-night.meowdan-chance",
                                config.getDouble(
                                        "muma-night.meowdan-drop-chance",
                                        DEFAULT_MUMA_MEOWDAN_CHANCE
                                )
                        )
                );

        int[] weights =
                parseWeights(
                        config,
                        "drops.muma-night.meowdan-quality-weights",
                        DEFAULT_MUMA_WEIGHTS,
                        logger
                );

        double xpPillChance =
                ConfigParseSupport.unit(
                        config.getDouble(
                                "drops.muma-night.xp-pill-chance",
                                config.getDouble(
                                        "muma-night.xp-pill-drop-chance",
                                        DEFAULT_MUMA_XP_PILL_CHANCE
                                )
                        )
                );

        double eliteXpPillChance =
                ConfigParseSupport.unit(
                        config.getDouble(
                                "drops.muma-night.elite-xp-pill-chance",
                                config.getDouble(
                                        "muma-night.elite-xp-pill-drop-chance",
                                        DEFAULT_MUMA_ELITE_XP_PILL_CHANCE
                                )
                        )
                );

        double equipBagChance =
                ConfigParseSupport.unit(
                        config.getDouble(
                                "drops.muma-night.equip-bag-chance",
                                DEFAULT_MUMA_BAG_CHANCE
                        )
                );

        return new ConfigSnapshot.Drops.DropSet(
                enabled,
                meowdanChance,
                weights,
                xpPillChance,
                eliteXpPillChance,
                equipBagChance
        );
    }

    /*
     * 品质权重解析：必须是长度 >=5 的整数列表，
     * 取前 5 个（平凡→至极），逐项钳制 [0,10000]。
     *
     * 键不存在 → 静默使用默认（旧配置升级后的正常状态）；
     * 键存在但非法 → 回退默认并告警。
     */
    private static int[] parseWeights(
            FileConfiguration config,
            String path,
            int[] defaults,
            Logger logger
    ) {

        if (!config.contains(
                path
        )) {

            return defaults.clone();
        }

        List<Integer> raw;

        try {

            raw =
                    config.getIntegerList(
                            path
                    );

        } catch (RuntimeException e) {

            warn(
                    logger,
                    path,
                    e
            );

            return defaults.clone();
        }

        if (raw == null ||
                raw.size() < defaults.length) {

            warn(
                    logger,
                    path,
                    null
            );

            return defaults.clone();
        }

        int[] weights =
                new int[defaults.length];

        for (int i = 0;
             i < defaults.length;
             i++) {

            weights[i] =
                    ConfigParseSupport.clamp(
                            raw.get(i) == null
                                    ? 0
                                    : raw.get(i),
                            0,
                            10000
                    );
        }

        return weights;
    }

    private static void warn(
            Logger logger,
            String path,
            Throwable cause
    ) {

        if (logger == null) {
            return;
        }

        logger.warning(
                "[NekoNYume] Invalid drop config at "
                        + path
                        + " — using defaults."
                        + (cause == null
                        ? ""
                        : " (" + cause.getClass().getSimpleName()
                        + ": " + cause.getMessage() + ")")
        );
    }
}
