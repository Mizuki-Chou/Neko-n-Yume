package mizukichou.nekonyume.config;

import org.bukkit.Material;

import java.util.Map;

/**
 * 配置解析静态工具（从 ConfigLoader 拆分）。
 *
 * <p>
 * 全部为纯 JVM 逻辑，不触碰 Bukkit Registry，
 * 单元测试与生产环境行为一致。
 * </p>
 */
final class ConfigParseSupport {

    private ConfigParseSupport() {
    }

    /*
     * 安全"空气"判定（避免 Registry 依赖）：
     *
     * Material.isAir() 内部经 asBlockType() 惰性访问 Registry，
     * 在没有服务器实例的单元测试中会抛
     * IllegalStateException（No RegistryAccess implementation found）。
     * 空气仅三种：AIR / CAVE_AIR / VOID_AIR，
     * 用枚举常量直接比较即可。
     */
    static boolean isAir(Material material) {

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
     * 这里改为遍历枚举常量，纯 JVM 逻辑。
     */
    static Material matchMaterialSafe(String name) {

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

    static String mapString(
            Map<?, ?> map,
            String key
    ) {

        Object value =
                map.get(key);

        return value == null
                ? null
                : value.toString();
    }

    static int mapInt(
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

    static int clamp(
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

    static int positive(
            int value,
            int min
    ) {

        return Math.max(
                min,
                value
        );
    }
}
