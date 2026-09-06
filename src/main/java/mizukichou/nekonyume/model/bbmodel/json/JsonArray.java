package mizukichou.nekonyume.model.bbmodel.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JSON 数组（只读）。
 */
public final class JsonArray implements JsonValue {

    private final List<JsonValue> items;

    JsonArray(
            List<JsonValue> items
    ) {

        this.items =
                Collections.unmodifiableList(
                        new ArrayList<>(items)
                );
    }

    public int size() {

        return items.size();
    }

    public JsonValue get(
            int index
    ) {

        return items.get(
                index
        );
    }

    /**
     * 元素（不可修改视图）。
     */
    public List<JsonValue> items() {

        return items;
    }

    /**
     * 提取 {@code [x, y, z]} 数字三元组；缺失/类型不符抛
     * {@link IllegalArgumentException}（导入器用它报结构错误）。
     */
    public double[] toDoubleArray(
            int expectedLength
    ) {

        if (size() != expectedLength) {
            throw new IllegalArgumentException(
                    "Expected " + expectedLength +
                            " numbers, got " + size()
            );
        }

        double[] result =
                new double[expectedLength];

        for (int i = 0; i < expectedLength; i++) {

            JsonValue value =
                    items.get(i);

            if (!(value instanceof JsonNumber number)) {
                throw new IllegalArgumentException(
                        "Element " + i +
                                " is not a number."
                );
            }

            result[i] =
                    number.value();
        }

        return result;
    }

    @Override
    public boolean isObject() {

        return false;
    }

    @Override
    public boolean isArray() {

        return true;
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

        throw new IllegalStateException(
                "Not an object."
        );
    }

    @Override
    public JsonArray asArray() {

        return this;
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

        return items.toString();
    }
}
