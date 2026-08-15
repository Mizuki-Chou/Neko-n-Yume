package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.NekoNYume;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.List;

public class PlayerJoinListener implements Listener {

    /*
     * 登录后延迟多久判定礼物（tick）。
     * 让玩家先看到自己的猫，
     * 再收到猫咪的礼物。
     */
    private static final long GIFT_CHECK_DELAY_TICKS =
            60L;

    private final NekoNYume plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    /*
     * 预编译后的入服欢迎消息。
     *
     * 构造时解析一次；
     * /nekoyumeadmin reload 时由主类
     * 再次调用 reload() 重读，
     * 让配置改动即时生效。
     *
     * 解析失败的条目只记录警告并跳过，
     * 绝不阻断玩家的猫咪数据加载。
     */
    private final List<Component> joinMessages =
            new ArrayList<>();

    public PlayerJoinListener(NekoNYume plugin) {

        this.plugin = plugin;

        reload();
    }

    /*
     * ============================================================
     * 重载欢迎消息
     * ============================================================
     *
     * 管理员写错一行 MiniMessage
     * 不应该影响玩家登录时的数据加载。
     */

    public void reload() {

        joinMessages.clear();

        if (!plugin.getConfig()
                .getBoolean(
                        "join-message.enabled",
                        true
                )) {

            return;
        }

        for (String msg :
                plugin.getConfig()
                        .getStringList(
                                "join-message.messages"
                        )) {

            if (msg == null ||
                    msg.isBlank()) {

                continue;
            }

            try {

                joinMessages.add(
                        mm.deserialize(msg)
                );

            } catch (Exception exception) {

                plugin.getLogger().warning(
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
         * 1. 原有入服欢迎消息
         * ============================================================
         *
         * 使用预编译后的 Component。
         * 解析错误已在 reload() 阶段处理，
         * 这里不会抛出异常。
         */

        for (Component message :
                joinMessages) {

            player.sendMessage(
                    message
            );
        }

        /*
         * ============================================================
         * 2. 确保玩家有猫咪存档
         * ============================================================
         */

        plugin.getDataManager()
                .ensureCat(
                        player.getUniqueId()
                );

        /*
         * ============================================================
         * 3. 加载运行时猫咪
         * ============================================================
         *
         * players.yml
         *      ↓
         * PlayerDataManager
         *      ↓
         * CatManager
         *      ↓
         * 内存中的 Cat
         */

        plugin.getCatManager()
                .loadCat(
                        player
                );

        /*
         * ============================================================
         * 2. 加载运行时猫咪
         * ============================================================
         *
         * 只有拥有猫咪记录的玩家才加载与恢复实体。
         *
         * 没有猫咪的玩家等待 /nekoyume claim，
         * 登录时绝不自动创建猫。
         *
         * players.yml
         *      ↓
         * PlayerDataManager
         *      ↓
         * CatManager
         *      ↓
         * 内存中的 Cat
         */

        if (plugin.getDataManager()
                .hasCat(
                        player.getUniqueId()
                )) {

            plugin.getCatManager()
                    .loadCat(
                            player
                    );

            /*
             * ====================================================
             * 3. 恢复猫咪实体
             * ====================================================
             *
             * 恢复顺序：
             *
             * 1. 保存的 Entity UUID
             * 2. 最后保存的世界 + 区块
             * 3. 当前已加载的全部世界
             * 4. 在最后保存的位置重建
             * 5. 完全没有可用位置时兜底
             *
             * 登录恢复会尽量保留猫咪原来的位置，
             * 不会主动传送到玩家身边。
             *
             * 如果猫咪所在世界尚未加载，
             * 会进入等待队列，
             * 世界加载完成后由
             * CatManager.retryPendingWorldRestores() 重试。
             */

            plugin.getCatManager()
                    .restoreCatEntity(
                            player
                    );
        }


        /*
         * ============================================================
         * 5. 每日礼物判定
         * ============================================================
         */

        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {

                            if (player.isOnline()) {

                                plugin.getGiftManager()
                                        .checkAndGive(
                                                player
                                        );
                            }
                        },
                        GIFT_CHECK_DELAY_TICKS
                );
    }
}
