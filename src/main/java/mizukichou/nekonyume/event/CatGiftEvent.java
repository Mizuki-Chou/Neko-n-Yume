package mizukichou.nekonyume.event;

import mizukichou.nekonyume.cat.Cat;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 猫咪送礼事件。
 *
 * <p>
 * 礼物已经发放后触发。
 * 属于事后通知，不可取消。
 * </p>
 */
public class CatGiftEvent extends Event {

    private static final HandlerList handlers =
            new HandlerList();

    private final Player player;
    private final Cat cat;

    /*
     * 本次送出的礼物。
     */
    private final List<ItemStack> gifts;

    /*
     * 送礼时的喵阶。
     */
    private final int meowRank;

    public CatGiftEvent(
            Player player,
            Cat cat,
            List<ItemStack> gifts,
            int meowRank
    ) {

        this.player = player;
        this.cat = cat;
        this.gifts =
                List.copyOf(
                        gifts
                );
        this.meowRank = meowRank;
    }

    public Player getPlayer() {
        return player;
    }

    public Cat getCat() {
        return cat;
    }

    public List<ItemStack> getGifts() {
        return gifts;
    }

    public int getMeowRank() {
        return meowRank;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
