package mizukichou.nekonyume.model.bbmodel.json;

/**
 * JSON 值（只读）。
 *
 * <p>
 * 仅用于 BBModelImporter 内部（架构 §44：
 * 该类型不得出现在 model 包之外的业务代码）。
 * 类型判定用 isXxx()；asXxx() 在类型不符时抛
 * {@link IllegalStateException}（编程错误）。
 * </p>
 */
public interface JsonValue {

    boolean isObject();

    boolean isArray();

    boolean isString();

    boolean isNumber();

    boolean isBoolean();

    boolean isNull();

    JsonObject asObject();

    JsonArray asArray();

    JsonString asString();

    JsonNumber asNumber();

    JsonBoolean asBoolean();
}
