package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.listener.CatFoodListener;
import mizukichou.nekonyume.model.EntityVisualController;
import mizukichou.nekonyume.model.ModelManager;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * 模型视觉监听器（Phase 4 + Phase 6）：
 * 玩家登录 / 换世界时记得重新应用原版视觉隐藏啦
 * （Player.hideEntity 是 per-player 状态，不跨会话保留）；
 * 登录时按配置推送资源包（§Phase 6）。
 */
public class ModelVisualListener implements Listener {

    private final EntityVisualController visualController;

    private final ModelManager modelManager;

    /**
     * 0.9.0更新：交互转发的目标——
     * Display 右键 → 对猫的喂食/装备/抚摸捏。
     */
    private final CatFoodListener foodListener;

    public ModelVisualListener(
            EntityVisualController visualController,
            ModelManager modelManager,
            CatFoodListener foodListener
    ) {

        if (visualController == null ||
                modelManager == null ||
                foodListener == null) {
            throw new IllegalArgumentException(
                    "Arguments must not be null."
            );
        }

        this.visualController = visualController;
        this.modelManager = modelManager;
        this.foodListener = foodListener;
    }

    @EventHandler
    public void onPlayerJoin(
            PlayerJoinEvent event
    ) {

        Player player =
                event.getPlayer();

        visualController.onPlayerJoin(
                player
        );

        modelManager.sendResourcePack(
                player
        );
    }

    @EventHandler
    public void onPlayerQuit(
            PlayerQuitEvent event
    ) {

        modelManager.forgetResourcePack(
                event.getPlayer()
                        .getUniqueId()
        );
    }

    @EventHandler
    public void onPlayerChangedWorld(
            PlayerChangedWorldEvent event
    ) {

        Player player =
                event.getPlayer();

        visualController.onPlayerChangedWorld(
                player
        );
    }

    /**
     * P1-7：客户端资源包状态（loaded ≠ sent）。
     */
    @EventHandler
    public void onResourcePackStatus(
            PlayerResourcePackStatusEvent event
    ) {

        modelManager.onResourcePackStatus(
                event
        );
    }

    /**
     * P1-10：猫实体移出世界（外部 remove / chunk 卸载前的
     * 销毁 / 世界卸载）时同步销毁渲染状态——
     * non-persistent Display 已随实体消失，本地条目会变成
     * “假活跃”导致模型永久消失。
     */
    @EventHandler
    public void onEntityRemovedFromWorld(
            EntityRemoveFromWorldEvent event
    ) {

        if (event.getEntity() instanceof Cat) {

            modelManager.onEntityRemovedFromWorld(
                    event.getEntity()
            );

            return;
        }

        /*
         * 0.9.0更新：Display 被外部移除——总有人想手撕你的渲染对象。
         * （/kill / 清理插件）→ 猫仍有效则重建模型；
         * Interaction 被移除 → 重建交互代理。
         */
        if (event.getEntity() instanceof ItemDisplay) {

            modelManager.onDisplayRemoved(
                    event.getEntity()
                            .getUniqueId()
            );

            return;
        }

        if (event.getEntity() instanceof Interaction) {

            modelManager.onInteractionRemoved(
                    event.getEntity()
                            .getUniqueId()
            );
        }
    }

    /**
     * 0.9.0更新：右键交互代理实体 → 转发为
     * 对原版猫的交互（hideEntity 后客户端不再对猫发送
     * 交互包，核心玩法不断）。
     */
    @EventHandler(
            priority = EventPriority.NORMAL,
            ignoreCancelled = true
    )
    public void onProxyInteract(
            PlayerInteractEntityEvent event
    ) {

        if (!(event.getRightClicked()
                instanceof Interaction)) {

            return;
        }

        String owner =
                event.getRightClicked()
                        .getPersistentDataContainer()
                        .get(
                                modelManager.displayOwnerKey(),
                                PersistentDataType.STRING
                        );

        if (owner == null) {
            return;
        }

        Cat cat =
                modelManager.catForInteraction(
                        event.getRightClicked()
                                .getUniqueId()
                );

        if (cat == null) {
            return;
        }

        event.setCancelled(
                true
        );

        foodListener.handleProxyInteract(
                event.getPlayer(),
                cat,
                event.getHand()
        );
    }

    /**
     * P1-10：世界卸载时该世界全部猫实体销毁，
     * 清理其渲染状态。
     */
    @EventHandler
    public void onWorldUnload(
            WorldUnloadEvent event
    ) {

        modelManager.onWorldUnloaded(
                event.getWorld()
                        .getUID()
        );
    }
}
