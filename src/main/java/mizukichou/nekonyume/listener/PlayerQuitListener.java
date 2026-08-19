package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerQuitListener implements Listener {

    private final CatCache cache;
    private final CatStore store;
    private final CatEntityService entityService;

    public PlayerQuitListener(
            CatCache cache,
            CatStore store,
            CatEntityService entityService
    ) {

        this.cache = cache;
        this.store = store;
        this.entityService = entityService;
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
        cache.saveCat(
                cache.getCat(
                        playerUUID
                )
        );

        /*
         * 玩家退出是一个关键保存节点，
         * 所以这里立即 flush。
         */
        store.flush();

        /*
         * 从内存卸载。
         *
         * 下次玩家加入时会重新从 players.yml 加载。
         */
        cache.removeByOwner(
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
        entityService.clearPendingRestore(
                playerUUID
        );
    }
}

