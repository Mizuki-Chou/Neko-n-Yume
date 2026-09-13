package mizukichou.nekonyume.model;

import mizukichou.nekonyume.cat.CatEntityRuntime;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 原版视觉控制器（架构 §33/§34）。
 *
 * <p>
 * D1 决策（Player.hideEntity 方案）的落地：
 * 通过 Paper 的 {@code Player.hideEntity/showEntity} 对玩家隐藏
 * 原版猫实体——纯客户端 packet 层隐藏，服务器侧 AI、
 * 仇恨链、战斗系统完全不受影响（这是与 setInvisible 方案
 * 的本质区别，后者会破坏怪物对猫的复仇逻辑）。
 * </p>
 *
 * <p>
 * 职责：
 * </p>
 * <ul>
 *   <li>维护"当前被隐藏的猫实体"集合</li>
 *   <li>玩家登录 / 换世界时重新应用隐藏（hideEntity 是
 *       per-player 状态）</li>
 *   <li>恢复期由 ModelManager 协调：show 原版猫
 *       （复用原版 INVISIBILITY 药水的半透明表现）+ 隐藏模型</li>
 * </ul>
 *
 * <p>
 * 主线程专用。
 * </p>
 */
public final class EntityVisualController {

    private final Plugin plugin;

    /**
     * Bukkit 触点的 seam（不碰 Bukkit 静态）。
     */
    private final CatEntityRuntime runtime;

    /**
     * 当前被隐藏原版视觉的猫实体 UUID。
     */
    private final Set<UUID> hiddenCatUuids =
            new HashSet<>();

    public EntityVisualController(
            Plugin plugin,
            CatEntityRuntime runtime
    ) {

        if (plugin == null || runtime == null) {
            throw new IllegalArgumentException(
                    "Arguments must not be null."
            );
        }

        this.plugin = plugin;
        this.runtime = runtime;
    }

    /**
     * 对所有在线玩家隐藏原版猫视觉（幂等）。
     */
    public void hide(
            Entity entity
    ) {

        if (!(entity instanceof Cat)) {
            return;
        }

        if (!hiddenCatUuids.add(
                entity.getUniqueId()
        )) {

            return;
        }

        for (Player player :
                runtime.onlinePlayers()) {

            player.hideEntity(
                    plugin,
                    entity
            );
        }
    }

    /**
     * 恢复原版视觉（恢复期半透明影子）。幂等。
     */
    public void show(
            Entity entity
    ) {

        if (entity == null) {
            return;
        }

        UUID uuid =
                entity.getUniqueId();

        if (!hiddenCatUuids.remove(
                uuid
        )) {

            return;
        }

        for (Player player :
                runtime.onlinePlayers()) {

            player.showEntity(
                    plugin,
                    entity
            );
        }
    }

    /**
     * 直接遗忘（实体即将销毁，无需 show）。
     */
    public void forget(
            UUID entityUuid
    ) {

        hiddenCatUuids.remove(
                entityUuid
        );
    }

    /**
     * 主动恢复可见：从隐藏集合移除并 show 给全部在线玩家
     * （模型设置变更 / 清除时，视觉回退到原版猫）。
     */
    public void reveal(
            UUID entityUuid
    ) {

        if (entityUuid == null ||
                !hiddenCatUuids.remove(
                        entityUuid
                )) {

            return;
        }

        Entity entity =
                runtime.getEntity(
                        entityUuid
                );

        if (entity == null ||
                !entity.isValid()) {

            return;
        }

        for (Player player :
                runtime.onlinePlayers()) {

            player.showEntity(
                    plugin,
                    entity
            );
        }
    }

    /**
     * 玩家登录：重新应用全部隐藏。
     */
    public void onPlayerJoin(
            Player player
    ) {

        if (player == null) {
            return;
        }

        applyTo(
                player
        );
    }

    /**
     * 玩家换世界：新世界重新应用隐藏。
     */
    public void onPlayerChangedWorld(
            Player player
    ) {

        if (player == null) {
            return;
        }

        applyTo(
                player
        );
    }

    /**
     * 插件停用：清空状态（实体即将随世界消失）。
     */
    public void clearAll() {

        hiddenCatUuids.clear();
    }

    public int hiddenCount() {

        return hiddenCatUuids.size();
    }

    private void applyTo(
            Player player
    ) {

        for (UUID uuid :
                new HashSet<>(hiddenCatUuids)) {

            Entity entity =
                    runtime.getEntity(
                            uuid
                    );

            if (entity == null ||
                    !entity.isValid()) {

                hiddenCatUuids.remove(
                        uuid
                );

                continue;
            }

            player.hideEntity(
                    plugin,
                    entity
            );
        }
    }
}
