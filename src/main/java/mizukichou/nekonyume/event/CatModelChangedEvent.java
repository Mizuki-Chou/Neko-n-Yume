package mizukichou.nekonyume.event;

import lombok.Getter;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 猫的视觉模型变更事件（Phase 8）捏。
 *
 * <p>
 * 管理员通过命令设置 / 清除模型后触发啦；
 * 事后通知，不可取消。{@code newModelId} 为空串表示清除
 * （回退默认模型 / 原版视觉）。
 * </p>
 */
@Getter
public class CatModelChangedEvent extends Event {

    private static final HandlerList handlers =
            new HandlerList();

    private final UUID playerUuid;

    private final UUID catUuid;

    private final String newModelId;

    public CatModelChangedEvent(
            UUID playerUuid,
            UUID catUuid,
            String newModelId
    ) {

        this.playerUuid = playerUuid;
        this.catUuid = catUuid;
        this.newModelId = newModelId;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
