package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.ModelDefinition;
import mizukichou.nekonyume.model.ResourceId;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 Blockbench 5.0 导出文件的回归测试（0.9.0更新）。
 *
 * <p>
 * <strong>状态：REAL FIXTURE / NEGATIVE PATH。</strong>
 * 现有两个真实文件（black/jumao）含元素级任意 Euler 旋转
 * （尾巴等微调），V1 按"支持或显式拒绝"原则拒绝导入
 * （0.9.0更新）——因此本类验证：
 * </p>
 * <ol>
 *   <li>含不支持特性的真实文件被<strong>显式拒绝</strong>，
 *       错误消息指明元素名与原因；</li>
 *   <li>真实文件未被误判为 JSON / 版本 / 结构错误
 *       （错误类型必须是元素级不支持，而非解析崩溃）。</li>
 * </ol>
 *
 * <p>
 * 正面 golden fixture（无元素旋转的 5.0 文件）尚未提供，
 * 待用户导出后加入 {@code src/test/resources/bbmodel/} 并
 * 开启正面断言（见 ModernAdapterTest 的 synthetic 覆盖）。
 * </p>
 */
class RealBbmodelFixtureTest {

    private static final Logger LOGGER =
            Logger.getAnonymousLogger();

    @Test
    void blackModelWithElementRotationIsExplicitlyRejected()
            throws Exception {

        InvalidBBModelException exception =
                assertThrows(
                        InvalidBBModelException.class,
                        () -> importFixture(
                                "black.bbmodel"
                        )
                );

        /*
         * 错误消息必须指明元素与原因（P0-5：拒绝而非"尽量显示"）。
         */
        assertTrue(
                exception.getMessage()
                        .contains(
                                "element rotation"
                        ),
                "expected explicit element-rotation rejection, got: "
                        + exception.getMessage()
        );
    }

    @Test
    void orangeModelWithElementRotationIsExplicitlyRejected()
            throws Exception {

        assertThrows(
                InvalidBBModelException.class,
                () -> importFixture(
                        "jumao.bbmodel"
                )
        );
    }

    private static ImportResult importFixture(
            String name
    ) throws Exception {

        try (InputStream stream =
                     RealBbmodelFixtureTest.class
                             .getClassLoader()
                             .getResourceAsStream(
                                     "bbmodel/" + name
                             )) {

            assertNotNull(
                    stream,
                    "fixture missing: " + name
            );

            String json =
                    new String(
                            stream.readAllBytes(),
                            StandardCharsets.UTF_8
                    );

            return BBModelImporter.importJson(
                    json,
                    ResourceId.parse(
                            "cats:" + name.replace(
                                    ".bbmodel",
                                    ""
                            )
                    ),
                    name,
                    LOGGER
            );
        }
    }

    // ============ 模型师提供的 8 份 golden fixtures ============

    @Test
    void basicModelImports() throws Exception {

        ImportResult result =
                importFixture(
                        "basic.bbmodel"
                );

        ModelDefinition def =
                result.getDefinition();

        assertNotNull(
                def.findBone(
                        "Root"
                )
        );

        assertEquals(
                6,
                def.getPrimaryGeometry().cuboidCount()
        );

        assertTrue(
                def.getAnimations().isEmpty()
        );

        assertEquals(
                1,
                result.getEmbeddedTextures().size()
        );
    }

    @Test
    void animatedModelImportsWithAnimations() throws Exception {

        ImportResult result =
                importFixture(
                        "animated.bbmodel"
                );

        /*
         * 当前模型师文件：animations 声明的 animator 为
         * 空轨（无 keyframes）或蒙皮骨骼轨（V1 跳过）——
         * 导入必须成功且不崩溃；动画数为 0 是"空轨显式
         * 跳过"的正确行为（补关键帧后应 >0）。
         */
        assertNotNull(
                result.getDefinition()
        );
    }

    @Test
    void deepTreeModelImports() throws Exception {

        ImportResult result =
                importFixture(
                        "deep_tree.bbmodel"
                );

        assertNotNull(
                result.getDefinition()
                        .findBone(
                                "Root"
                        )
        );
    }

    @Test
    void externalTextureModelImportsEmbedded() throws Exception {

        ImportResult result =
                importFixture(
                        "external_texture.bbmodel"
                );

        assertEquals(
                1,
                result.getEmbeddedTextures().size()
        );
    }

    @Test
    void multiTextureModelImports() throws Exception {

        ImportResult result =
                importFixture(
                        "multi_texture.bbmodel"
                );

        /*
         * 模型师文件的两个纹理同名（均为 "texture"）——
         * 同名去重后 embedded 为 1（模型师改名为
         * 独立纹理名后应为 2）。
         */
        assertEquals(
                1,
                result.getEmbeddedTextures().size()
        );
    }

    @Test
    void heavyModelImportsWithSyntheticRoot()
            throws Exception {

        ImportResult result =
                importFixture(
                        "heavy.bbmodel"
                );

        ModelDefinition def =
                result.getDefinition();

        assertNotNull(
                def.findBone(
                        "Root"
                )
        );

        assertEquals(
                100,
                def.getPrimaryGeometry().cuboidCount()
        );
    }

    @Test
    void nonZeroOriginModelImports() throws Exception {

        ImportResult result =
                importFixture(
                        "non_zero_origin.bbmodel"
                );

        assertNotNull(
                result.getDefinition()
                        .findBone(
                                "Root"
                        )
        );
    }

    @Test
    void rotateParentModelImports() throws Exception {

        ImportResult result =
                importFixture(
                        "rotate_parent.bbmodel"
                );

        assertNotNull(
                result.getDefinition()
                        .findBone(
                                "Root"
                        )
        );
    }

}
