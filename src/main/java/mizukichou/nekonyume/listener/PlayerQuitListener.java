package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.lang.Lang;
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
    private final Lang lang;

    public PlayerQuitListener(
            CatCache cache,
            CatStore store,
            CatEntityService entityService,
            CatSkillManager skillManager,
            Lang lang
    ) {

        this.cache = cache;
        this.store = store;
        this.entityService = entityService;
        this.skillManager = skillManager;
        this.lang = lang;
    }

    @EventHandler
    public void onQuit(
            PlayerQuitEvent event
    ) {

        UUID playerUUID =
                event.getPlayer()
                        .getUniqueId();

        /*
         * 0.8.1 修复（R3，社区上报）：
         * 退出前先从实体捕获最新位置/花色/世界，
         * 否则刚移动就退出会回写 30 秒前的旧位置。
         */
        entityService.captureEntityState(
                cache.getCat(
                        playerUUID
                )
        );

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
         * 0.8.1 修复（P2）：
         * 清除个人语言覆盖，防止 overrides 表单调增长。
         */
        lang.clearOverride(
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
