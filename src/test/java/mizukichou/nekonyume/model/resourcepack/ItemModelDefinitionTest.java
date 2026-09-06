package mizukichou.nekonyume.model.resourcepack;

import mizukichou.nekonyume.model.bbmodel.json.JsonFactory;
import mizukichou.nekonyume.model.bbmodel.json.JsonObject;
import mizukichou.nekonyume.model.bbmodel.json.JsonParseException;
import mizukichou.nekonyume.model.bbmodel.json.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Item Model Definition AST（知识包 P0-1：minecraft:model）。
 */
class ItemModelDefinitionTest {

    @Test
    void plainModelSerializesToModernDefinition() throws JsonParseException {

        PlainItemModel plain =
                new PlainItemModel(
                        "nekonyume:cats/test_cat/body"
                );

        JsonObject definition =
                JsonParser.parse(
                                JsonFactory.write(
                                        plain.toItemDefinition()
                                )
                        )
                        .asObject();

        JsonObject model =
                definition.getObject(
                        "model"
                );

        assertEquals(
                "minecraft:model",
                model.getString(
                        "type"
                )
        );

        assertEquals(
                "nekonyume:cats/test_cat/body",
                model.getString(
                        "model"
                )
        );
    }

    @Test
    void rejectsBlankModel() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new PlainItemModel(
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PlainItemModel(
                        "  "
                )
        );
    }
}
