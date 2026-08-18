package mizukichou.nekonyume.event;

import lombok.Getter;

import mizukichou.nekonyume.cat.Cat;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 猫咪抚摸成功事件。
 *
 * <p>
 * 在抚摸逻辑全部完成后触发。
 * 属于事后通知，不可取消。
 * </p>
 */
@Getter
public class CatPettedEvent extends Event {

    private static final HandlerList handlers =
            new HandlerList();

    private final Player player;
    private final Cat cat;

    /*
     * 被抚摸的 Bukkit 猫实体。
     */
    private final org.bukkit.entity.Cat entityCat;

    /*
     * 本次实际增加的好感度。
     */
    private final int affectionGain;

    /*
     * 本次获得的经验。
     */
    private final int xpGain;

    /*
     * 本次获得的喵力（0 或 1）。
     */
    private final int meowGain;

    public CatPettedEvent(
            Player player,
            Cat cat,
            org.bukkit.entity.Cat entityCat,
            int affectionGain,
            int xpGain,
            int meowGain
    ) {

        this.player = player;
        this.cat = cat;
        this.entityCat = entityCat;
        this.affectionGain = affectionGain;
        this.xpGain = xpGain;
        this.meowGain = meowGain;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
