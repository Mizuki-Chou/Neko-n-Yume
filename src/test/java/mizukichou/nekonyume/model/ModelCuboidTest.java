package mizukichou.nekonyume.model;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ModelCuboid 4 值 UV（P0-6）与构造校验。
 */
class ModelCuboidTest {

    private static final Vec3 OFFSET =
            Vec3.ZERO;

    private static final Vec3 SIZE =
            new Vec3(2.0, 2.0, 2.0);

    @Test
    void legacyConstructorBuildsNorthUvRect() {

        ModelCuboid cube =
                new ModelCuboid(
                        "c",
                        OFFSET,
                        SIZE,
                        new Vec2(0.0, 0.0),
                        new Vec2(2.0, 2.0),
                        0,
                        false
                );

        /*
         * 简写构造（uv + uv_size）→ 北面 4 值 UvRect。
         */
        UvRect north =
                cube.getFaces()
                        .get(CubeFace.NORTH);

        assertNotNull(north);

        assertEquals(
                new UvRect(0.0, 0.0, 2.0, 2.0),
                north
        );

        assertTrue(
                cube.hasTexture()
        );
    }

    @Test
    void faceUvRectIsPreserved() {

        Map<CubeFace, UvRect> faces =
                new EnumMap<>(CubeFace.class);

        faces.put(
                CubeFace.NORTH,
                new UvRect(1.0, 2.0, 5.0, 6.0)
        );

        ModelCuboid cube =
                new ModelCuboid(
                        "c",
                        OFFSET,
                        SIZE,
                        0,
                        false,
                        faces
                );

        assertEquals(
                new UvRect(1.0, 2.0, 5.0, 6.0),
                cube.getFaces()
                        .get(CubeFace.NORTH)
        );
    }

    @Test
    void emptyFacesRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelCuboid(
                        "c",
                        OFFSET,
                        SIZE,
                        0,
                        false,
                        new EnumMap<>(CubeFace.class)
                )
        );
    }

    @Test
    void nonPositiveSizeRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelCuboid(
                        "c",
                        OFFSET,
                        new Vec3(0.0, 2.0, 2.0),
                        ModelCuboid.NO_TEXTURE,
                        false,
                        null
                )
        );
    }

    @Test
    void textureIndexBelowSentinelRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelCuboid(
                        "c",
                        OFFSET,
                        SIZE,
                        -2,
                        false,
                        null
                )
        );
    }

    @Test
    void cubeFaceFromName() {

        assertEquals(
                CubeFace.NORTH,
                CubeFace.fromName("north")
        );
        assertEquals(
                CubeFace.UP,
                CubeFace.fromName("UP")
        );
        assertNull(
                CubeFace.fromName("diagonal")
        );
        assertNull(
                CubeFace.fromName(null)
        );
    }

    @Test
    void uvRectRejectsNonFinite() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new UvRect(
                        Double.NaN,
                        0.0,
                        1.0,
                        1.0
                )
        );
    }

    @Test
    void uvRectNormalizesCorners() {

        assertEquals(
                new UvRect(1.0, 2.0, 9.0, 8.0),
                new UvRect(9.0, 8.0, 1.0, 2.0)
                        .normalized()
        );
    }
}
