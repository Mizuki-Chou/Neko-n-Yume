package mizukichou.nekonyume.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResourceIdTest {

    @Test
    void parsesNamespaceAndPath() {

        ResourceId id =
                ResourceId.parse(
                        "cats:black_cat"
                );

        assertEquals("cats", id.getNamespace());
        assertEquals("black_cat", id.getPath());
        assertEquals("cats:black_cat", id.toString());
    }

    @Test
    void allowsSubPathWithSlash() {

        ResourceId id =
                ResourceId.parse(
                        "nekonyume:models/cats/black_cat"
                );

        assertEquals(
                "models/cats/black_cat",
                id.getPath()
        );
    }

    @Test
    void rejectsInvalidInputs() {

        assertNull(ResourceId.parse(null));
        assertNull(ResourceId.parse(""));
        assertNull(ResourceId.parse("   "));
        assertNull(ResourceId.parse("noColon"));
        assertNull(ResourceId.parse(":path"));
        assertNull(ResourceId.parse("namespace:"));
        assertNull(ResourceId.parse("UPPER:path"));
        assertNull(ResourceId.parse("ns:path with space"));
        assertNull(ResourceId.parse("ns:a..b"));
        assertNull(ResourceId.parse("ns:path/../escape"));
    }

    @Test
    void equalsAndHashCodeFollowValue() {

        ResourceId a =
                ResourceId.parse("cats:black_cat");

        ResourceId b =
                ResourceId.parse("cats:black_cat");

        ResourceId c =
                ResourceId.parse("cats:white_cat");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(false, a.equals(c));
    }
}
