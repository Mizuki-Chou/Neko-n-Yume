package mizukichou.nekonyume.model.resourcepack;

import mizukichou.nekonyume.model.bbmodel.json.JsonArray;
import mizukichou.nekonyume.model.bbmodel.json.JsonFactory;
import mizukichou.nekonyume.model.bbmodel.json.JsonObject;
import mizukichou.nekonyume.model.bbmodel.json.JsonValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * pack.mcmeta 生成器（知识包 P0-2：26.2 版 pack metadata schema）。
 *
 * <p>
 * 1.21.9 起 pack version 采用 major.minor 语义：
 * {@code min_format} 与 {@code max_format} 是新必需字段；
 * legacy {@code pack_format} 不再写出（0.9.0更新——
 * 26.2 目标版本固定，不维护双格式）。
 * 两个字段均为 {@code [major, minor]} 数组形式。
 * </p>
 */
public final class PackMetadataBuilder {

    /**
     * 资源包描述（客户端资源包列表可见）。
     */
    public static final String DESCRIPTION =
            "Neko n' Yume models";

    private PackMetadataBuilder() {
    }

    /**
     * 构建 pack.mcmeta 的 JSON AST。
     *
     * @param format 目标 Minecraft 的 pack version
     */
    public static JsonObject build(
            PackFormat format
    ) {

        if (format == null) {

            throw new IllegalArgumentException(
                    "format must not be null."
            );
        }

        JsonArray version =
                format.toJsonArray();

        Map<String, JsonValue> pack =
                new LinkedHashMap<>();

        pack.put(
                "min_format",
                version
        );

        pack.put(
                "max_format",
                version
        );

        pack.put(
                "description",
                JsonFactory.string(
                        DESCRIPTION
                )
        );

        return JsonFactory.object(
                Map.of(
                        "pack",
                        JsonFactory.object(
                                pack
                        )
                )
        );
    }
}
