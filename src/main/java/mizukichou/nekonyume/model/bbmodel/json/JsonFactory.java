package mizukichou.nekonyume.model.bbmodel.json;

import java.util.List;
import java.util.Map;

/**
 * JSON AST 构建与序列化工厂。
 *
 * <p>
 * 读取侧使用 {@link JsonParser}（BBModel 导入边界），
 * 写入侧使用本类：所有资源 JSON 先构造为
 * {@link JsonValue} AST，再统一序列化——
 * 避免手写字符串拼接产生非法 JSON
 * （知识包 P2-6：JSON AST + serialize）。
 * </p>
 *
 * <p>
 * 序列化输出确定性：对象成员与数组元素均按插入序，
 * 数字输出格式固定，与 {@link JsonParser} 可往返。
 * </p>
 */
public final class JsonFactory {

    private JsonFactory() {
    }

    public static JsonObject object(
            Map<String, JsonValue> members
    ) {

        return new JsonObject(
                members
        );
    }

    public static JsonObject object() {

        return new JsonObject(
                Map.of()
        );
    }

    public static JsonArray array(
            List<JsonValue> items
    ) {

        return new JsonArray(
                items
        );
    }

    public static JsonArray array(
            JsonValue... items
    ) {

        return new JsonArray(
                List.of(items)
        );
    }

    public static JsonString string(
            String value
    ) {

        return new JsonString(
                value
        );
    }

    public static JsonNumber number(
            double value
    ) {

        return new JsonNumber(
                value
        );
    }

    public static JsonNumber number(
            int value
    ) {

        return new JsonNumber(
                value
        );
    }

    public static JsonBoolean bool(
            boolean value
    ) {

        return new JsonBoolean(
                value
        );
    }

    public static JsonNull nullValue() {

        return JsonNull.INSTANCE;
    }

    /**
     * 序列化 JSON AST（2 空格缩进，UTF-8 安全）。
     */
    public static String write(
            JsonValue value
    ) {

        StringBuilder builder =
                new StringBuilder();

        writeValue(
                value,
                builder,
                ""
        );

        return builder.toString();
    }

    private static void writeValue(
            JsonValue value,
            StringBuilder builder,
            String indent
    ) {

        if (value.isNull()) {

            builder.append("null");
            return;
        }

        if (value.isBoolean()) {

            builder.append(
                    value.asBoolean()
                            .value()
            );

            return;
        }

        if (value.isNumber()) {

            writeNumber(
                    value.asNumber()
                            .value(),
                    builder
            );

            return;
        }

        if (value.isString()) {

            writeString(
                    value.asString()
                            .value(),
                    builder
            );

            return;
        }

        if (value.isArray()) {

            writeArray(
                    value.asArray(),
                    builder,
                    indent
            );

            return;
        }

        if (value.isObject()) {

            writeObject(
                    value.asObject(),
                    builder,
                    indent
            );
        }
    }

    private static void writeObject(
            JsonObject object,
            StringBuilder builder,
            String indent
    ) {

        Map<String, JsonValue> members =
                object.members();

        if (members.isEmpty()) {

            builder.append("{}");
            return;
        }

        builder.append("{\n");

        String childIndent =
                indent + "  ";

        int index = 0;

        for (Map.Entry<String, JsonValue> member :
                members.entrySet()) {

            builder.append(childIndent);

            writeString(
                    member.getKey(),
                    builder
            );

            builder.append(": ");

            writeValue(
                    member.getValue(),
                    builder,
                    childIndent
            );

            index++;

            if (index < members.size()) {
                builder.append(",");
            }

            builder.append("\n");
        }

        builder.append(indent)
                .append("}");
    }

    private static void writeArray(
            JsonArray array,
            StringBuilder builder,
            String indent
    ) {

        if (array.size() == 0) {

            builder.append("[]");
            return;
        }

        builder.append("[\n");

        String childIndent =
                indent + "  ";

        for (int i = 0;
                i < array.size();
                i++) {

            builder.append(childIndent);

            writeValue(
                    array.get(i),
                    builder,
                    childIndent
            );

            if (i < array.size() - 1) {
                builder.append(",");
            }

            builder.append("\n");
        }

        builder.append(indent)
                .append("]");
    }

    private static void writeString(
            String value,
            StringBuilder builder
    ) {

        builder.append('"');

        for (int i = 0;
                i < value.length();
                i++) {

            char character =
                    value.charAt(i);

            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {

                        builder.append(
                                String.format(
                                        "\\u%04x",
                                        (int) character
                                )
                        );

                    } else {

                        builder.append(
                                character
                        );
                    }
                }
            }
        }

        builder.append('"');
    }

    private static void writeNumber(
            double value,
            StringBuilder builder
    ) {

        /*
         * 整数值输出整数形式；否则 Double.toString
         * （与 JsonParser 的 Double.parseDouble 可往返）。
         */
        if (value == Math.rint(value) &&
                !Double.isInfinite(value) &&
                Math.abs(value) < 9_007_199_254_740_992.0) {

            builder.append(
                    (long) value
            );

            return;
        }

        builder.append(
                Double.toString(
                        value
                )
        );
    }
}
