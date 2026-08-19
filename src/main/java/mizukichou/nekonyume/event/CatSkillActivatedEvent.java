package mizukichou.nekonyume.event;

import lombok.Getter;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatSkill;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 主动技能成功施放事件。
 *
 * <p>
 * 效果已生效、冷却已记录后触发。
 * 属于事后通知，不可取消。
 * </p>
 */
@Getter
public class CatSkillActivatedEvent extends Event {

    private static final HandlerList handlers =
            new HandlerList();

    private final Player player;
    private final Cat cat;
    private final CatSkill skill;

    public CatSkillActivatedEvent(
            Player player,
            Cat cat,
            CatSkill skill
    ) {

        this.player = player;
        this.cat = cat;
        this.skill = skill;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

