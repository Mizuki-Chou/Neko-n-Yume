package mizukichou.nekonyume.model.bbmodel.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON 对象（只读，成员保持插入序）。
 */
public final class JsonObject implements JsonValue {

    private final Map<String, JsonValue> members;

    JsonObject(
            Map<String, JsonValue> members
    ) {

        this.members =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(members)
                );
    }

    /**
     * 按名取值；缺失返回 null。
     */
    public JsonValue get(
            String key
    ) {

        return members.get(
                key
        );
    }

    public boolean has(
            String key
    ) {

        return members.containsKey(
                key
        );
    }

    /**
     * 成员（不可修改视图）。
     */
    public Map<String, JsonValue> members() {

        return members;
    }

    /**
     * 按名取字符串；缺失或类型不符返回 null。
     */
    public String getString(
            String key
    ) {

        JsonValue value =
                members.get(key);

        return value instanceof JsonString string
                ? string.value()
                : null;
    }

    /**
     * 按名取数字；缺失或类型不符返回 null。
     */
    public Double getDouble(
            String key
    ) {

        JsonValue value =
                members.get(key);

        return value instanceof JsonNumber number
                ? number.value()
                : null;
    }

    /**
     * 按名取对象；缺失或类型不符返回 null。
     */
    public JsonObject getObject(
            String key
    ) {

        JsonValue value =
                members.get(key);

        return value instanceof JsonObject object
                ? object
                : null;
    }

    /**
     * 按名取数组；缺失或类型不符返回 null。
     */
    public JsonArray getArray(
            String key
    ) {

        JsonValue value =
                members.get(key);

        return value instanceof JsonArray array
                ? array
                : null;
    }

    @Override
    public boolean isObject() {

        return true;
    }

    @Override
    public boolean isArray() {

        return false;
    }

    @Override
    public boolean isString() {

        return false;
    }

    @Override
    public boolean isNumber() {

        return false;
    }

    @Override
    public boolean isBoolean() {

        return false;
    }

    @Override
    public boolean isNull() {

        return false;
    }

    @Override
    public JsonObject asObject() {

        return this;
    }

    @Override
    public JsonArray asArray() {

        throw new IllegalStateException(
                "Not an array."
        );
    }

    @Override
    public JsonString asString() {

        throw new IllegalStateException(
                "Not a string."
        );
    }

    @Override
    public JsonNumber asNumber() {

        throw new IllegalStateException(
                "Not a number."
        );
    }

    @Override
    public JsonBoolean asBoolean() {

        throw new IllegalStateException(
                "Not a boolean."
        );
    }

    @Override
    public String toString() {

        return members.toString();
    }
}
