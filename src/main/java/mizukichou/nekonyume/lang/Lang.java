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
 *
 * <p>
 * 玩家可见文案全部集中在 lang/&lt;code&gt;.yml（jar 内建：
 * zh_cn / en_us / ja_jp），
 * 可用服务器端 plugins/NekoNYume/lang/&lt;code&gt;.yml 覆盖。
 * /nekoyumeadmin reload 时热重载。
 * </p>
 *
 * <p>
 * 0.7.1：按玩家客户端语言自动选择（Player.locale()）：
 * zh_* → zh_cn、en_* → en_us、ja_* → ja_jp，
 * 其余回退到 config 的 language 默认值。
 * 玩家可用 /nekoyume language &lt;auto|zh_cn|en_us|ja_jp&gt;
 * 设置个人覆盖（仅内存，重启后回到 auto）。
 * </p>
 *
 * <p>
 * 注入安全铁律：
 * 模板只包含固定文案；动态参数通过占位符 {0} {1} …
 * 在 MiniMessage 解析完成后替换为 Component，
 * 玩家可控文本绝不会被当作标签解析。
 * </p>
 */
public final class Lang {

 private static final String DEFAULT_CODE = "zh_cn";

 private final JavaPlugin plugin;
 private final ConfigManager configManager;
 private final Logger logger;

 private volatile String defaultCode =
 DEFAULT_CODE;

 private final Map<String, LangMessages> cache =
 new ConcurrentHashMap<>();

 private volatile LangMessages fallback;

 private final Map<UUID, String> overrides =
 new ConcurrentHashMap<>();

 public Lang(
 JavaPlugin plugin,
 ConfigManager configManager,
 Logger logger ) {

 this.plugin = plugin;
 this.configManager = configManager;
 this.logger = logger;

 reload();
 }

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
 code );
 }

 public LangMessages forSender(
 CommandSender sender ) {

 if (sender instanceof Player player) {

 return forPlayer(
 player );
 }

 return fallback;
 }

 public LangMessages forPlayer(
 Player player ) {

 if (player == null) {
 return fallback;
 }

 UUID playerUuid =
 player.getUniqueId();

 String override =
 overrides.get(
 playerUuid );

 if (override != null &&
 !"auto".equalsIgnoreCase(
 override )) {

 return messagesFor(
 override );
 }

 return messagesFor(
 resolveLocale(
 player ) );
 }

 public LangMessages fallback() {
 return fallback;
 }

 public void setOverride(
 UUID playerUuid,
 String code ) {

 if (playerUuid == null) {
 return;
 }

 if (code == null ||
 code.isBlank() ||
 "auto".equalsIgnoreCase(
 code )) {

 overrides.remove(
 playerUuid );

 } else {

 overrides.put(
 playerUuid,
 code );
 }
 }

 public String getOverride(
 UUID playerUuid ) {

 if (playerUuid == null) {
 return "auto";
 }

 return overrides.getOrDefault(
 playerUuid,
 "auto" );
 }

 public Component message(
 String key,
 String... rawArgs ) {

 return fallback.message(
 key,
 rawArgs );
 }

 public Component messageComponents(
 String key,
 Component... args ) {

 return fallback.messageComponents(
 key,
 args );
 }

 public String text(
 String key,
 String... args ) {

 return fallback.text(
 key,
 args );
 }

 public List<String> textList(
 String key ) {

 return fallback.textList(
 key );
 }

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

                    /*
                     * 繁体中文（台湾 / 香港 / 澳门）→ zh_tw；
                     * 其余 zh_* → zh_cn。
                     */
                    if ("zh".equals(
                            code.substring(
                                    0,
                                    2
                            )
                    )) {

                        if ("zh_tw".equals(code) ||
                                "zh_hk".equals(code) ||
                                "zh_mo".equals(code)) {

                            return "zh_tw";
                        }

                        return "zh_cn";
                    }

                    /*
                     * 不匹配任何支持语言时统一回退英语。
                     */
                    return switch (
                            code.substring(
                                    0,
                                    2
                            )
                    ) {

                        case "en" ->
                                "en_us";

                        case "ja" ->
                                "ja_jp";

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

    private LangMessages messagesFor(
 String code ) {

 if (code == null ||
 code.isBlank()) {

 code = defaultCode;
 }

 return cache.computeIfAbsent(
 code,
 c -> LangMessages.load(
 plugin,
 c,
 logger ) );
 }
}
