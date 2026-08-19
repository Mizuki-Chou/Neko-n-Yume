package mizukichou.nekonyume.event;

import lombok.Getter;

import mizukichou.nekonyume.cat.Cat;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 猫咪喵阶提升事件。
 *
 * <p>
 * 喵力达到升阶曲线时触发。
 * 属于事后通知，不可取消。
 * </p>
 */
@Getter
public class CatMeowRankUpEvent extends Event {

    private static final HandlerList handlers =
            new HandlerList();

    private final Player player;
    private final Cat cat;
    private final int fromMeowRank;
    private final int toMeowRank;

    public CatMeowRankUpEvent(
            Player player,
            Cat cat,
            int fromMeowRank,
            int toMeowRank
    ) {

        this.player = player;
        this.cat = cat;
        this.fromMeowRank = fromMeowRank;
        this.toMeowRank = toMeowRank;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

