package mizukichou.nekonyume.lang;

import mizukichou.nekonyume.config.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 语言文本组件。
 * 玩家可见文案全部集中在 lang/&lt;code&gt;.yml（jar 内建：
 * zh_cn / en_us / ja_jp），
 * 可用服务器端 plugins/NekoNYume/lang/&lt;code&gt;.yml 覆盖。
 * /nekoyumeadmin reload 时热重载。
 * 0.7.1：按玩家客户端语言自动选择（Player.locale()）：
 * zh_* → zh_cn、en_* → en_us、ja_* → ja_jp，
 * 其余（如 ko / fr / de …）统一回退英语（en_us）；
 * config 的 language 仅用于控制台/全局场景。
 * 玩家可用 /nekoyume language &lt;auto|zh_cn|en_us|ja_jp&gt;
 * 设置个人覆盖（仅内存，重启后回到 auto）。
 * 注入安全铁律：
 * 模板只包含固定文案；动态参数通过占位符 {0} {1} …
 * 在 MiniMessage 解析完成后替换为 Component，
 * 玩家可控文本绝不会被当作标签解析。
 */
public final class Lang {

    /*
     * 支持的完整语言代码集（决定 resolveLocale 的映射）。
     */
    private static final String DEFAULT_CODE = "zh_cn";

    private final JavaPlugin plugin;

    private final ConfigManager configManager;

    private final Logger logger;

    /*
     * 默认语言（config: language），reload 时刷新。
     */
    private volatile String defaultCode =
            DEFAULT_CODE;

    /*
     * 已加载语言实例缓存：code → messages。
     * reload 时清空。
     */
    private final Map<String, LangMessages> cache =
            new ConcurrentHashMap<>();

    /*
     * 默认语言实例（控制台 / 广播回退等使用）。
     */
    private volatile LangMessages fallback;

    /*
     * 玩家个人语言覆盖（仅内存，重启后回到 auto）。
     * "auto" = 跟随客户端语言。
     */
    private final Map<UUID, String> overrides =
            new ConcurrentHashMap<>();

    public Lang(
            JavaPlugin plugin,
            ConfigManager configManager,
            Logger logger
    ) {

        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = logger;

        reload();
    }

    /**
     * 重载：
     * 清空缓存、重读默认语言、
     * 重新加载默认语言实例。
     */
    public void reload() {

        cache.clear();

        String code =
                configManager.snapshot()
                        .getLanguage();

        if (code == null ||
                code.isBlank()) {

            code = DEFAULT_CODE;
        }

        defaultCode = code;

        fallback =
                messagesFor(
                        code
                );
    }

    /*
     * ============================================================
     * 语言解析
     * ============================================================
     */

    /**
     * 按发送者解析语言：
     * 玩家 → 个人覆盖 &gt; 客户端语言 &gt; 默认；
     * 控制台 → 默认。
     */
    public LangMessages forSender(
            CommandSender sender
    ) {

        if (sender instanceof Player player) {

            return forPlayer(
                    player
            );
        }

        return fallback;
    }

    /**
     * 按玩家解析语言。
     */
    public LangMessages forPlayer(
            Player player
    ) {

        if (player == null) {
            return fallback;
        }

        UUID playerUuid =
                player.getUniqueId();

        String override =
                overrides.get(
                        playerUuid
                );

        if (override != null &&
                !"auto".equalsIgnoreCase(
                        override
                )) {

            return messagesFor(
                    override
            );
        }

        return messagesFor(
                resolveLocale(
                        player
                )
        );
    }

    /**
     * 默认语言实例（控制台 / 全局场景）。
     */
    public LangMessages fallback() {

        return fallback;
    }

    /*
     * ============================================================
     * 玩家个人覆盖（/nekoyume language）
     * ============================================================
     */

    public void setOverride(
            UUID playerUuid,
            String code
    ) {

        if (playerUuid == null) {
            return;
        }

        if (code == null ||
                code.isBlank() ||
                "auto".equalsIgnoreCase(
                        code
                )) {

            overrides.remove(
                    playerUuid
            );

        } else {

            overrides.put(
                    playerUuid,
                    code
            );
        }
    }

    public String getOverride(
            UUID playerUuid
    ) {

        if (playerUuid == null) {
            return "auto";
        }

        return overrides.getOrDefault(
                playerUuid,
                "auto"
        );
    }

    /*
     * ============================================================
     * 默认语言快捷方法（控制台 / 全局广播）
     * ============================================================
     */

    public Component message(
            String key,
            String... rawArgs
    ) {

        return fallback.message(
                key,
                rawArgs
        );
    }

    public Component messageComponents(
            String key,
            Component... args
    ) {

        return fallback.messageComponents(
                key,
                args
        );
    }

    public String text(
            String key,
            String... args
    ) {

        return fallback.text(
                key,
                args
        );
    }

    public List<String> textList(
            String key
    ) {

        return fallback.textList(
                key
        );
    }

    /*
     * ============================================================
     * 内部
     * ============================================================
     */

    /*
     * 客户端语言 → 插件语言代码。
     *
     * Player.locale() 返回 Adventure 的 Locale
     * （如 zh_CN / en_US / ja_JP / zh_TW）。
     * 按语言前缀映射：zh_* → zh_cn、en_* → en_us、
     * ja_* → ja_jp，其余回退默认。
     */
    private String resolveLocale(
            Player player
    ) {

        try {

            Locale locale =
                    player.locale();

            if (locale != null) {

                String code =
                        locale.toLanguageTag()
                                .toLowerCase(
                                        Locale.ROOT
                                )
                                .replace(
                                        '-',
                                        '_'
                                );

                if (code.length() >= 2) {

                    return switch (
                            code.substring(
                                    0,
                                    2
                            )
                            ) {

                        case "zh" ->
                                "zh_cn";

                        case "en" ->
                                "en_us";

                        case "ja" ->
                                "ja_jp";
                        /*
                         * 不匹配任何支持语言时，
                         * 统一回退英语（en_us）：
                         * 英语是覆盖面最广的通用语言。
                         * 服务器端 config 的 language
                         * 仍作为控制台/全局场景的默认语言。
                         */
                        default ->
                                "en_us";
                    };

                }
            }

        } catch (Exception ignored) {

            /*
             * locale 读取异常时静默回退默认。
             */
        }

        return defaultCode;
    }

    /*
     * 按代码取语言实例（惰性加载 + 缓存）。
     * 代码不存在 / 加载失败时，LangMessages 内部回退 zh_cn。
     */
    private LangMessages messagesFor(
            String code
    ) {

        if (code == null ||
                code.isBlank()) {

            code = defaultCode;
        }

        return cache.computeIfAbsent(
                code,
                c -> LangMessages.load(
                        plugin,
                        c,
                        logger
                )
        );
    }
}