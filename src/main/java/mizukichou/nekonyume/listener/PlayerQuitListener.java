package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.NekoNYume;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerQuitListener implements Listener {

    private final NekoNYume plugin;

    public PlayerQuitListener(
            NekoNYume plugin
    ) {

        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(
            PlayerQuitEvent event
    ) {

        UUID playerUUID =
                event.getPlayer()
                        .getUniqueId();

        /*
         * 保存这个玩家的运行时猫咪。
         */
        plugin.getCatManager()
                .saveCat(
                        plugin.getCatManager()
                                .getCat(
                                        playerUUID
                                )
                );

        /*
         * 玩家退出是一个关键保存节点，
         * 所以这里立即 flush。
         */
        plugin.getDataManager()
                .flush();

        /*
         * 从内存卸载。
         *
         * 下次玩家加入时会重新从 players.yml 加载。
         */
        plugin.getCatManager()
                .removeLogicalCat(
                        playerUUID
                );

        /*
         * 清除待恢复队列。
         *
         * 如果这个玩家登录时猫咪所在世界尚未加载，
         * 他会被放入待恢复队列。
         * 退出时移除，避免世界加载后
         * 为已离线的玩家执行恢复。
         */
        plugin.getCatManager()
                .clearPendingRestore(
                        playerUUID
                );
    }
}
