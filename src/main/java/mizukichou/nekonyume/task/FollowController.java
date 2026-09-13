package mizukichou.nekonyume.task;

/**
 * 跟随寻路决策器（纯逻辑，可单元测试）。
 *
 * <p>
 * 0.9.0更新：跟随从"传送式"改为"寻路式"（Paper Pathfinder）——
 * 猫真的走起来，walk 动画（速度驱动）自然触发，卡墙时兜底传送。
 * 本类只做决策：每轮（CatBehaviorTask 每 20 tick 一轮）由任务
 * 传入当前水平距离与上一轮 moveTo 的成败，返回本轮动作。
 * </p>
 *
 * <p>
 * 状态机（每猫一份）：</p>
 * <ul>
 * <li>距离 ≤ {@link #STOP_DISTANCE}：已贴身 → STOP（停路径、重置）；</li>
 * <li>距离 &gt; {@link #TELEPORT_DISTANCE}：太远，寻路无意义 →
 * 立即 TELEPORT（与旧版传送跟随的远距语义一致，避免长距离硬走）；</li>
 * <li>两者之间：NAVIGATE（每轮重算路径，跟踪移动中的主人）；
 * 连续 {@link #NO_PROGRESS_LIMIT} 轮没有向主人靠近
 * {@link #PROGRESS_EPSILON} 格以上，或 moveTo 一直失败
 * （无可用路径）→ TELEPORT 兜底。</li>
 * </ul>
 */
public final class FollowController {

    /**
     * 停止跟随距离（水平，格）：猫走到主人这个距离内就停下。
     */
    public static final double STOP_DISTANCE = 2.5;

    /**
     * 兜底传送距离（水平，格）：超过则直接传送，不做寻路。
     */
    public static final double TELEPORT_DISTANCE = 32.0;

    /**
     * 无进展轮数上限：连续这么多轮没有靠近 → 兜底传送。
     * （任务每 20 tick 一轮 ≈ 1 秒。）
     */
    public static final int NO_PROGRESS_LIMIT = 4;

    /**
     * 寻路最少轮数：兜底传送至少等这么多轮才生效，
     * 给寻路一点起步时间，避免"刚出发就被判定为卡住"。
     */
    public static final int MIN_FOLLOW_ROUNDS = 2;

    /**
     * 判定"有进展"的最小接近量（水平，格）。
     * 用带容差的比较而不是"距离必须单调递减"，
     * 防止主人原地微动造成的抖动误判。
     */
    public static final double PROGRESS_EPSILON = 0.5;

    /**
     * 本轮动作。
     */
    public enum Action {

        /**
         * 什么都不做（当前仅在防御路径使用）。
         */
        NONE,

        /**
         * 寻路走向主人（任务应调用 moveTo 并把结果回传）。
         */
        NAVIGATE,

        /**
         * 已在主人身边：停止寻路。
         */
        STOP,

        /**
         * 兜底传送（任务复用 SafeTeleport 逻辑）。
         */
        TELEPORT
    }

    /**
     * 基线距离：最近一次判定"有进展"时的水平距离。
     * 初始正无穷，保证第一轮必然进入 NAVIGATE 分支。
     */
    private double baselineDistance =
            Double.POSITIVE_INFINITY;

    /**
     * 连续无进展轮数。
     */
    private int noProgressRounds = 0;

    /**
     * 本轮跟随会话已进行的轮数。
     */
    private int followRounds = 0;

    /**
     * 每轮决策。
     *
     * @param distance            猫与主人的水平距离（格）
     * @param pathFoundLastRound  上一轮 NAVIGATE 时 moveTo 是否成功找到路径
     *                            （首轮传 false 即可）
     * @return 本轮动作
     */
    public Action tick(
            double distance,
            boolean pathFoundLastRound
    ) {

        if (!Double.isFinite(distance) ||
                distance < 0) {

            /*
             * 距离数据异常（理论不可达）：保守不动，
             * 下一轮自然重试。
             */
            return Action.NONE;
        }

        if (distance <= STOP_DISTANCE) {

            reset();

            return Action.STOP;
        }

        if (distance > TELEPORT_DISTANCE) {

            reset();

            return Action.TELEPORT;
        }

        followRounds++;

        if (pathFoundLastRound &&
                distance <=
                        baselineDistance
                                - PROGRESS_EPSILON) {

            /*
             * 有进展：基线前进到更近处，无进展计数清零。
             */
            baselineDistance = distance;
            noProgressRounds = 0;

        } else {

            noProgressRounds++;
        }

        if (followRounds > MIN_FOLLOW_ROUNDS &&
                noProgressRounds >=
                        NO_PROGRESS_LIMIT) {

            reset();

            return Action.TELEPORT;
        }

        return Action.NAVIGATE;
    }

    /**
     * 会话重置（STOP / TELEPORT 之后，或跨猫复用实例时）。
     */
    public void reset() {

        baselineDistance =
                Double.POSITIVE_INFINITY;
        noProgressRounds = 0;
        followRounds = 0;
    }
}
