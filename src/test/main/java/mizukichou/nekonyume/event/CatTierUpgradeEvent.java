package mizukichou.nekonyume.event;

import lombok.Getter;
import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatTier;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 猫咪底蕴升阶事件。
 *
 * <p>
 * 在底蕴升阶全部完成后触发（0.7.0，成就系统使用）。
 * 属于事后通知，不可取消。
 * </p>
 */
@Getter
public class CatTierUpgradeEvent extends Event {

    private static final HandlerList handlers =
            new HandlerList();

    private final Player player;
    private final Cat cat;
    private final CatTier fromTier;
    private final CatTier toTier;

    public CatTierUpgradeEvent(
            Player player,
            Cat cat,
            CatTier fromTier,
            CatTier toTier
    ) {

        this.player = player;
        this.cat = cat;
        this.fromTier = fromTier;
        this.toTier = toTier;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
