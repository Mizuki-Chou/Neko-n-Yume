package mizukichou.nekonyume.model.bbmodel.json;

/**
 * JSON null（只读，单例语义）。
 */
public final class JsonNull implements JsonValue {

    static final JsonNull INSTANCE =
            new JsonNull();

    /**
     * 包内可见：单例可经 {@code JsonFactory.nullValue()} 访问。
     */
    JsonNull() {
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

        return false;
    }

    @Override
    public boolean isNull() {

        return true;
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

        throw new IllegalStateException(
                "Not a boolean."
        );
    }

    @Override
    public String toString() {

        return "null";
    }
}
