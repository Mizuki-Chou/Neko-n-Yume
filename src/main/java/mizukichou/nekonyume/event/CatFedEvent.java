package mizukichou.nekonyume.event;

import mizukichou.nekonyume.cat.Cat;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * 猫咪喂食成功事件。
 *
 * <p>
 * 在喂食逻辑全部完成后触发。
 * 属于事后通知，不可取消。
 * </p>
 */
public class CatFedEvent extends Event {

    private static final HandlerList handlers =
            new HandlerList();

    private final Player player;
    private final Cat cat;
    private final ItemStack item;
    private final int hungerGain;
    private final int affectionGain;
    private final int xpGain;
    private final int meowGain;

    public CatFedEvent(
            Player player,
            Cat cat,
            ItemStack item,
            int hungerGain,
            int affectionGain,
            int xpGain,
            int meowGain
    ) {

        this.player = player;
        this.cat = cat;
        this.item = item;
        this.hungerGain = hungerGain;
        this.affectionGain = affectionGain;
        this.xpGain = xpGain;
        this.meowGain = meowGain;
    }

    public Player getPlayer() {
        return player;
    }

    public Cat getCat() {
        return cat;
    }

    public ItemStack getItem() {
        return item;
    }

    /**
     * 本次实际增加的饱食度。
     */
    public int getHungerGain() {
        return hungerGain;
    }

    /**
     * 本次实际增加的好感度。
     */
    public int getAffectionGain() {
        return affectionGain;
    }

    /**
     * 本次获得的经验。
     */
    public int getXpGain() {
        return xpGain;
    }

    /**
     * 本次获得的喵力（0 或 1）。
     */
    public int getMeowGain() {
        return meowGain;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}