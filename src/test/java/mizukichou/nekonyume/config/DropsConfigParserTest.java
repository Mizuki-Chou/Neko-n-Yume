package mizukichou.nekonyume.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 掉落配置解析测试（0.8.0）：
 * 默认值、钳制、权重合法性、0.7.x 旧键回退。
 */
class DropsConfigParserTest {

    private final Logger logger =
            Logger.getAnonymousLogger();

    private ConfigSnapshot.Drops load(String yaml) {

        YamlConfiguration config =
                new YamlConfiguration();

        try {

            config.loadFromString(
                    yaml
            );

        } catch (org.bukkit.configuration.InvalidConfigurationException e) {

            throw new RuntimeException(
                    e
            );
        }

        return DropsConfigParser.load(
                config,
                logger
        );
    }

    /*
     * 空配置 → 全默认：
     * 平时关闭（只掉前三种喵丹、装备袋 0）；
     * 梦魔夜开启（旧版数值）。
     */
    @Test
    void defaults() {

        ConfigSnapshot.Drops.DropSet general =
                load("").getGeneral();

        assertFalse(general.isEnabled());

        assertEquals(
                0.05,
                general.getMeowdanChance(),
                1e-9
        );

        assertArrayEquals(
                new int[]{
                        80,
                        16,
                        3,
                        0,
                        0
                },
                general.getMeowdanQualityWeights()
        );

        assertEquals(
                0.0,
                general.getXpPillChance(),
                1e-9
        );

        assertEquals(
                0.0,
                general.getEliteXpPillChance(),
                1e-9
        );

        assertEquals(
                0.0,
                general.getEquipBagChance(),
                1e-9
        );

        ConfigSnapshot.Drops.DropSet muma =
                load("").getMumaNight();

        assertTrue(muma.isEnabled());

        assertEquals(
                0.15,
                muma.getMeowdanChance(),
                1e-9
        );

        assertArrayEquals(
                new int[]{
                        80,
                        16,
                        3,
                        1,
                        0
                },
                muma.getMeowdanQualityWeights()
        );

        assertEquals(
                0.03,
                muma.getXpPillChance(),
                1e-9
        );

        assertEquals(
                0.01,
                muma.getEliteXpPillChance(),
                1e-9
        );

        assertEquals(
                0.02,
                muma.getEquipBagChance(),
                1e-9
        );
    }

    /*
     * 自定义新键生效，非法概率钳到 [0,1]。
     */
    @Test
    void customValuesParsedAndClamped() {

        ConfigSnapshot.Drops drops =
                load("""
                        drops:
                          general:
                            enabled: true
                            meowdan-chance: 1.5
                            meowdan-quality-weights: [10, 20, 30, 40, 50]
                            xp-pill-chance: 0.01
                            elite-xp-pill-chance: -0.5
                            equip-bag-chance: 0.001
                          muma-night:
                            enabled: false
                            meowdan-chance: -1
                            meowdan-quality-weights: [1, 2, 3, 4, 5]
                            xp-pill-chance: 0.07
                            elite-xp-pill-chance: 0.02
                            equip-bag-chance: 0.05
                        """);

        ConfigSnapshot.Drops.DropSet general =
                drops.getGeneral();

        assertTrue(general.isEnabled());

        assertEquals(
                1.0,
                general.getMeowdanChance(),
                1e-9
        );

        assertArrayEquals(
                new int[]{
                        10,
                        20,
                        30,
                        40,
                        50
                },
                general.getMeowdanQualityWeights()
        );

        assertEquals(
                0.01,
                general.getXpPillChance(),
                1e-9
        );

        assertEquals(
                0.0,
                general.getEliteXpPillChance(),
                1e-9
        );

        assertEquals(
                0.001,
                general.getEquipBagChance(),
                1e-9
        );

        ConfigSnapshot.Drops.DropSet muma =
                drops.getMumaNight();

        assertFalse(muma.isEnabled());

        assertEquals(
                0.0,
                muma.getMeowdanChance(),
                1e-9
        );

        assertArrayEquals(
                new int[]{
                        1,
                        2,
                        3,
                        4,
                        5
                },
                muma.getMeowdanQualityWeights()
        );

        assertEquals(
                0.07,
                muma.getXpPillChance(),
                1e-9
        );

        assertEquals(
                0.02,
                muma.getEliteXpPillChance(),
                1e-9
        );

        assertEquals(
                0.05,
                muma.getEquipBagChance(),
                1e-9
        );
    }

    /*
     * 0.7.x 旧键回退：新键缺失时沿用 muma-night 节下的旧键。
     */
    @Test
    void legacyKeysFallback() {

        ConfigSnapshot.Drops.DropSet muma =
                load("""
                        muma-night:
                          meowdan-drop-chance: 0.42
                          xp-pill-drop-chance: 0.11
                          elite-xp-pill-drop-chance: 0.07
                        """).getMumaNight();

        assertTrue(muma.isEnabled());

        assertEquals(
                0.42,
                muma.getMeowdanChance(),
                1e-9
        );

        assertEquals(
                0.11,
                muma.getXpPillChance(),
                1e-9
        );

        assertEquals(
                0.07,
                muma.getEliteXpPillChance(),
                1e-9
        );

        /*
         * 旧键没有装备袋 → 默认 0.02。
         */
        assertEquals(
                0.02,
                muma.getEquipBagChance(),
                1e-9
        );
    }

    /*
     * 新键优先级高于旧键。
     */
    @Test
    void newKeysOverrideLegacy() {

        ConfigSnapshot.Drops.DropSet muma =
                load("""
                        muma-night:
                          meowdan-drop-chance: 0.42
                        drops:
                          muma-night:
                            meowdan-chance: 0.30
                        """).getMumaNight();

        assertEquals(
                0.30,
                muma.getMeowdanChance(),
                1e-9
        );
    }

    /*
     * 非法权重回退默认：长度不足、非列表、负值钳 0、超大钳 10000。
     */
    @Test
    void invalidWeightsFallbackOrClamped() {

        ConfigSnapshot.Drops.DropSet shortList =
                load("""
                        drops:
                          general:
                            meowdan-quality-weights: [1, 2]
                        """).getGeneral();

        assertArrayEquals(
                new int[]{
                        80,
                        16,
                        3,
                        0,
                        0
                },
                shortList.getMeowdanQualityWeights()
        );

        ConfigSnapshot.Drops.DropSet notList =
                load("""
                        drops:
                          general:
                            meowdan-quality-weights: 42
                        """).getGeneral();

        assertArrayEquals(
                new int[]{
                        80,
                        16,
                        3,
                        0,
                        0
                },
                notList.getMeowdanQualityWeights()
        );

        ConfigSnapshot.Drops.DropSet clamped =
                load("""
                        drops:
                          general:
                            meowdan-quality-weights: [-5, 0, 3, 99999, 7]
                        """).getGeneral();

        assertArrayEquals(
                new int[]{
                        0,
                        0,
                        3,
                        10000,
                        7
                },
                clamped.getMeowdanQualityWeights()
        );
    }
}
