package mizukichou.nekonyume.model.bbmodel.json;

/**
 * JSON 数字（只读，统一 double 存储）。
 *
 * <p>
 * Blockbench 坐标/UV 数值远小于 2^53，
 * double 表示不失真；整型转换由调用方负责范围检查。
 * </p>
 */
public final class JsonNumber implements JsonValue {

    private final double value;

    JsonNumber(
            double value
    ) {

        this.value = value;
    }

    public double value() {

        return value;
    }

    /**
     * 截断为 int（调用方负责范围语义）。
     */
    public int intValue() {

        return (int) value;
    }

    public boolean isIntegral() {

        return value == Math.rint(value);
    }

    @Override
    public boolean isObject() {

        return false;
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

        return true;
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

        return this;
    }

    @Override
    public JsonBoolean asBoolean() {

        throw new IllegalStateException(
                "Not a boolean."
        );
    }

    @Override
    public String toString() {

        return Double.toString(
                value
        );
    }
}
