package mizukichou.nekonyume.skill;

import org.bukkit.entity.Player;

/**
 * 技能刷新消耗提供者。
 *
 * <p>
 * 默认实现消耗喵力；
 * 服务器主可通过 config 的 skills.refresh.cost-type
 * 切换到其他经济系统（如 PlayerPoints points）。
 * </p>
 *
 * <p>
 * 未来接入其他经济插件时，
 * 只需实现本接口并注册，无需改动核心代码。
 * </p>
 */
public interface SkillRefreshCostProvider {

    /**
     * 消耗展示名，如「喵力」「points」。
     */
    String getDisplayName();

    /**
     * 玩家当前是否付得起本次费用。
     */
    boolean canAfford(
            Player player,
            int cost
    );

    /**
     * 实际扣除（返回是否成功）。
     */
    boolean charge(
            Player player,
            int cost
    );

    /**
     * 回退扣除（默认无操作）。
     *
     * <p>
     * 仅在"扣费成功但后续写入被拒绝"的极端防御路径上调用，
     * 保证"无结果必不付费"不变量。
     * 实现方若无法安全回退（如第三方经济插件不支持退款），
     * 应保持默认实现并记录日志。
     * </p>
     */
    default void refund(
            Player player,
            int cost
    ) {
        /*
         * 默认无操作。
         */
    }
}
