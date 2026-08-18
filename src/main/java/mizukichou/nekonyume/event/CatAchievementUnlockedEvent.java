package mizukichou.nekonyume.event;

import mizukichou.nekonyume.achievement.CatAchievement;
import mizukichou.nekonyume.cat.Cat;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 成就解锁事件。
 *
 * <p>
 * 成就已持久化、奖励已发放后触发。
 * 属于事后通知，不可取消。
 * </p>
 */
public class CatAchievementUnlockedEvent extends Event {

    private static final HandlerList handlers =
            new HandlerList();

    private final Player player;
    private final Cat cat;
    private final CatAchievement achievement;

    /*
     * 本次实际发放的奖励数值。
     */
    private final int rewardXp;
    private final int rewardMeowPower;

    public CatAchievementUnlockedEvent(
            Player player,
            Cat cat,
            CatAchievement achievement,
            int rewardXp,
            int rewardMeowPower
    ) {

        this.player = player;
        this.cat = cat;
        this.achievement = achievement;
        this.rewardXp = rewardXp;
        this.rewardMeowPower = rewardMeowPower;
    }

    public Player getPlayer() {
        return player;
    }

    public Cat getCat() {
        return cat;
    }

    public CatAchievement getAchievement() {
        return achievement;
    }

    public int getRewardXp() {
        return rewardXp;
    }

    public int getRewardMeowPower() {
        return rewardMeowPower;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}