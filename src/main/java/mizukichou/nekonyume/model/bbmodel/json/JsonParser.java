package mizukichou.nekonyume.model.bbmodel.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小 JSON 解析器（只读、递归下降）。
 *
 * <p>
 * 专为 .bbmodel 导入而写（架构 §44：JSON 类型
 * 只允许出现在格式边界内）。支持对象、数组、字符串
 * （含 4 位十六进制 Unicode 转义）、数字、布尔、null；
 * 错误携带行列位置；深度上限防恶意嵌套栈溢出。
 * </p>
 */
public final class JsonParser {

    private static final int MAX_DEPTH = 128;

    private final String text;

    private int position;

    private int line = 1;

    private int column = 1;

    private JsonParser(
            String text
    ) {

        this.text = text;
    }

    /**
     * 解析整个输入；尾随非空白内容视为错误。
     */
    public static JsonValue parse(
            String text
    ) throws JsonParseException {

        if (text == null) {
            throw new IllegalArgumentException(
                    "text must not be null."
            );
        }

        JsonParser parser =
                new JsonParser(text);

        parser.skipWhitespace();

        JsonValue value =
                parser.parseValue(0);

        parser.skipWhitespace();

        if (parser.position < text.length()) {
            throw parser.error(
                    "Unexpected trailing content"
            );
        }

        return value;
    }

    private JsonValue parseValue(
            int depth
    ) throws JsonParseException {

        if (depth > MAX_DEPTH) {
            throw error(
                    "Maximum nesting depth exceeded"
            );
        }

        if (position >= text.length()) {
            throw error(
                    "Unexpected end of input"
            );
        }

        char ch =
                text.charAt(position);

        switch (ch) {

            case '{':
                return parseObject(depth);

            case '[':
                return parseArray(depth);

            case '"':
                return parseString();

            case 't':
            case 'f':
                return parseBoolean();

            case 'n':
                return parseNull();

            default:
                if (ch == '-' ||
                        (ch >= '0' && ch <= '9')) {

                    return parseNumber();
                }

                throw error(
                        "Unexpected character '" +
                                ch + "'"
                );
        }
    }

    private JsonObject parseObject(
            int depth
    ) throws JsonParseException {

        expect('{');

        Map<String, JsonValue> members =
                new LinkedHashMap<>();

        skipWhitespace();

        if (peek() == '}') {

            advance();

            return new JsonObject(
                    members
            );
        }

        while (true) {

            skipWhitespace();

            if (peek() != '"') {
                throw error(
                        "Expected string key"
                );
            }

            JsonString key =
                    parseString();

            skipWhitespace();

            expect(':');

            skipWhitespace();

            JsonValue value =
                    parseValue(depth + 1);

            /*
             * P2-6：JSON 重复键视为错误（静默覆盖会隐藏
             * 模型文件损坏）。
             */
            if (members.put(
                            key.value(),
                            value
                    ) != null) {

                throw error(
                        "Duplicate object key '"
                                + key.value() + "'"
                );
            }

            skipWhitespace();

            char next =
                    peek();

            if (next == ',') {

                advance();
                continue;
            }

            if (next == '}') {

                advance();
                break;
            }

            throw error(
                    "Expected ',' or '}'"
            );
        }

        return new JsonObject(
                members
        );
    }

    private JsonArray parseArray(
            int depth
    ) throws JsonParseException {

        expect('[');

        List<JsonValue> items =
                new ArrayList<>();

        skipWhitespace();

        if (peek() == ']') {

            advance();

            return new JsonArray(
                    items
            );
        }

        while (true) {

            skipWhitespace();

            JsonValue value =
                    parseValue(depth + 1);

            items.add(
                    value
            );

            skipWhitespace();

            char next =
                    peek();

            if (next == ',') {

                advance();
                continue;
            }

            if (next == ']') {

                advance();
                break;
            }

            throw error(
                    "Expected ',' or ']'"
            );
        }

        return new JsonArray(
                items
        );
    }

    private JsonString parseString()
            throws JsonParseException {

        expect('"');

        StringBuilder builder =
                new StringBuilder();

        while (true) {

            if (position >= text.length()) {
                throw error(
                        "Unterminated string"
                );
            }

            char ch =
                    advance();

            if (ch == '"') {
                break;
            }

            if (ch == '\\') {

                char escaped =
                        advance();

                switch (escaped) {

                    case '"':
                    case '\\':
                    case '/':
                        builder.append(escaped);
                        break;

                    case 'b':
                        builder.append('\b');
                        break;

                    case 'f':
                        builder.append('\f');
                        break;

                    case 'n':
                        builder.append('\n');
                        break;

                    case 'r':
                        builder.append('\r');
                        break;

                    case 't':
                        builder.append('\t');
                        break;

                    case 'u':
                        builder.append(
                                parseUnicodeEscape()
                        );
                        break;

                    default:
                        throw error(
                                "Invalid escape sequence '\\" +
                                        escaped + "'"
                        );
                }

                continue;
            }

            if (ch < 0x20) {
                throw error(
                        "Unescaped control character in string"
                );
            }

            builder.append(ch);
        }

        return new JsonString(
                builder.toString()
        );
    }

