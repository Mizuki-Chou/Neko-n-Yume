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
    private final mizukichou.nekonyume.skill.CatBattleState battleState;
    private final Lang lang;

    private final mizukichou.nekonyume.gui.RankingGuiManager rankingGuiManager;
    private final mizukichou.nekonyume.gui.CatDetailGuiManager detailGuiManager;

    public PlayerQuitListener(
            CatCache cache,
            CatStore store,
            CatEntityService entityService,
            CatSkillManager skillManager,
            mizukichou.nekonyume.skill.CatBattleState battleState,
            Lang lang,
            mizukichou.nekonyume.gui.RankingGuiManager rankingGuiManager,
            mizukichou.nekonyume.gui.CatDetailGuiManager detailGuiManager
    ) {

        this.cache = cache;
        this.store = store;
        this.entityService = entityService;
        this.skillManager = skillManager;
        this.battleState = battleState;
        this.lang = lang;
        this.rankingGuiManager = rankingGuiManager;
        this.detailGuiManager = detailGuiManager;
    }

    @EventHandler
    public void onQuit(
            PlayerQuitEvent event
    ) {

        UUID playerUUID =
                event.getPlayer()
                        .getUniqueId();

        /*
         * 0.8.4 R18（社区上报 L-NEW-04 + M-NEW-02）：
         * 退出清理整体 finally 化——保存环节任何一步抛异常，
         * 运行时状态清理都必须全部执行；并顺带清理
         * 协助目标（会话态，退出即失效）。
         */
        try {

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

        } finally {

            /*
             * 从内存卸载。
             */
            cache.removeByOwner(
                    playerUUID
            );

            /*
             * 清除待恢复队列。
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

            /*
             * 0.8.4 R18（社区上报 M-NEW-02）：
             * 协助目标属于会话态——退出即失效。
             */
            battleState.clearAssistTarget(
                    playerUUID
            );

            /*
             * 0.8.5：排行面板状态（排序模式 / 页码）
             * 属于会话态，退出即清理，防止状态表单调增长。
             */
            rankingGuiManager.clearState(
                    playerUUID
            );

            detailGuiManager.clearState(
                    playerUUID
            );
        }
    }
}
