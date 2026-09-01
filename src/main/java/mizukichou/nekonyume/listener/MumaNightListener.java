package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.muma.MumaNightManager;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * 梦魔之夜事件监听：
 * - 怪物生成时立即强化（新刷出的怪无需等周期扫描）；
 * - 怪物死亡时按 drops 配置掉落喵丹 / 经验丸 / 猫猫装备袋
 *   （梦魔夜强化怪走 muma-night 集，平时走 general 集，默认关闭）。
 */
public class MumaNightListener implements Listener {

    private final MumaNightManager manager;

    public MumaNightListener(
            MumaNightManager manager
    ) {

        this.manager = manager;
    }

    /*
     * 0.8.4 R19（社区上报 L-NEW-07）：
     * 本处理器会修改实体状态（属性/装备/PDC/生命），
     * 不应挂在 MONITOR（事件惯例：MONITOR 只观察不改状态，
     * 同级插件顺序不受契约保证）。
     * 改为 HIGHEST：在插件修改链的常规末端应用强化。
     */
    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onCreatureSpawn(
            CreatureSpawnEvent event
    ) {

        if (!(event.getEntity()
                instanceof Monster monster)) {

            return;
        }

        if (manager.isActive(
                monster.getWorld()
        )) {

            manager.buffMonster(
                    monster
            );
        }
    }

    /*
     * 0.8.0 P1-5：区块加载时清理残留强化。
     *
     * 服务器重启后，未加载区块里的怪物可能仍带着
     * 上一场梦魔夜的强化 PDC；区块重新加载且梦魔夜
     * 未激活时，立即还原该区块内的强化怪物。
     */
    /*
     * 0.8.4 R20（全面自查）：
     * 与 onCreatureSpawn 同类——本处理器会 buff/strip
     * 区块内怪物（修改实体状态），不应挂在 MONITOR。
     */
    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onChunkLoad(
            ChunkLoadEvent event
    ) {

        manager.stripMarkedInChunk(
                event.getChunk()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMonsterDeath(
            EntityDeathEvent event
    ) {

        if (!(event.getEntity()
                instanceof Monster monster)) {

            return;
        }

        /*
         * 0.8.0：按当前状态解析掉落集合：
         * 梦魔夜且已强化 → drops.muma-night；
         * 平时 → drops.general（默认关闭）。
         */
        ConfigSnapshot.Drops.DropSet set =
                manager.resolveDropSet(
                        monster
                );

        if (set == null ||
                !set.isEnabled()) {

            return;
        }

        manager.maybeDropMeowDan(
                monster,
                set
        );

        manager.maybeDropXpPills(
                monster,
                set
        );

        manager.maybeDropEquipBag(
                monster,
                set
        );
    }
}
