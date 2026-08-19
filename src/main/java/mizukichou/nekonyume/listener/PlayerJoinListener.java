package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.gift.GiftManager;
import mizukichou.nekonyume.storage.CatStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 玩家登录监听。
 *
 * <p>
 * 0.7.0：入服欢迎消息改从 ConfigManager 快照读取。
 * </p>
 */
public class PlayerJoinListener implements Listener {

    /*
     * 登录后延迟多久判定礼物（tick）。
     * 让玩家先看到自己的猫，再收到猫咪的礼物。
     */
    private static final long GIFT_CHECK_DELAY_TICKS =
            60L;

    /*
     * plugin 仅用于调度器。
     */
    private final JavaPlugin plugin;
    private final Logger logger;
    private final CatStore store;
    private final CatCache cache;
    private final CatEntityService entityService;
    private final GiftManager giftManager;
    private final ConfigManager configManager;

    private final MiniMessage mm =
            MiniMessage.miniMessage();

    /*
     * 预编译后的入服欢迎消息。
     *
     * 构造时解析一次；
     * /nekoyumeadmin reload 时由主类再次调用 reload() 重读。
     *
     * 解析失败的条目只记录警告并跳过，
     * 绝不阻断玩家的猫咪数据加载。
     */
    private final List<Component> joinMessages =
            new ArrayList<>();

    public PlayerJoinListener(
            JavaPlugin plugin,
            Logger logger,
            CatStore store,
            CatCache cache,
            CatEntityService entityService,
            GiftManager giftManager,
            ConfigManager configManager
    ) {

        this.plugin = plugin;
        this.logger = logger;
        this.store = store;
        this.cache = cache;
        this.entityService = entityService;
        this.giftManager = giftManager;
        this.configManager = configManager;

        reload();
    }

    /*
     * ============================================================
     * 重载欢迎消息
     * ============================================================
     */

    public void reload() {

        joinMessages.clear();

        ConfigSnapshot.JoinMessage joinMessage =
                configManager.snapshot()
                        .getJoinMessage();

        if (!joinMessage.isEnabled()) {
            return;
        }

        for (String msg :
                joinMessage.getMessages()) {

            if (msg == null ||
                    msg.isBlank()) {

                continue;
            }

            try {

                joinMessages.add(
                        mm.deserialize(msg)
                );

            } catch (Exception exception) {

                logger.warning(
                        "Invalid MiniMessage in join-message.messages, skipping: "
                                + msg
                );
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        Player player =
                event.getPlayer();

        /*
         * ============================================================
         * 1. 入服欢迎消息
         * ============================================================
         *
         * 使用预编译后的 Component。
         */
        for (Component message :
                joinMessages) {

            player.sendMessage(
                    message
            );
        }

        /*
         * ============================================================
         * 2. 恢复猫咪（仅限已有猫咪数据的玩家）
         * ============================================================
         *
         * P0 修复：
         * 这里绝不调用 ensureCat。
         * 没有猫咪数据的玩家等待 /nekoyume claim，
         * 登录时不会自动创建任何猫数据。
         *
         * 恢复顺序（见 CatEntityService.restoreCatEntity）：
         * 1. 保存的 Entity UUID
         * 2. 最后保存的世界 + 区块
         * 3. 当前已加载的全部世界
         * 4. 在最后保存的位置重建
         * 5. 完全没有可用位置时兜底
         */
        if (store.hasCat(
                player.getUniqueId()
        )) {

            cache.loadCat(
                    player
            );

            entityService.restoreCatEntity(
                    player
            );
        }

        /*
         * ============================================================
         * 3. 每日礼物判定
         * ============================================================
         */
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {

                            if (player.isOnline()) {

                                giftManager.checkAndGive(
                                        player
                                );
                            }
                        },
                        GIFT_CHECK_DELAY_TICKS
                );
    }
}
