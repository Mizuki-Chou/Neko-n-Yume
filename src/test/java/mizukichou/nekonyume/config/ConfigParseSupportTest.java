package mizukichou.nekonyume.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 0.8.1 修复（R3，社区上报）：
 * 配置 double 的有限性守卫——NaN / Infinity 必须回退，
 * 合法低倍率（如 0.5）不被强制最小值破坏。
 */
class ConfigParseSupportTest {

    @Test
    void nanFallsBackToDefault() {

        assertEquals(
                4.0,
                ConfigParseSupport.positiveDouble(
                        Double.NaN,
                        4.0
                ),
                0.0001
        );
    }

    @Test
    void infinityFallsBackToDefault() {

        assertEquals(
                2.5,
                ConfigParseSupport.positiveDouble(
                        Double.POSITIVE_INFINITY,
                        2.5
                ),
                0.0001
        );
    }

    @Test
    void negativeInfinityFallsBackToDefault() {

        assertEquals(
                4.0,
                ConfigParseSupport.positiveDouble(
                        Double.NEGATIVE_INFINITY,
                        4.0
                ),
                0.0001
        );
    }

    @Test
    void validLowMultiplierIsPreserved() {

        /*
         * 0.5 是合法配置（弱化梦魔夜），绝不能被钳成默认值。
         */
        assertEquals(
                0.5,
                ConfigParseSupport.positiveDouble(
                        0.5,
                        4.0
                ),
                0.0001
        );
    }

    @Test
    void negativeFiniteIsClampedToZero() {

        assertEquals(
                0.0,
                ConfigParseSupport.positiveDouble(
                        -2.0,
                        4.0
                ),
                0.0001
        );
    }

    @Test
    void unitRejectsNan() {

        assertEquals(
                0.0,
                ConfigParseSupport.unit(
                        Double.NaN
                ),
                0.0001
        );
    }

    @Test
    void finiteGuardsNan() {

        assertEquals(
                9.0,
                ConfigParseSupport.finite(
                        Double.NaN,
                        9.0
                ),
                0.0001
        );

        assertEquals(
                1.5,
                ConfigParseSupport.finite(
                        1.5,
                        9.0
                ),
                0.0001
        );
    }
}
