package mizukichou.nekonyume.event;

import mizukichou.nekonyume.cat.Cat;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 猫咪等级提升事件。
 *
 * <p>
 * 经验达到升级曲线时触发。
 * 属于事后通知，不可取消。
 * </p>
 */
public class CatLevelUpEvent extends Event {

    private static final HandlerList handlers =
            new HandlerList();

    private final Player player;
    private final Cat cat;
    private final int fromLevel;
    private final int toLevel;

    public CatLevelUpEvent(
            Player player,
            Cat cat,
            int fromLevel,
            int toLevel
    ) {

        this.player = player;
        this.cat = cat;
        this.fromLevel = fromLevel;
        this.toLevel = toLevel;
    }

    public Player getPlayer() {
        return player;
    }

    public Cat getCat() {
        return cat;
    }

    public int getFromLevel() {
        return fromLevel;
    }

    public int getToLevel() {
        return toLevel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}