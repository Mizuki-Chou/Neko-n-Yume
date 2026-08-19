package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.muma.MumaNightManager;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * 梦魔之夜事件监听：
 * - 怪物生成时立即强化（新刷出的怪无需等周期扫描）；
 * - 被强化的怪物死亡时按概率掉落喵丹。
 */
public class MumaNightListener implements Listener {

    private final MumaNightManager manager;

    public MumaNightListener(
            MumaNightManager manager
    ) {

        this.manager = manager;
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMonsterDeath(
            EntityDeathEvent event
    ) {

        if (!(event.getEntity()
                instanceof Monster monster)) {

            return;
        }

        if (manager.isActive(
                monster.getWorld()
        ) &&
                manager.isBuffed(monster)) {

            manager.maybeDropMeowDan(
                    monster
            );

            manager.maybeDropXpPills(
                    monster
            );
        }
    }
}
