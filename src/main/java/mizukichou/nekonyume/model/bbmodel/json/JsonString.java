package mizukichou.nekonyume.model.bbmodel.json;

/**
 * JSON 字符串（只读）。
 */
public final class JsonString implements JsonValue {

    private final String value;

    JsonString(
            String value
    ) {

        this.value = value;
    }

    public String value() {

        return value;
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

        return true;
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

        throw new IllegalStateException(
                "Not an array."
        );
    }

    @Override
    public JsonString asString() {

        return this;
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

        return "\"" + value + "\"";
    }
}
