package mizukichou.nekonyume.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
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

            code = "en_us";
        }

        YamlConfiguration builtin =
                loadBuiltinBase(
                        plugin,
                        code,
                        logger
                );

        /*
         * 1. 服务器端覆盖文件（管理员可改）。
         *
         * 0.8.1 R5（社区上报）：
         * 覆盖文件是“局部覆盖”——与内建资源深度合并；
         * 只写一个键也不会丢失其余内建文案。
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

                    if (builtin != null) {

                        mergeDeep(
                                builtin,
                                overrideConfig
                        );

                        return new LangMessages(
                                builtin,
                                logger
                        );
                    }

                    return new LangMessages(
                            overrideConfig,
                            logger
                    );
                }
            }
        }

        if (builtin != null) {

            return new LangMessages(
                    builtin,
                    logger
            );
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
     * 加载内建语言资源（含 en_us 回退链）。
     *
     * 0.8.1（用户要求）：全部回退场景统一 en_us——
     * 不支持的语言 / 检测失败 / 文件缺失 / 解析失败。
     * 返回 null 表示 en_us 自身也缺失/损坏。
     */
    private static YamlConfiguration loadBuiltinBase(
            JavaPlugin plugin,
            String code,
            Logger logger
    ) {

        InputStream stream =
                plugin.getResource(
                        "lang/" + code + ".yml"
                );

        if (stream == null &&
                !"en_us".equals(code)) {

            logger.warning(
                    "Language '"
                            + code
                            + "' not found, falling back to en_us."
            );

            stream =
                    plugin.getResource(
                            "lang/en_us.yml"
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

                return resourceConfig;

            } catch (IOException | InvalidConfigurationException e) {

                logger.log(
                        Level.SEVERE,
                        "Failed to load language resource lang/"
                                + code
                                + ".yml",
                        e
                );

                if (!"en_us".equals(code)) {

                    return loadBuiltinBase(
                            plugin,
                            "en_us",
                            logger
                    );
                }
            }
        }

        return null;
    }

    /*
     * 0.8.1 R5（社区上报）：
     * 深度合并——覆盖文件的每个键覆盖内建同路径键，
     * 未提及的键保留内建值；嵌套节逐层递归。
     * 包级可见以便单元测试。
     */
    static void mergeDeep(
            ConfigurationSection target,
            ConfigurationSection source
    ) {

        for (String key :
                source.getKeys(false)) {

            Object sourceValue =
                    source.get(key);

            if (sourceValue instanceof ConfigurationSection sourceSection) {

                ConfigurationSection targetSection =
                        target.getConfigurationSection(
                                key
                        );

                if (targetSection == null) {

                    targetSection =
                            target.createSection(
                                    key
                            );
                }

                mergeDeep(
                        targetSection,
                        sourceSection
                );

            } else {

                target.set(
                        key,
                        sourceValue
                );
            }
        }
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

        Component parsed;

        try {

            parsed =
                    mm.deserialize(
                            template
                    );

        } catch (Exception e) {

            /*
             * 自定义语言覆盖文件可能含非法 MiniMessage（如未闭合标签）：
             * 解析失败降级为纯文本，绝不让一条坏模板击穿全部消息路径。
             */
            logger.warning(
                    "Invalid MiniMessage in language key "
                            + key
                            + ": "
                            + e.getMessage()
            );

            parsed =
                    Component.text(
                            template
                    );
        }

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

        if (args.length == 0) {
            return template;
        }

        /*
         * 单遍扫描替换：
         * 只扫描模板中的 {n}，参数值不再被二次扫描，
         * 避免参数值本身含 {1} 时被后续轮次串改。
         */
        StringBuilder builder =
                new StringBuilder(
                        template.length() + 16
                );

        int start = 0;

        int i = 0;

        while (i < template.length()) {

            char current =
                    template.charAt(i);

            if (current == '{') {

                int end =
                        template.indexOf(
                                '}',
                                i + 1
                        );

                if (end > i + 1 &&
                        isDigits(
                                template.substring(
                                        i + 1,
                                        end
                                )
                        )) {

                    int index =
                            Integer.parseInt(
                                    template.substring(
                                            i + 1,
                                            end
                                    )
                            );

                    builder.append(
                            template,
                            start,
                            i
                    );

                    builder.append(
                            index < args.length
                                    ? args[index] == null
                                    ? ""
                                    : args[index]
                                    : ""
                    );

                    i = end + 1;

                    start = i;

                    continue;
                }
            }

            i++;
        }

        builder.append(
                template,
                start,
                template.length()
        );

        return builder.toString();
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

        /*
         * 0.8.1 修复（P0）：content 无占位符时绝不能丢弃它。
         *
         * 模板形如 <white>等级: <yellow>{0}</yellow></white>
         * 解析后占位符位于子节点、父节点 content="等级: "。
         * 旧实现此处返回 Component.text("")，父节点文本
         * （"等级: "等大量前缀）随替换静默丢失。
         * 此处 segments == null 时保留原 content，仅替换子节点。
         */
        if (segments == null) {

            return Component.text(
                            text.content()
                    ).style(
                            text.style()
                    ).children(
                            childrenChanged
                                    ? newChildren
                                    : children
                    );
        }

        List<Component> finalChildren =
                new ArrayList<>();

        finalChildren.addAll(
                segments
        );

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
                                    ? applyPlaceholderStyle(
                                    args[index],
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

    /*
     * 占位符样式继承规则：
     *
     * - 模板节点样式非空（如 <yellow>{0}</yellow>）→
     *   参数继承模板样式（保持既有语义）；
     * - 模板节点样式为空 → 保留参数自身样式。
     *
     * 修复前对空样式节点执行 .style(empty) 会覆盖参数
     * 自身样式，导致经 LegacyComponentSerializer 反序列化的
     * 彩色参数（如 §6 喵丹名）在普通模板中丢失颜色。
     */
    private Component applyPlaceholderStyle(
            Component arg,
            Style style
    ) {

        if (style == null ||
                style.isEmpty()) {

            return arg;
        }

        return arg.style(
                style
        );
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
