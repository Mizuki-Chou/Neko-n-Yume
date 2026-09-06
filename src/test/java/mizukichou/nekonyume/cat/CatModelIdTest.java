package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Cat 的视觉模型 ID 字段测试（Generic Model 系统，预留）。
 *
 * <p>
 * 语义：{@code null} = 未指定（运行时回退默认模型 / 原版视觉）；
 * setter 与 variant 一致——blank / null 归一化为 null，非空 trim。
 * </p>
 */
class CatModelIdTest {

    private Cat newCat() {

        return Cat.createNew(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Mikan"
        );
    }

    @Test
    void defaultsToNull() {

        Cat cat = newCat();

        assertNull(cat.getModelId());
    }

    @Test
    void blankAndNullNormalizeToNull() {

        Cat cat = newCat();

        cat.setModelId(null);

        assertNull(cat.getModelId());

        cat.setModelId("");

        assertNull(cat.getModelId());

        cat.setModelId("   ");

        assertNull(cat.getModelId());
    }

    @Test
    void nonBlankIsTrimmed() {

        Cat cat = newCat();

        cat.setModelId("  cats:black_cat  ");

        assertEquals(
                "cats:black_cat",
                cat.getModelId()
        );
    }

    @Test
    void roundTripPreservesValue() {

        Cat cat = newCat();

        cat.setModelId("cats:siamese");

        assertEquals(
                "cats:siamese",
                cat.getModelId()
        );

        /*
         * 清除后回到默认。
         */
        cat.setModelId("");

        assertNull(cat.getModelId());
    }
}
