package mizukichou.nekonyume.event;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatSkill;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 技能抽取 / 刷新结果事件。
 *
 * <p>
 * 结果已写入并持久化后触发。
 * 属于事后通知，不可取消。
 * </p>
 */
public class CatSkillRollEvent extends Event {

    private static final HandlerList handlers =
            new HandlerList();

    private final Player player;
    private final Cat cat;
    private final int slotIndex;

    /*
     * 刷新前的旧技能；
     * 新槽免费抽取时为 null。
     */
    private final CatSkill oldSkill;

    private final CatSkill newSkill;

    /*
     * true = 付费刷新
     * false = 新槽免费抽取
     */
    private final boolean refreshed;

    public CatSkillRollEvent(
            Player player,
            Cat cat,
            int slotIndex,
            CatSkill oldSkill,
            CatSkill newSkill,
            boolean refreshed
    ) {

        this.player = player;
        this.cat = cat;
        this.slotIndex = slotIndex;
        this.oldSkill = oldSkill;
        this.newSkill = newSkill;
        this.refreshed = refreshed;
    }

    public Player getPlayer() {
        return player;
    }

    public Cat getCat() {
        return cat;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public CatSkill getOldSkill() {
        return oldSkill;
    }

    public CatSkill getNewSkill() {
        return newSkill;
    }

    public boolean isRefreshed() {
        return refreshed;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
