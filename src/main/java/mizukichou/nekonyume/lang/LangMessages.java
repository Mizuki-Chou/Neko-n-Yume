package mizukichou.nekonyume.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 语言文本核心（可单元测试）。
 *
 * <p>
 * 占位符规则：
 * 模板中的 {0} {1} … 在 MiniMessage 解析完成之后
 * 被替换为对应的组件参数。
 * 替换发生在解析后，因此参数中的任何
 * MiniMessage 标签字符都只会显示为纯文本。
 * </p>
 *
 * <p>
 * 0.7.1：类与方法改为 public，
 * 供其他包通过 Lang.forPlayer / forSender 直接使用。
 * 加载统一走 loadFromString（实例方法），
 * 规避 Reader 重载在新版 YAML 实现下的兼容问题。
 * </p>
 */
public final class LangMessages {

    private final YamlConfiguration data;

    private final Logger logger;

    private final MiniMessage mm =
            MiniMessage.miniMessage();

    LangMessages(
            YamlConfiguration data,
            Logger logger
    ) {

        this.data = data;
        this.logger = logger;
    }

    /*
     * ============================================================
     * 加载
     * ============================================================
     */

    static LangMessages load(
            JavaPlugin plugin,
            String code,
            Logger logger
    ) {

        if (code == null ||
                code.isBlank()) {

            code = "zh_cn";
        }

        /*
         * 1. 服务器端覆盖文件（管理员可改）。
         */
        File override =
                new File(
                        plugin.getDataFolder(),
                        "lang"
                                + File.separator
                                + code
                                + ".yml"
                );

        if (override.exists() &&
                override.length() > 0) {

            String content;

            try {

                content =
                        Files.readString(
                                override.toPath(),
                                StandardCharsets.UTF_8
                        );

            } catch (IOException e) {

                logger.log(
                        Level.SEVERE,
                        "Failed to read language override: "
                                + override.getPath(),
                        e
                );

                content = null;
            }

            if (content != null &&
                    !content.isBlank()) {

                YamlConfiguration overrideConfig =
                        new YamlConfiguration();

                try {

                    overrideConfig.loadFromString(
                            content
                    );

                } catch (InvalidConfigurationException e) {

                    logger.log(
                            Level.SEVERE,
                            "Failed to parse language override: "
                                    + override.getPath(),
                            e
                    );

                    overrideConfig = null;
                }

                if (overrideConfig != null &&
                        !overrideConfig.getKeys(false).isEmpty()) {

                    logger.info(
                            "Loaded language override: "
                                    + override.getPath()
                    );

                    return new LangMessages(
                            overrideConfig,
                            logger
                    );
                }
            }
        }

        /*
         * 2. 插件内建资源。
         */
        InputStream stream =
                plugin.getResource(
                        "lang/" + code + ".yml"
                );

        if (stream == null &&
                !"zh_cn".equals(code)) {

            logger.warning(
                    "Language '"
                            + code
                            + "' not found, falling back to zh_cn."
            );

            stream =
                    plugin.getResource(
                            "lang/zh_cn.yml"
                    );
        }

        if (stream != null) {

            try {

                String content =
                        new String(
                                stream.readAllBytes(),
                                StandardCharsets.UTF_8
                        );

                YamlConfiguration resourceConfig =
                        new YamlConfiguration();

                resourceConfig.loadFromString(
                        content
                );

                return new LangMessages(
                        resourceConfig,
                        logger
                );

            } catch (IOException | InvalidConfigurationException e) {

                logger.log(
                        Level.SEVERE,
                        "Failed to load language resource lang/"
                                + code
                                + ".yml",
                        e
                );
            }
        }

        logger.warning(
                "No language file available, messages will show raw keys."
        );

        return new LangMessages(
                new YamlConfiguration(),
                logger
        );
    }

    /*
     * ============================================================
     * 聊天消息
     * ============================================================
     */

    public Component message(
            String key,
            String... rawArgs
    ) {

        Component[] args =
                new Component[rawArgs.length];

        for (int i = 0;
             i < rawArgs.length;
             i++) {

            args[i] =
                    Component.text(
                            rawArgs[i] == null
                                    ? ""
                                    : rawArgs[i]
                    );
        }

        return messageComponents(
                key,
                args
        );
    }

