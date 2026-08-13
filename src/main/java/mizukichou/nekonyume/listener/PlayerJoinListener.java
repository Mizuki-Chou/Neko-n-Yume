package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.NekoNYume;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.List;

public class PlayerJoinListener implements Listener {

    private final NekoNYume plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    /*
     * 预编译后的入服欢迎消息。
     *
     * 在构造函数中一次性解析。
     * 解析失败的条目只记录警告并跳过，
     * 绝不阻断玩家的猫咪数据加载。
     */
    private final List<Component> joinMessages =
            new ArrayList<>();

    public PlayerJoinListener(NekoNYume plugin) {

        this.plugin = plugin;

        precompileJoinMessages();
    }

    /*
     * ============================================================
     * 预编译 config 中的欢迎消息
     * ============================================================
     *
     * 管理员写错一行 MiniMessage
     * 不应该影响玩家登录时的数据加载。
     */

    private void precompileJoinMessages() {

        if (!plugin.getConfig()
                .getBoolean("join-message.enabled")) {

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
         * 解析错误已在构造阶段处理，
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
         * 4. 恢复猫咪实体
         * ============================================================
         *
         * 阶段 1：登录自动恢复实体。
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
}