    private char parseUnicodeEscape()
            throws JsonParseException {

        if (position + 4 > text.length()) {
            throw error(
                    "Incomplete unicode escape"
            );
        }

        int codePoint = 0;

        for (int i = 0; i < 4; i++) {

            char hex =
                    advance();

            int digit =
                    Character.digit(hex, 16);

            if (digit < 0) {
                throw error(
                        "Invalid unicode escape digit '" +
                                hex + "'"
                );
            }

            codePoint =
                    codePoint * 16 + digit;
        }

        return (char) codePoint;
    }

    private JsonNumber parseNumber()
            throws JsonParseException {

        int start =
                position;

        if (peek() == '-') {
            advance();
        }

        if (position >= text.length() ||
                !isDigit(peek())) {

            throw error(
                    "Invalid number"
            );
        }

        /*
         * JSON 不允许前导零（01 非法，0.5 合法）。
         */
        if (peek() == '0') {

            int next =
                    position + 1;

            if (next < text.length() &&
                    isDigit(text.charAt(next))) {

                throw error(
                        "Leading zeros are not allowed"
                );
            }
        }

        while (position < text.length() &&
                isDigit(peek())) {

            advance();
        }

        if (peek() == '.') {

            advance();

            if (position >= text.length() ||
                    !isDigit(peek())) {

                throw error(
                        "Invalid number: expected digit after '.'"
                );
            }

            while (position < text.length() &&
                    isDigit(peek())) {

                advance();
            }
        }

        if (peek() == 'e' ||
                peek() == 'E') {

            advance();

            if (peek() == '+' ||
                    peek() == '-') {

                advance();
            }

            if (position >= text.length() ||
                    !isDigit(peek())) {

                throw error(
                        "Invalid number: expected exponent digit"
                );
            }

            while (position < text.length() &&
                    isDigit(peek())) {

                advance();
            }
        }

        String token =
                text.substring(start, position);

        double value;

        try {

            value =
                    Double.parseDouble(token);

        } catch (NumberFormatException exception) {

            throw error(
                    "Invalid number '" + token + "'"
            );
        }

        if (!Double.isFinite(value)) {
            throw error(
                    "Number out of range"
            );
        }

        return new JsonNumber(
                value
        );
    }

    private JsonBoolean parseBoolean()
            throws JsonParseException {

        if (peek() == 't') {

            expectLiteral("true");

            return new JsonBoolean(
                    true
            );
        }

        expectLiteral("false");

        return new JsonBoolean(
                false
        );
    }

    private JsonNull parseNull()
            throws JsonParseException {

        expectLiteral("null");

        return JsonNull.INSTANCE;
    }

    private void expectLiteral(
            String literal
    ) throws JsonParseException {

        if (position + literal.length() > text.length()) {
            throw error(
                    "Unexpected end of input"
            );
        }

        if (!text.startsWith(literal, position)) {
            throw error(
                    "Invalid literal"
            );
        }

        for (int i = 0;
                i < literal.length();
                i++) {

            advance();
        }
    }

    private void expect(
            char expected
    ) throws JsonParseException {

        if (peek() != expected) {
            throw error(
                    "Expected '" + expected +
                            "' but got '" + peek() + "'"
            );
        }

        advance();
    }

    private char peek() {

        return position < text.length()
                ? text.charAt(position)
                : '\0';
    }

    private char advance() {

        char ch =
                text.charAt(position);

        position++;

        if (ch == '\n') {

            line++;
            column = 1;

        } else {

            column++;
        }

        return ch;
    }

    private void skipWhitespace() {

        while (position < text.length()) {

            char ch =
                    text.charAt(position);

            if (ch != ' ' &&
                    ch != '\t' &&
                    ch != '\n' &&
                    ch != '\r') {

                break;
            }

            advance();
        }
    }

    private static boolean isDigit(
            char ch
    ) {

        return ch >= '0' && ch <= '9';
    }

    private JsonParseException error(
            String message
    ) {

        return new JsonParseException(
                message,
                position,
                line,
                column
        );
    }
}