    public Component messageComponents(
            String key,
            Component... args
    ) {

        String template =
                data.getString(
                        key
                );

        if (template == null) {

            logger.warning(
                    "Missing language key: "
                            + key
            );

            return Component.text(
                    key
            );
        }

        Component parsed =
                mm.deserialize(
                        template
                );

        if (args.length == 0) {
            return parsed;
        }

        return replacePlaceholders(
                parsed,
                args
        );
    }

    /*
     * ============================================================
     * 纯字符串
     * ============================================================
     */

    public String text(
            String key,
            String... args
    ) {

        String template =
                data.getString(
                        key
                );

        if (template == null) {

            logger.warning(
                    "Missing language key: "
                            + key
            );

            return key;
        }

        String result =
                template;

        for (int i = 0;
             i < args.length;
             i++) {

            result =
                    result.replace(
                            "{" + i + "}",
                            args[i] == null
                                    ? ""
                                    : args[i]
                    );
        }

        return result;
    }

    public List<String> textList(
            String key
    ) {

        if (!data.contains(key)) {

            logger.warning(
                    "Missing language key: "
                            + key
            );
        }

        return data.getStringList(
                key
        );
    }

    /*
     * ============================================================
     * 占位符替换（解析后替换，注入安全）
     * ============================================================
     *
     * MiniMessage 对无嵌套标签的模板会生成单个 TextComponent，
     * 占位符位于其 content 内而非子节点，
     * 因此替换必须同时处理：
     * 1. 本节点 content 中的 {n}（按内容切分）；
     * 2. 子节点递归替换。
     * 占位符继承所在节点的样式（如 <yellow>{0}</yellow>）。
     */

    private Component replacePlaceholders(
            Component node,
            Component[] args
    ) {

        if (!(node instanceof TextComponent text)) {
            return node;
        }

        /*
         * 1. 递归处理子节点。
         */
        List<Component> children =
                text.children();

        List<Component> newChildren =
                new ArrayList<>(
                        children.size()
                );

        boolean childrenChanged =
                false;

        for (Component child :
                children) {

            Component replaced =
                    replacePlaceholders(
                            child,
                            args
                    );

            childrenChanged |=
                    replaced != child;

            newChildren.add(
                    replaced
            );
        }

        /*
         * 2. 切分本节点 content 中的 {n}。
         */
        List<Component> segments =
                splitOnPlaceholders(
                        text.content(),
                        text.style(),
                        args
                );

        if (segments == null &&
                !childrenChanged) {

            return node;
        }

        List<Component> finalChildren =
                new ArrayList<>();

        if (segments != null) {

            finalChildren.addAll(
                    segments
            );
        }

        finalChildren.addAll(
                childrenChanged
                        ? newChildren
                        : children
        );

        return Component.text(
                ""
        ).style(
                text.style()
        ).children(
                finalChildren
        );
    }

    /*
     * 返回 null = content 中没有占位符。
     */
    private List<Component> splitOnPlaceholders(
            String content,
            Style style,
            Component[] args
    ) {

        List<Component> result =
                null;

        int start =
                0;

        int i =
                0;

        while (i < content.length()) {

            char current =
                    content.charAt(i);

            if (current == '{') {

                int end =
                        content.indexOf(
                                '}',
                                i + 1
                        );

                if (end > i + 1 &&
                        isDigits(
                                content.substring(
                                        i + 1,
                                        end
                                )
                        )) {

                    if (result == null) {

                        result =
                                new ArrayList<>();
                    }

                    if (i > start) {

                        result.add(
                                Component.text(
                                                content.substring(
                                                        start,
                                                        i
                                                )
                                        )
                                        .style(
                                                style
                                        )
                        );
                    }

                    int index =
                            Integer.parseInt(
                                    content.substring(
                                            i + 1,
                                            end
                                    )
                            );

                    result.add(
                            index < args.length
                                    ? args[index].style(
                                    style
                            )
                                    : Component.empty()
                    );

                    i = end + 1;

                    start = i;

                    continue;
                }
            }

            i++;
        }

        if (result != null &&
                start < content.length()) {

            result.add(
                    Component.text(
                                    content.substring(
                                            start
                                    )
                            )
                            .style(
                                    style
                            )
            );
        }

        return result;
    }

    private boolean isDigits(
            String value
    ) {

        if (value.isEmpty()) {
            return false;
        }

        for (int i = 0;
             i < value.length();
             i++) {

            if (!Character.isDigit(
                    value.charAt(i)
            )) {

                return false;
            }
        }

        return true;
    }
}
