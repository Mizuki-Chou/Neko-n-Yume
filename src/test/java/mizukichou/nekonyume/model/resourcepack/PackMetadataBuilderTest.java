package mizukichou.nekonyume.model.resourcepack;

import mizukichou.nekonyume.model.bbmodel.json.JsonFactory;
import mizukichou.nekonyume.model.bbmodel.json.JsonObject;
import mizukichou.nekonyume.model.bbmodel.json.JsonParseException;
import mizukichou.nekonyume.model.bbmodel.json.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * pack.mcmeta 生成（知识包 P0-2：26.2 major.minor schema）。
 */
class PackMetadataBuilderTest {

    @Test
    void buildsMajorMinorMetadata() throws JsonParseException {

        JsonObject meta =
                PackMetadataBuilder.build(
                        MinecraftResourcePackVersion.MINECRAFT_26_2
                );

        /*
         * 序列化 → 解析往返，确保输出是合法 JSON。
         */
        JsonObject parsed =
                JsonParser.parse(
                                JsonFactory.write(
                                        meta
                                )
                        )
                        .asObject();

        JsonObject pack =
                parsed.getObject(
                        "pack"
                );

        assertVersion(
                pack.getArray("min_format"),
                88,
                0
        );

        assertVersion(
                pack.getArray("max_format"),
                88,
                0
        );

        assertEquals(
                PackMetadataBuilder.DESCRIPTION,
                pack.getString(
                        "description"
                )
        );
    }

    @Test
    void rejectsNullFormat() {

        assertThrows(
                IllegalArgumentException.class,
                () -> PackMetadataBuilder.build(
                        null
                )
        );
    }

    @Test
    void rejectsNegativeComponents() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new PackFormat(
                        -1,
                        0
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PackFormat(
                        0,
                        -1
                )
        );
    }

    private static void assertVersion(
            mizukichou.nekonyume.model.bbmodel.json.JsonArray version,
            int major,
            int minor
    ) {

        assertEquals(2, version.size());

        assertEquals(
                major,
                version.get(0)
                        .asNumber()
                        .intValue()
        );

        assertEquals(
                minor,
                version.get(1)
                        .asNumber()
                        .intValue()
        );
    }
}
