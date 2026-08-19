package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.skill.CatSkillManager;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerQuitListener implements Listener {

    private final CatCache cache;
    private final CatStore store;
    private final CatEntityService entityService;
    private final CatSkillManager skillManager;

    public PlayerQuitListener(
            CatCache cache,
            CatStore store,
            CatEntityService entityService,
            CatSkillManager skillManager
    ) {

        this.cache = cache;
        this.store = store;
        this.entityService = entityService;
        this.skillManager = skillManager;
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

        /*
         * 清理技能冷却表条目：
         * 防止长跑服务器上冷却 Map 累积离线玩家记录。
         */
        skillManager.clearCooldowns(
                playerUUID
        );

        /*
         * 释放召唤标记（0.7.4 修复）：
         * 异步区块加载完成前下线会绕过回调的 finally，
         * 残留标记会永久封锁该玩家的召唤功能。
         */
        entityService.clearSummoning(
                playerUUID
        );
    }
}
