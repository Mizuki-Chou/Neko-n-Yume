package mizukichou.nekonyume.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语言文本核心测试。
 *
 * <p>
 * 覆盖：占位符替换、样式继承、
 * 玩家文本注入安全、缺失键回退、纯字符串替换。
 * </p>
 */
class LangMessagesTest {

    private final Logger logger =
            Logger.getAnonymousLogger();

    private LangMessages messages(String yaml) {

        YamlConfiguration config =
                new YamlConfiguration();

        try {

            config.loadFromString(
                    yaml
            );

        } catch (org.bukkit.configuration.InvalidConfigurationException e) {

            throw new RuntimeException(
                    e
            );
        }

        return new LangMessages(
                config,
                logger
        );
    }

    @Test
    void simpleMessageWithoutArgs() {

        LangMessages messages =
                messages(
                        "hello: \"<green>你好</green>\""
                );

        Component result =
                messages.message(
                        "hello"
                );

        assertEquals(
                "你好",
                plain(result)
        );
    }

    @Test
    void placeholdersAreReplacedAfterParsing() {

        LangMessages messages =
                messages(
                        "greet: \"<gold>欢迎, {0}!</gold>\""
                );

        Component result =
                messages.message(
                        "greet",
                        "Mikan"
                );

        assertEquals(
                "欢迎, Mikan!",
                plain(result)
        );
    }

    @Test
    void playerTextIsNeverParsedAsTags() {

        /*
         * 注入安全铁律：
         * 玩家可控文本即使包含 MiniMessage 标签，
         * 也只会作为纯文本显示，不会被解析成样式。
         */
        LangMessages messages =
                messages(
                        "greet: \"<gold>欢迎, {0}!</gold>\""
                );

        String malicious =
                "<red>HACK</red>";

        Component result =
                messages.message(
                        "greet",
                        malicious
                );

        assertEquals(
                "欢迎, <red>HACK</red>!",
                plain(result)
        );

        /*
         * 被替换的组件没有红色样式：
         * 它继承的是模板自身的金色（样式继承），
         * 而非被解析出的 <red> 标签——
         * 若标签被解析，颜色会是红色。
         */
        Component arg =
                findChildWithText(
                        result,
                        malicious
                );

        assertNotNull(arg);

        assertEquals(
                "gold",
                arg.style().color() != null
                        ? arg.style()
                        .color()
                        .toString()
                        : null
        );
    }

    @Test
    void placeholderInheritsTemplateStyle() {

        LangMessages messages =
                messages(
                        "food: \"<light_purple>🐱 </light_purple>{0} 吃掉了 <yellow>{1}</yellow>!\""
                );

        Component result =
                messages.message(
                        "food",
                        "Mikan",
                        "生鳕鱼"
                );

        assertEquals(
                "🐱 Mikan 吃掉了 生鳕鱼!",
                plain(result)
        );

        /*
         * {1} 在模板中位于 <yellow> 内，
         * 替换后应继承黄色样式。
         */
        Component food =
                findChildWithText(
                        result,
                        "生鳕鱼"
                );

        assertNotNull(food);

        assertEquals(
                "yellow",
                food.style().color() != null
                        ? food.style()
                        .color()
                        .toString()
                        : null
        );
    }

    @Test
    void numberedPlaceholdersSupportReorder() {

        LangMessages messages =
                messages(
                        "swap: \"{1} 和 {0}\""
                );

        assertEquals(
                "B 和 A",
                plain(
                        messages.message(
                                "swap",
                                "A",
                                "B"
                        )
                )
        );
    }

    @Test
    void missingKeyFallsBackToRawKey() {

        LangMessages messages =
                messages(
                        "exists: \"<green>ok</green>\""
                );

        Component result =
                messages.message(
                        "missing.key"
                );

        assertEquals(
                "missing.key",
                plain(result)
        );
    }

