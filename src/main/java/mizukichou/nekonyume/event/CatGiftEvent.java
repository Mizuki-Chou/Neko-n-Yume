package mizukichou.nekonyume.event;

import lombok.Getter;

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
@Getter
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
        /*
         * 0.8.4 R18（社区上报 L-NEW-03）：
         * List.copyOf 只保护列表结构，不保护其中的 ItemStack——
         * 事件是事后通知，礼品必须是深快照而非活对象。
         */
        this.gifts =
                gifts.stream()
                        .map(
                                ItemStack::clone
                        )
                        .toList();
        this.meowRank = meowRank;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
