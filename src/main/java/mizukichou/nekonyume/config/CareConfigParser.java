package mizukichou.nekonyume.config;

import mizukichou.nekonyume.cat.CatMood;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/*
 * ============================================================
 * 羁绊纪元（0.8.0）care 节解析器
 * ============================================================
 *
 * 解析风格与既有解析器一致：
 *  - 缺失键走默认值；
 *  - 非法值钳制 / 回退默认，只告警，绝不拒启；
 *  - 纯 JVM 逻辑（不触碰 Registry），单测与生产一致。
 */

final class CareConfigParser {

    private CareConfigParser() {
    }

    static ConfigSnapshot.Care load(
            FileConfiguration config,
            Logger logger
    ) {

        Map<CatMood, Double> moodDamagePercent =
                loadMoodTable(
                        config,
                        "care.mood-damage-percent",
                        DEFAULT_MOOD_DAMAGE,
                        logger
                );

        Map<CatMood, Double> moodXpPercent =
                loadMoodTable(
                        config,
                        "care.mood-xp-percent",
                        DEFAULT_MOOD_XP,
                        logger
                );

        int affectionDailyDecay =
                clampInt(
                        config.getInt(
                                "care.affection-daily-decay",
                                2
                        ),
                        0,
                        10
                );

        int feedHungryAffection =
                clampInt(
                        config.getInt(
                                "care.feed-hungry-affection",
                                8
                        ),
                        0,
                        50
                );

        int feedNormalAffection =
                clampInt(
                        config.getInt(
                                "care.feed-normal-affection",
                                2
                        ),
                        0,
                        50
                );

        /*
         * 门槛语义：-1 = 关闭，其余钳制到 [0, 100]。
         */
        int hungrySkillThreshold =
                clampThreshold(
                        config.getInt(
                                "care.hungry-skill-threshold",
                                20
                        )
                );

        int starvingFightThreshold =
                clampThreshold(
                        config.getInt(
                                "care.starving-fight-threshold",
                                0
                        )
                );

        int hungryFeedThreshold =
                clampThreshold(
                        config.getInt(
                                "care.hungry-feed-threshold",
                                20
                        )
                );

        List<Integer> bondTierThresholds =
                loadThresholds(
                        config,
                        logger
                );

        List<Integer> bondXpPercent =
                loadBondTable(
                        config,
                        "care.bond-xp-percent",
                        DEFAULT_BOND_XP,
                        bondTierThresholds.size() + 1,
                        logger
                );

        List<Integer> bondCooldownPercent =
                loadBondTable(
                        config,
                        "care.bond-cooldown-percent",
                        DEFAULT_BOND_COOLDOWN,
                        bondTierThresholds.size() + 1,
                        logger
                );

        List<Integer> bondDamagePercent =
                loadBondTable(
                        config,
                        "care.bond-damage-percent",
                        DEFAULT_BOND_DAMAGE,
                        bondTierThresholds.size() + 1,
                        logger
                );

        int defeatHealthLoss =
                clampInt(
                        config.getInt(
                                "care.defeat-health-loss",
                                10
                        ),
                        0,
                        100
                );

        int feedHealthRestore =
                clampInt(
                        config.getInt(
                                "care.feed-health-restore",
                                5
                        ),
                        0,
                        100
                );

        /*
         * 饥饿好感衰减节流间隔（分钟，0 = 关闭）。
         *
         * 背景：旧实现的饥饿好感衰减与饥饿 tick 同频（每 5 分钟一次），
         * 每日喂食两次的玩家好感仍会净亏损；
         * 0.8.0 好感开始承载战力后，必须与喂食节奏对齐。
         * 默认 180 分钟：日喂两次维持高好感、弃养约 2~3 天归零。
         */
        int hungerAffectionLossMinutes =
                clampInt(
                        config.getInt(
                                "care.hunger-affection-loss-minutes",
                                180
                        ),
                        0,
                        1440
                );

        return new ConfigSnapshot.Care(
                moodDamagePercent,
                moodXpPercent,
                affectionDailyDecay,
                feedHungryAffection,
                feedNormalAffection,
                hungrySkillThreshold,
                starvingFightThreshold,
                hungryFeedThreshold,
                bondTierThresholds,
                bondXpPercent,
                bondCooldownPercent,
                bondDamagePercent,
                defeatHealthLoss,
                feedHealthRestore,
                hungerAffectionLossMinutes
        );
    }

    /*
     * 心情百分比表：五档心情，缺失档位取 0。
     */

    private static Map<CatMood, Double> loadMoodTable(
            FileConfiguration config,
            String path,
            Map<CatMood, Double> defaults,
            Logger logger
    ) {

        Map<CatMood, Double> table =
                new EnumMap<>(
                        CatMood.class
                );

        for (CatMood mood :
                CatMood.values()) {

            double value =
                    config.getDouble(
                            path + "." + mood.name(),
                            defaults.getOrDefault(
                                    mood,
                                    0.0
                            )
                    );

            table.put(
                    mood,
                    clampPercent(
                            value
                    )
            );
        }

        return Collections.unmodifiableMap(
                table
        );
    }

