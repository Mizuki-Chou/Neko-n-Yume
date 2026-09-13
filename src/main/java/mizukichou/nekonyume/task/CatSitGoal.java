package mizukichou.nekonyume.task;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Pose;

import java.util.EnumSet;

/**
 * 猫的坐姿目标（替代被移除的原版 SitWhenOrderedToGoal）。
 *
 * <p>
 * 0.9.0更新：跟随改寻路后每猫清空原版 AI，而 26.2 的
 * {@code TamableAnimal#setOrderedToSit} 只置标志位
 * （反汇编验证：坐姿与停导航全部由 SitWhenOrderedToGoal
 * 实现）。本类通过 Paper 的 Goal API 恢复原版语义：
 * 标志位为真时停止导航并进入坐姿，清除时恢复站立——
 * 插件 SIT 模式与原版右键切换坐姿（FREE 模式）都继续生效。
 * </p>
 */
public final class CatSitGoal implements
        Goal<org.bukkit.entity.Cat> {

    /**
     * 目标键（每猫注册一次，键只用于标识）。
     */
    private static final GoalKey<org.bukkit.entity.Cat> KEY =
            GoalKey.of(
                    org.bukkit.entity.Cat.class,
                    new NamespacedKey(
                            "nekonyume",
                            "sit_when_ordered"
                    )
            );

    private final org.bukkit.entity.Cat cat;

    private final Pathfinder pathfinder;

    public CatSitGoal(
            org.bukkit.entity.Cat cat
    ) {

        this.cat = cat;
        this.pathfinder = cat.getPathfinder();
    }

    /**
     * 与 NMS SitWhenOrderedToGoal#canUse 同口径的防御：
     * 水下 / 空中不进入坐姿。
     */
    @Override
    public boolean shouldActivate() {

        if (cat.isInWater() ||
                !cat.isOnGround()) {

            return false;
        }

        return cat.isSitting();
    }

    @Override
    public boolean shouldStayActive() {

        return cat.isSitting();
    }

    @Override
    public void start() {

        pathfinder.stopPathfinding();

        cat.setPose(
                Pose.SITTING
        );
    }

    @Override
    public void stop() {

        cat.setPose(
                Pose.STANDING
        );
    }

    @Override
    public GoalKey<org.bukkit.entity.Cat> getKey() {

        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {

        return EnumSet.of(
                GoalType.MOVE,
                GoalType.JUMP
        );
    }
}
