package mizukichou.nekonyume.model.resourcepack;

import mizukichou.nekonyume.model.bbmodel.json.JsonArray;
import mizukichou.nekonyume.model.bbmodel.json.JsonFactory;

/**
 * Minecraft Pack Version（1.21.9 起为 major.minor 语义）。
 *
 * <p>
 * 知识包 P0-2：pack version 不再是永远递增的单一整数，
 * minor 增量表示向后兼容的小版本变化。pack.mcmeta 中
 * 以 {@code [major, minor]} 数组形式表达。
 * </p>
 */
public record PackFormat(
        int major,
        int minor
) {

    public PackFormat {

        if (major < 0 || minor < 0) {

            throw new IllegalArgumentException(
                    "Pack format components must not be negative: " +
                            major + "." + minor
            );
        }
    }

    /**
     * pack.mcmeta 中的数组形式，例如 {@code [88, 0]}。
     */
    public JsonArray toJsonArray() {

        return JsonFactory.array(
                JsonFactory.number(
                        major
                ),
                JsonFactory.number(
                        minor
                )
        );
    }

    @Override
    public String toString() {

        return major + "." + minor;
    }
}
