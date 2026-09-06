package mizukichou.nekonyume.model.bbmodel.json;

/**
 * JSON 布尔（只读）。
 */
public final class JsonBoolean implements JsonValue {

    private final boolean value;

    JsonBoolean(
            boolean value
    ) {

        this.value = value;
    }

    public boolean value() {

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

        return false;
    }

    @Override
    public boolean isNumber() {

        return false;
    }

    @Override
    public boolean isBoolean() {

        return true;
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

        throw new IllegalStateException(
                "Not a number."
        );
    }

    @Override
    public JsonBoolean asBoolean() {

        return this;
    }

    @Override
    public String toString() {

        return Boolean.toString(
                value
        );
    }
}