    @Test
    void textReplacesPlainPlaceholders() {

        LangMessages messages =
                messages(
                        "label: \"§7进度: §f{0} / {1}\""
                );

        assertEquals(
                "§7进度: §f3 / 10",
                messages.text(
                        "label",
                        "3",
                        "10"
                )
        );
    }

    @Test
    void textArgsAreNotRescanned() {

        LangMessages messages =
                messages(
                        "pair: \"{0} / {1}\""
                );

        assertEquals(
                "{1} / 999",
                messages.text(
                        "pair",
                        "{1}",
                        "999"
                )
        );
    }

    @Test
    void textListReturnsEntries() {

        LangMessages messages =
                messages(
                        "list:\n  - \"a\"\n  - \"b\""
                );

        assertEquals(
                List.of("a", "b"),
                messages.textList(
                        "list"
                )
        );
    }

    /*
     * ============================================================
     * 工具
     * ============================================================
     */

    private String plain(Component component) {

        StringBuilder builder =
                new StringBuilder();

        collectText(
                component,
                builder
        );

        return builder.toString();
    }

    private void collectText(
            Component component,
            StringBuilder builder
    ) {

        if (component instanceof TextComponent text) {

            builder.append(
                    text.content()
            );
        }

        for (Component child :
                component.children()) {

            collectText(
                    child,
                    builder
            );
        }
    }

    private Component findChildWithText(
            Component root,
            String text
    ) {

        if (root instanceof TextComponent t &&
                t.children().isEmpty() &&
                text.equals(t.content())) {

            return root;
        }

        for (Component child :
                root.children()) {

            Component found =
                    findChildWithText(
                            child,
                            text
                    );

            if (found != null) {
                return found;
            }
        }

        return null;
    }

    @Test
    void rawArgsAreWrappedAsPlainText() {

        LangMessages messages =
                messages(
                        "name: \"名字: {0}\""
                );

        Component result =
                messages.message(
                        "name",
                        "§c带色码的名字"
                );

        assertEquals(
                "名字: §c带色码的名字",
                plain(result)
        );

        assertTrue(
                plain(result).contains("§")
        );
    }

    @Test
    void styledArgKeepsItsStyleWhenTemplateStyleIsEmpty() {

        /*
         * 修复回归测试：
         * 模板节点无样式时，参数必须保留自身样式，
         * 不能被空样式覆盖（否则 legacy 反序列化的
         * 彩色喵丹名会丢失颜色）。
         */
        LangMessages messages =
                messages(
                        "reward: \"你收到了 {0}\""
                );

        Component coloredArg =
                net.kyori.adventure.text.serializer.legacy
                        .LegacyComponentSerializer
                        .legacySection()
                        .deserialize(
                                "§6✨ 至极喵丹"
                        );

        Component result =
                messages.messageComponents(
                        "reward",
                        coloredArg
                );

        assertEquals(
                "你收到了 ✨ 至极喵丹",
                plain(result)
        );

        Component arg =
                findChildWithText(
                        result,
                        "✨ 至极喵丹"
                );

        assertNotNull(arg);

        assertEquals(
                "gold",
                arg.style().color() != null
                        ? arg.style()
                        .color()
                        .toString()
                        : null
        );
    }

    @Test
    void styledArgInheritsTemplateStyleWhenPresent() {

        /*
         * 对照测试：模板节点带样式时，
         * 参数仍继承模板样式（既有语义不变）。
         */
        LangMessages messages =
                messages(
                        "reward: \"<yellow>你收到了 {0}</yellow>\""
                );

        Component coloredArg =
                net.kyori.adventure.text.serializer.legacy
                        .LegacyComponentSerializer
                        .legacySection()
                        .deserialize(
                                "§6✨ 至极喵丹"
                        );

        Component result =
                messages.messageComponents(
                        "reward",
                        coloredArg
                );

        Component arg =
                findChildWithText(
                        result,
                        "✨ 至极喵丹"
                );

        assertNotNull(arg);

        assertEquals(
                "yellow",
                arg.style().color() != null
                        ? arg.style()
                        .color()
                        .toString()
                        : null
        );
    }
}
