package mizukichou.nekonyume.model.bbmodel.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON AST 构建 + 序列化往返（知识包 P2-6：AST → serialize）。
 */
class JsonFactoryTest {

    @Test
    void roundTripsComplexStructure() throws JsonParseException {

        JsonObject source =
                JsonFactory.object(
                        Map.of(
                                "string", JsonFactory.string("hello \"world\"\n"),
                                "int", JsonFactory.number(42),
                                "double", JsonFactory.number(3.5),
                                "bool", JsonFactory.bool(true),
                                "nil", JsonFactory.nullValue(),
                                "array", JsonFactory.array(
                                        JsonFactory.number(1),
                                        JsonFactory.string("two"),
                                        JsonFactory.bool(false)
                                ),
                                "nested", JsonFactory.object(
                                        Map.of(
                                                "a", JsonFactory.string("b")
                                        )
                                )
                        )
                );

        String text =
                JsonFactory.write(
                        source
                );

        JsonObject parsed =
                JsonParser.parse(text)
                        .asObject();

        assertEquals(
                "hello \"world\"\n",
                parsed.getString("string")
        );

        assertEquals(
                42,
                parsed.get("int")
                        .asNumber()
                        .intValue()
        );

        assertEquals(
                3.5,
                parsed.get("double")
                        .asNumber()
                        .value(),
                1e-9
        );

        assertTrue(
                parsed.get("bool")
                        .asBoolean()
                        .value()
        );

        assertTrue(
                parsed.get("nil")
                        .isNull()
        );

        JsonArray array =
                parsed.getArray("array");

        assertEquals(3, array.size());
        assertFalse(
                array.get(2)
                        .asBoolean()
                        .value()
        );

        assertEquals(
                "b",
                parsed.getObject("nested")
                        .getString("a")
        );
    }

    @Test
    void emptyContainersSerialize() throws JsonParseException {

        String text =
                JsonFactory.write(
                        JsonFactory.object(
                                Map.of(
                                        "emptyObject",
                                        JsonFactory.object(),
                                        "emptyArray",
                                        JsonFactory.array(
                                                List.of()
                                        )
                                )
                        )
                );

        JsonObject parsed =
                JsonParser.parse(text)
                        .asObject();

        assertTrue(
                parsed.getObject("emptyObject")
                        .members()
                        .isEmpty()
        );

        assertEquals(
                0,
                parsed.getArray("emptyArray")
                        .size()
        );
    }

    @Test
    void escapesControlCharacters() throws JsonParseException {

        String text =
                JsonFactory.write(
                        JsonFactory.string(
                                "tab\tbell\bform\fcr\r"
                        )
                );

        JsonObject wrapper =
                JsonParser.parse(
                                "{\"v\":" + text + "}"
                        )
                        .asObject();

        assertEquals(
                "tab\tbell\bform\fcr\r",
                wrapper.getString("v")
        );
    }

    @Test
    void integralDoublesWriteAsIntegers() throws JsonParseException {

        String text =
                JsonFactory.write(
                        JsonFactory.object(
                                Map.of(
                                        "whole", JsonFactory.number(88.0),
                                        "fraction", JsonFactory.number(0.5)
                                )
                        )
                );

        JsonObject parsed =
                JsonParser.parse(text)
                        .asObject();

        assertEquals(
                88,
                parsed.get("whole")
                        .asNumber()
                        .intValue()
        );

        assertEquals(
                0.5,
                parsed.get("fraction")
                        .asNumber()
                        .value(),
                1e-9
        );
    }

    @Test
    void nullValueIsSingleton() {

        assertTrue(
                JsonFactory.nullValue() ==
                        JsonFactory.nullValue()
        );
    }
}
