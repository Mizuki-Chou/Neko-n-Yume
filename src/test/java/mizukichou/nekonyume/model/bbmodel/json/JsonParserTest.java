package mizukichou.nekonyume.model.bbmodel.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonParserTest {

    @Test
    void parsesScalars() throws Exception {

        assertEquals(
                "hello",
                JsonParser.parse("\"hello\"")
                        .asString()
                        .value()
        );

        assertEquals(
                42.0,
                JsonParser.parse("42")
                        .asNumber()
                        .value()
        );

        assertEquals(
                -3.5,
                JsonParser.parse("-3.5")
                        .asNumber()
                        .value()
        );

        assertEquals(
                1000.0,
                JsonParser.parse("1e3")
                        .asNumber()
                        .value()
        );

        assertTrue(
                JsonParser.parse("true")
                        .asBoolean()
                        .value()
        );

        assertTrue(
                JsonParser.parse("null")
                        .isNull()
        );
    }

    @Test
    void parsesNestedStructures() throws Exception {

        JsonValue value =
                JsonParser.parse(
                        "{\"a\":[1,2,{\"b\":\"x\"}],\"c\":{}}"
                );

        JsonObject root =
                value.asObject();

        assertEquals(
                2.0,
                root.getArray("a")
                        .get(1)
                        .asNumber()
                        .value()
        );

        assertEquals(
                "x",
                root.getArray("a")
                        .get(2)
                        .asObject()
                        .getString("b")
        );

        assertNotNull(
                root.getObject("c")
        );
        assertNull(
                root.get("missing")
        );
    }

    @Test
    void parsesStringEscapes() throws Exception {

        JsonValue value =
                JsonParser.parse(
                        "\"a\\\"b\\\\c\\nd\\u0041\""
                );

        assertEquals(
                "a\"b\\c\ndA",
                value.asString().value()
        );
    }

    @Test
    void parsesEmptyStructures() throws Exception {

        assertTrue(
                JsonParser.parse("{}")
                        .asObject()
                        .members()
                        .isEmpty()
        );

        assertEquals(
                0,
                JsonParser.parse("[]")
                        .asArray()
                        .size()
        );
    }

    @Test
    void rejectsMalformedInput() {

        for (String bad :
                java.util.Arrays.asList(
                        "",
                        "{",
                        "[1,]",
                        "{\"a\":}",
                        "{\"a\" 1}",
                        "\"unterminated",
                        "tru",
                        "nulll",
                        "01",
                        "1.",
                        "[1] extra",
                        "{\"a\":1,}",
                        "{\"a\":\"\\q\"}"
                )) {

            assertThrows(
                    JsonParseException.class,
                    () -> JsonParser.parse(bad),
                    "should reject: " + bad
            );
        }
    }

    @Test
    void errorCarriesPosition() {

        JsonParseException exception =
                assertThrows(
                        JsonParseException.class,
                        () -> JsonParser.parse(
                                "{\n  \"a\": [1, 2,\n    oops ]\n}"
                        )
                );

        assertTrue(
                exception.getLine() > 0
        );
        assertTrue(
                exception.getColumn() > 0
        );
        assertTrue(
                exception.getMessage()
                        .contains("line")
        );
    }

    @Test
    void rejectsExcessiveNesting() {

        StringBuilder deep =
                new StringBuilder();

        for (int i = 0; i < 200; i++) {
            deep.append('[');
        }

        assertThrows(
                JsonParseException.class,
                () -> JsonParser.parse(
                        deep.toString()
                )
        );
    }

    @Test
    void typeMismatchAsXxxThrows() throws Exception {

        JsonValue value =
                JsonParser.parse("\"str\"");

        assertFalse(value.isObject());
        assertTrue(value.isString());

        assertThrows(
                IllegalStateException.class,
                value::asArray
        );
    }
}
