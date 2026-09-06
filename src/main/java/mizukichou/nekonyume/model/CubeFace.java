package mizukichou.nekonyume.model;

/**
 * 立方体六面（Minecraft / Blockbench 约定）。
 */
public enum CubeFace {

    NORTH,
    EAST,
    SOUTH,
    WEST,
    UP,
    DOWN;

    /**
     * 从 Blockbench JSON 的面名（小写）解析；未知返回 null。
     */
    public static CubeFace fromName(
            String name
    ) {

        if (name == null) {
            return null;
        }

        try {

            return valueOf(
                    name.toUpperCase(java.util.Locale.ROOT)
            );

        } catch (IllegalArgumentException ignored) {

            return null;
        }
    }

    /**
     * Blockbench JSON 面名（小写）。
     */
    public String jsonName() {

        return name().toLowerCase(
                java.util.Locale.ROOT
        );
    }
}