    /*
     * 羁绊阈值：严格递增校验，失败回退默认并告警。
     */

    private static List<Integer> loadThresholds(
            FileConfiguration config,
            Logger logger
    ) {

        List<?> raw =
                config.getList(
                        "care.bond-tier-thresholds"
                );

        if (raw == null) {
            return DEFAULT_THRESHOLDS;
        }

        List<Integer> thresholds =
                new ArrayList<>();

        for (Object element : raw) {

            if (element instanceof Number) {

                thresholds.add(
                        ((Number) element)
                                .intValue()
                );

            } else {

                logger.warning(
                        "[NekoNYume] care.bond-tier-thresholds 存在非数字项，"
                                + "已回退默认阈值。"
                );

                return DEFAULT_THRESHOLDS;
            }
        }

        if (thresholds.size()
                != DEFAULT_THRESHOLDS.size()) {

            logger.warning(
                    "[NekoNYume] care.bond-tier-thresholds 长度应为 "
                            + DEFAULT_THRESHOLDS.size()
                            + "，当前 "
                            + thresholds.size()
                            + "，已回退默认阈值。"
            );

            return DEFAULT_THRESHOLDS;
        }

        for (int i = 1;
             i < thresholds.size();
             i++) {

            if (thresholds.get(i)
                    <= thresholds.get(i - 1)) {

                logger.warning(
                        "[NekoNYume] care.bond-tier-thresholds 必须严格递增，"
                                + "已回退默认阈值。"
                );

                return DEFAULT_THRESHOLDS;
            }
        }

        return Collections.unmodifiableList(
                thresholds
        );
    }

    /*
     * 羁绊增益表：长度必须 = 阈值数 + 1，百分比钳制 ±100。
     */

    private static List<Integer> loadBondTable(
            FileConfiguration config,
            String path,
            List<Integer> defaults,
            int expectedSize,
            Logger logger
    ) {

        List<?> raw =
                config.getList(path);

        if (raw == null) {
            return defaults;
        }

        List<Integer> table =
                new ArrayList<>();

        for (Object element : raw) {

            if (element instanceof Number) {

                table.add(
                        clampPercentInt(
                                ((Number) element)
                                        .intValue()
                        )
                );

            } else {

                logger.warning(
                        "[NekoNYume] "
                                + path
                                + " 存在非数字项，已回退默认。"
                );

                return defaults;
            }
        }

        if (table.size() != expectedSize) {

            logger.warning(
                    "[NekoNYume] "
                            + path
                            + " 长度应为 "
                            + expectedSize
                            + "，当前 "
                            + table.size()
                            + "，已回退默认。"
            );

            return defaults;
        }

        return Collections.unmodifiableList(
                table
        );
    }

    private static double clampPercent(
            double value
    ) {

        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return Math.max(
                -100.0,
                Math.min(
                        100.0,
                        value
                )
        );
    }

    private static int clampPercentInt(
            int value
    ) {

        return Math.max(
                -100,
                Math.min(
                        100,
                        value
                )
        );
    }

    private static int clampInt(
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

    private static int clampThreshold(
            int value
    ) {

        return clampInt(
                value,
                -1,
                100
        );
    }

    /*
     * ============================================================
     * 默认值
     * ============================================================
     */

    private static final Map<CatMood, Double> DEFAULT_MOOD_DAMAGE;

    private static final Map<CatMood, Double> DEFAULT_MOOD_XP;

    private static final List<Integer> DEFAULT_THRESHOLDS =
            List.of(
                    20,
                    40,
                    60,
                    80,
                    100
            );

    private static final List<Integer> DEFAULT_BOND_XP =
            List.of(
                    0,
                    0,
                    5,
                    10,
                    10,
                    10
            );

    private static final List<Integer> DEFAULT_BOND_COOLDOWN =
            List.of(
                    0,
                    0,
                    0,
                    5,
                    5,
                    5
            );

    private static final List<Integer> DEFAULT_BOND_DAMAGE =
            List.of(
                    0,
                    0,
                    0,
                    0,
                    0,
                    10
            );

    static {

        Map<CatMood, Double> damage =
                new EnumMap<>(
                        CatMood.class
                );

        damage.put(
                CatMood.ECSTATIC,
                15.0
        );

        damage.put(
                CatMood.HAPPY,
                8.0
        );

        damage.put(
                CatMood.CALM,
                0.0
        );

        damage.put(
                CatMood.LOW,
                -10.0
        );

        damage.put(
                CatMood.SAD,
                -20.0
        );

        DEFAULT_MOOD_DAMAGE =
                Collections.unmodifiableMap(
                        damage
                );

        Map<CatMood, Double> xp =
                new EnumMap<>(
                        CatMood.class
                );

        xp.put(
                CatMood.ECSTATIC,
                10.0
        );

        xp.put(
                CatMood.HAPPY,
                5.0
        );

        xp.put(
                CatMood.CALM,
                0.0
        );

        xp.put(
                CatMood.LOW,
                -10.0
        );

        xp.put(
                CatMood.SAD,
                -20.0
        );

        DEFAULT_MOOD_XP =
                Collections.unmodifiableMap(
                        xp
                );
    }
}
