package mizukichou.nekonyume.model.resourcepack;

import mizukichou.nekonyume.model.bbmodel.json.JsonFactory;
import mizukichou.nekonyume.model.bbmodel.json.JsonObject;
import mizukichou.nekonyume.model.bbmodel.json.JsonValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code minecraft:model} item model type（知识包 P0-1）。
 *
 * <p>
 * 序列化为：
 * </p>
 * <pre>
 * { "type": "minecraft:model", "model": "ns:path" }
 * </pre>
 *
 * <p>
 * 其中 model 指向 {@code assets/&lt;ns&gt;/models/&lt;path&gt;.json}。
 * </p>
 */
public record PlainItemModel(
        String model
) implements ItemModelDefinition {

    public PlainItemModel {

        if (model == null || model.isBlank()) {

            throw new IllegalArgumentException(
                    "model must not be blank."
            );
        }
    }

    @Override
    public JsonObject toJson() {

        Map<String, JsonValue> members =
                new LinkedHashMap<>();

        members.put(
                "type",
                JsonFactory.string(
                        "minecraft:model"
                )
        );

        members.put(
                "model",
                JsonFactory.string(
                        model
                )
        );

        return JsonFactory.object(
                members
        );
    }

    /**
     * 完整的 item definition 顶层 JSON：
     * {@code {"model": {"type": "minecraft:model", "model": "..."}}}。
     */
    public JsonObject toItemDefinition() {

        return JsonFactory.object(
                Map.of(
                        "model",
                        toJson()
                )
        );
    }
}
