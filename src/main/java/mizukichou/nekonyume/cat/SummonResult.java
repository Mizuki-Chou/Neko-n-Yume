package mizukichou.nekonyume.cat;

/**
 * 召唤/恢复流水线的结果语义。
 *
 * <p>
 * 取代原先的 {@code Consumer<Boolean>}：
 * {@code false} 曾同时表达“实体已在场”与“召唤失败”，
 * 导致 /nekoyume summon 在真实失败时提示“猫已经在这里”。
 * </p>
 *
 * <ul>
 * <li>{@link #SPAWNED} —— 新建了猫实体；</li>
 * <li>{@link #ALREADY_PRESENT} —— 旧实体在场，已完成传送/绑定；</li>
 * <li>{@link #FAILED} —— 召唤/恢复失败（区块加载失败、传送失败、
 * 数据被删除、代际失效等）。</li>
 * </ul>
 */
public enum SummonResult {

    SPAWNED,
    ALREADY_PRESENT,
    FAILED,

    /*
     * 0.9.0更新：召唤已在进行——调用方需要
     * 可区分的忙态，而不是 callback 永不执行啦。
     */
    BUSY
}

