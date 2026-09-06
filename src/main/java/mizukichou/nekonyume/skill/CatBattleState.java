package mizukichou.nekonyume.skill;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 猫咪战斗运行状态捏。
 *
 * <p>
 * 不持久化，重启重置：
 * 攻击间隔 / 攻击计数 / 重生冷却 / 协助目标 / 啦
 * 追击状态 / 扑击冷却 / 追击收势 / 捏
 * 受伤恢复期 / 缓慢回血 / 恢复期清扫节流。
 * </p>
 */
public class CatBattleState {

    /*
     * 0.9.0更新：主线程契约的运行时断言基准——
     * 构造于组合根（主线程），测试同样在主线程构造，
     * 不依赖 Bukkit 静态（测试环境无 server）。
     */
    private final Thread mainThread =
            Thread.currentThread();

    /*
     * 实体 UUID → 上次攻击时间（毫秒）。
     */
    private final Map<UUID, Long> lastAttackTimes =
            new HashMap<>();

    /**
     * 最近受伤时间（动画信号 hurt 的动作窗口，知识包 P1-24）。
     */
    private final Map<UUID, Long> lastHurtTimes =
            new HashMap<>();

    /*
     * 动画信号用的单调时间戳（nanoTime，0.9.0更新）。
     */
    private final Map<UUID, Long> lastAttackNanos =
            new HashMap<>();

    private final Map<UUID, Long> lastHurtNanos =
            new HashMap<>();

    /*
     * 实体 UUID → 攻击计数（影袭用）。
     */
    private final Map<UUID, Integer> attackCounts =
            new HashMap<>();

    /*
     * 实体 UUID → 上次永恒重生时间。
     */
    private final Map<UUID, Long> rebirthTimes =
            new HashMap<>();

    /*
     * 主人 UUID → 协助攻击的目标 UUID。
     */
    private final Map<UUID, UUID> assistTargets =
            new HashMap<>();

    /*
     * 正在追击中的猫实体 UUID。
     */
    private final Set<UUID> chasing =
            new HashSet<>();

    /*
     * 羁绊纪元（0.8.0）：已提示过“饥饿拒绝战斗”的实体 UUID。
     * 状态翻转只提示一次；饥饿解除后清除，可再次提示。
     */
    private final Set<UUID> starvingAlerted =
            new HashSet<>();

    /*
     * 实体 UUID → 上次扑击时间（毫秒）。
     */
    private final Map<UUID, Long> lastPounceTimes =
            new HashMap<>();

    /*
     * 实体 UUID → 追击结束时间（毫秒）。
     */
    private final Map<UUID, Long> lastChaseEndTimes =
            new HashMap<>();

    /*
     * 实体 UUID → 受伤恢复期结束时间（毫秒）。
     *
     * 恢复期内：
     * - 血量固定在 1，不会死亡；
     * - 禁止攻击、禁止主动技能、光环停摆；
     * - 怪物视猫为不存在（目标屏蔽）；
     * - 倒计时不会被重复受伤重置；
     * - 倒计时结束后满血复活。
     */
    private final Map<UUID, Long> recoveryEndTimes =
            new HashMap<>();

    /*
     * 实体 UUID → 上次缓慢回血时间（毫秒）。
     */
    private final Map<UUID, Long> lastRegenTimes =
            new HashMap<>();

    /*
     * 实体 UUID → 上次"目标清扫 + 悬浮字刷新"时间（毫秒）。
     *
     * P0-9：恢复期半径扫描降频到约 1 秒一次，
     * 避免 120 秒恢复期产生数百次全半径实体扫描。
     */
    private final Map<UUID, Long> lastSweepTimes =
            new HashMap<>();

    /*
     * 实体 UUID → 上次刷新悬浮字时的剩余秒数。
     * 同秒内倒计时跨整数秒边界时补一次刷新。
     */
    private final Map<UUID, Long> lastSweepDisplaySeconds =
            new HashMap<>();

    /*
     * ============================================================
     * 攻击间隔
     * ============================================================
     */

    public boolean canAttack(
            UUID entityUuid,
            long intervalMillis
    ) {

        if (entityUuid == null) {
            return false;
        }

        Long last =
                lastAttackTimes.get(
                        entityUuid
                );

        if (last == null) {
            return true;
        }

        return System.currentTimeMillis() - last
                >= intervalMillis;
    }

    public void markAttack(
            UUID entityUuid
    ) {

        assert Thread.currentThread() == mainThread
                : "CatBattleState is main-thread only";

        assert Thread.currentThread() == mainThread
                : "CatBattleState is main-thread only";

        if (entityUuid == null) {
            return;
        }

        lastAttackTimes.put(
                entityUuid,
                System.currentTimeMillis()
        );

        /*
         * 0.9.0更新：动画信号层统一单调时钟
         * （nanoTime）——与 AnimationController 同一时间线。
         */
        lastAttackNanos.put(
                entityUuid,
                System.nanoTime()
        );
    }

    /**
     * 最近一次攻击时间戳（无记录返回 null）。
     */
    public Long getLastAttackMillis(
            UUID entityUuid
    ) {

        if (entityUuid == null) {
            return null;
        }

        return lastAttackTimes.get(
                entityUuid
        );
    }

    /**
     * 记录受伤时刻（动画信号 hurt 动作窗口）。
     */
    public void markHurt(
            UUID entityUuid
    ) {

        assert Thread.currentThread() == mainThread
                : "CatBattleState is main-thread only";

        if (entityUuid == null) {
            return;
        }

        lastHurtTimes.put(
                entityUuid,
                System.currentTimeMillis()
        );

        lastHurtNanos.put(
                entityUuid,
                System.nanoTime()
        );
    }

    /**
     * 最近一次受伤时间戳（无记录返回 null）。
     */
    public Long getLastHurtMillis(
            UUID entityUuid
    ) {

        if (entityUuid == null) {
            return null;
        }

        return lastHurtTimes.get(
                entityUuid
        );
    }

    /**
     * 最近攻击的单调时间戳（nanoTime；无记录返回 null）。
     * 动画信号专用（0.9.0更新）。
     */
    public Long getLastAttackNanos(
            UUID entityUuid
    ) {

        if (entityUuid == null) {
            return null;
        }

        return lastAttackNanos.get(
                entityUuid
        );
    }

    /**
     * 最近受伤的单调时间戳（nanoTime；无记录返回 null）。
     */
    public Long getLastHurtNanos(
            UUID entityUuid
    ) {

        if (entityUuid == null) {
            return null;
        }

        return lastHurtNanos.get(
                entityUuid
        );
    }

    /*
     * ============================================================
     * 攻击计数（影袭）
     * ============================================================
     */

    public int nextAttackCount(
            UUID entityUuid
    ) {

        if (entityUuid == null) {
            return 0;
        }

        /*
         * 0.9.0更新：模 5 计数——影袭只需要
         * 0..4 周期，无限增长的 int 最终溢出回绕；
         * 1..5 返回便于调用方 count % 5 == 0 判定。
         */
        int count =
                (attackCounts.getOrDefault(
                        entityUuid,
                        0
                ) % 5) + 1;

        attackCounts.put(
                entityUuid,
                count
        );

        return count;
    }

    /*
     * ============================================================
     * 永恒重生
     * ============================================================
     */

    public boolean tryRebirth(
            UUID entityUuid,
            long cooldownMillis
    ) {

        if (entityUuid == null) {
            return false;
        }

        Long last =
                rebirthTimes.get(
                        entityUuid
                );

        long now =
                System.currentTimeMillis();

        if (last != null &&
                now - last < cooldownMillis) {

            return false;
        }

        rebirthTimes.put(
                entityUuid,
                now
        );

        return true;
    }

    /*
     * ============================================================
     * 协助目标（Issue #6）
     * ============================================================
     */

    public void markAssistTarget(
            UUID ownerUuid,
            UUID targetUuid
    ) {

        if (ownerUuid == null ||
                targetUuid == null) {

            return;
        }

        assistTargets.put(
                ownerUuid,
                targetUuid
        );
    }

    public UUID getAssistTarget(
            UUID ownerUuid
    ) {

        if (ownerUuid == null) {
            return null;
        }

        return assistTargets.get(
                ownerUuid
        );
    }

    public void clearAssistTarget(
            UUID ownerUuid
    ) {

        if (ownerUuid == null) {
            return;
        }

        assistTargets.remove(
                ownerUuid
        );
    }

    /*
     * 羁绊纪元（0.8.0）：
     * 标记饥饿拒战提示；返回 true 表示首次标记（应发消息）。
     */

    public boolean markStarvingAlerted(
            UUID entityUuid
    ) {

        if (entityUuid == null) {
            return false;
        }

        return starvingAlerted.add(
                entityUuid
        );
    }

    public void clearStarvingAlerted(
            UUID entityUuid
    ) {

        if (entityUuid != null) {
            starvingAlerted.remove(
                    entityUuid
            );
        }
    }

    /*
     * ============================================================
     * 追击状态（Issue #6）
     * ============================================================
     */

    public void setChasing(
            UUID entityUuid,
            boolean value
    ) {

        if (entityUuid == null) {
            return;
        }

        if (value) {

            chasing.add(
                    entityUuid
            );

        } else {

            if (chasing.remove(
                    entityUuid
            )) {

                lastChaseEndTimes.put(
                        entityUuid,
                        System.currentTimeMillis()
                );
            }
        }
    }

    public boolean isChasing(
            UUID entityUuid
    ) {

        return entityUuid != null &&
                chasing.contains(
                        entityUuid
                );
    }

    /**
     * 是否正在追击，或刚结束追击不久（收势宽限内）。
     */
    public boolean isChasingOrRecentlyEnded(
            UUID entityUuid,
            long graceMillis
    ) {

        if (entityUuid == null) {
            return false;
        }

        if (chasing.contains(
                entityUuid
        )) {

            return true;
        }

        Long ended =
                lastChaseEndTimes.get(
                        entityUuid
                );

        if (ended == null) {
            return false;
        }

        return System.currentTimeMillis() - ended
                < graceMillis;
    }

    /*
     * ============================================================
     * 扑击限速
     * ============================================================
     */

    public boolean canPounce(
            UUID entityUuid,
            long intervalMillis
    ) {

        if (entityUuid == null) {
            return false;
        }

        Long last =
                lastPounceTimes.get(
                        entityUuid
                );

        if (last == null) {
            return true;
        }

        return System.currentTimeMillis() - last
                >= intervalMillis;
    }

    public void markPounce(
            UUID entityUuid
    ) {

        assert Thread.currentThread() == mainThread
                : "CatBattleState is main-thread only";

        if (entityUuid == null) {
            return;
        }

        lastPounceTimes.put(
                entityUuid,
                System.currentTimeMillis()
        );
    }

    /*
     * ============================================================
     * 受伤恢复期（120 秒，倒计时不重置）
     * ============================================================
     */

    public void markRecovering(
            UUID entityUuid,
            long recoveryMillis
    ) {

        /*
         * 0.9.0更新：主线程契约的运行时断言——
         * assert 仅在启用 -ea 时生效，生产零成本；
         * 未来任何异步接入都会在开发期立即暴露。
         */
        assert Thread.currentThread() == mainThread
                : "CatBattleState is main-thread only";

        if (entityUuid == null) {
            return;
        }

        recoveryEndTimes.put(
                entityUuid,
                System.currentTimeMillis()
                        + recoveryMillis
        );
    }

    public void recordRecoveryEntryState(
            UUID entityUuid,
            boolean hadAi,
            org.bukkit.potion.PotionEffect hadInvisibility
    ) {

        if (entityUuid == null) {
            return;
        }

        aiBeforeRecovery.put(
                entityUuid,
                hadAi
        );

        if (hadInvisibility != null) {

            invisBeforeRecovery.put(
                    entityUuid,
                    hadInvisibility
            );

        } else {

            invisBeforeRecovery.remove(
                    entityUuid
            );
        }
    }

    public boolean consumeAiState(
            UUID entityUuid
    ) {

        Boolean previous =
                aiBeforeRecovery.remove(
                        entityUuid
                );

        return previous == null ||
                previous;
    }

    public org.bukkit.potion.PotionEffect consumeInvisibilityState(
            UUID entityUuid
    ) {

        return invisBeforeRecovery.remove(
                entityUuid
        );
    }

    public boolean isRecovering(
            UUID entityUuid
    ) {

        if (entityUuid == null) {
            return false;
        }

        Long end =
                recoveryEndTimes.get(
                        entityUuid
                );

        return end != null &&
                System.currentTimeMillis() < end;
    }

    /**
     * 剩余恢复毫秒数；不在恢复期返回 null。
     */
    public Long getRecoveryRemainingMillis(
            UUID entityUuid
    ) {

        if (entityUuid == null) {
            return null;
        }

        Long end =
                recoveryEndTimes.get(
                        entityUuid
                );

        if (end == null) {
            return null;
        }

        long remaining =
                end - System.currentTimeMillis();

        return Math.max(
                0,
                remaining
        );
    }

    public int getRecoveryRemainingSeconds(
            UUID entityUuid
    ) {

        Long remaining =
                getRecoveryRemainingMillis(
                        entityUuid
                );

        if (remaining == null) {
            return 0;
        }

        return (int) Math.ceil(
                remaining / 1000.0
        );
    }

    public void clearRecovery(
            UUID entityUuid
    ) {

        if (entityUuid == null) {
            return;
        }

        recoveryEndTimes.remove(
                entityUuid
        );

        lastSweepTimes.remove(
                entityUuid
        );

        lastSweepDisplaySeconds.remove(
                entityUuid
        );
    }

    /*
     * ============================================================
     * 缓慢回血（4 秒 1 点，恢复期外）
     * ============================================================
     */

    public boolean canRegen(
            UUID entityUuid,
            long intervalMillis
    ) {

        if (entityUuid == null) {
            return false;
        }

        Long last =
                lastRegenTimes.get(
                        entityUuid
                );

        if (last == null) {
            return true;
        }

        return System.currentTimeMillis() - last
                >= intervalMillis;
    }

    public void markRegen(
            UUID entityUuid
    ) {

        if (entityUuid == null) {
            return;
        }

        lastRegenTimes.put(
                entityUuid,
                System.currentTimeMillis()
        );
    }

    /*
     * ============================================================
     * 恢复期目标清扫节流（P0-9）
     * ============================================================
     *
     * 恢复期降频清扫：约每秒执行一次半径扫描 + 悬浮字刷新；
     * 同秒内倒计时跨整数秒边界时补一次刷新，保证显示准确。
     */

    public boolean shouldSweepTargets(
            UUID entityUuid,
            long remainingSeconds
    ) {

        if (entityUuid == null) {
            return false;
        }

        long now =
                System.currentTimeMillis();

        Long last =
                lastSweepTimes.get(
                        entityUuid
                );

        if (last == null ||
                now - last >= 1000L) {

            lastSweepTimes.put(
                    entityUuid,
                    now
            );

            lastSweepDisplaySeconds.put(
                    entityUuid,
                    remainingSeconds
            );

            return true;
        }

        Long lastDisplay =
                lastSweepDisplaySeconds.get(
                        entityUuid
                );

        if (lastDisplay != null &&
                !lastDisplay.equals(
                        remainingSeconds
                )) {

            lastSweepDisplaySeconds.put(
                    entityUuid,
                    remainingSeconds
            );

            return true;
        }

        return false;
    }

    /*
     * ============================================================
     * 状态清理
     * ============================================================
     *
     * 由战斗任务周期性调用：
     * 移除已不存在实体/主人的残留状态，
     * 防止长跑服务器上各 Map 无限膨胀。
     */

    /*
     * ============================================================
     * 梦境编织（0.9.0更新）：濒死救援冷却
     * ============================================================
     *
     * 单调时钟（0.9.0更新）：60 秒冷却，
     * 防止 BOSS 战反复触发。
     */

    private static final long DREAM_RESCUE_COOLDOWN_NANOS =
            60_000_000_000L;

    /*
     * 0.9.0更新：恢复前的 AI 与隐身状态——
     * 结束恢复时按原值还原，而不是强制 true / 永久删隐身
     * （其它系统设置的 AI=false 或隐身不能被恢复副作用抹掉）。
     */
    private final Map<UUID, Boolean> aiBeforeRecovery =
            new java.util.HashMap<>();

    private final Map<UUID, org.bukkit.potion.PotionEffect> invisBeforeRecovery =
            new java.util.HashMap<>();

    private final Map<UUID, Long> dreamRescueLast =
            new HashMap<>();

    public boolean canDreamRescue(
            UUID entityUuid
    ) {

        if (entityUuid == null) {
            return false;
        }

        Long last =
                dreamRescueLast.get(
                        entityUuid
                );

        if (last == null) {
            return true;
        }

        long elapsed =
                System.nanoTime() - last;

        if (elapsed < 0) {

            /*
             * 时钟异常：保守放行（恢复一次无害）。
             */
            return true;
        }

        return elapsed >=
                DREAM_RESCUE_COOLDOWN_NANOS;
    }

    public void markDreamRescue(
            UUID entityUuid
    ) {

        if (entityUuid == null) {
            return;
        }

        dreamRescueLast.put(
                entityUuid,
                System.nanoTime()
        );
    }

    /**
     * 实体失效时清理救援冷却（与 retainOnly 同口径）。
     */
    public void retainOnlyDreamRescue(
            java.util.Collection<UUID> entities
    ) {

        dreamRescueLast.keySet()
                .retainAll(
                        entities
                );
    }

    public void retainOnly(
            Collection<UUID> activeEntityUuids,
            Collection<UUID> activeOwnerUuids
    ) {

        Set<UUID> entities =
                activeEntityUuids == null
                        ? Set.of()
                        : new HashSet<>(
                        activeEntityUuids
                );

        Set<UUID> owners =
                activeOwnerUuids == null
                        ? Set.of()
                        : new HashSet<>(
                        activeOwnerUuids
                );

        /*
         * 0.9.0更新：lastHurtTimes 与第八轮新增的
         * lastAttackNanos / lastHurtNanos 此前未纳入清理清单——
         * 每代猫实体 UUID 都会在这三张表中永久残留（泄漏）。
         */
        lastAttackTimes.keySet()
                .retainAll(entities);

        aiBeforeRecovery.keySet()
                .retainAll(entities);

        invisBeforeRecovery.keySet()
                .retainAll(entities);

        lastHurtTimes.keySet()
                .retainAll(entities);

        lastAttackNanos.keySet()
                .retainAll(entities);

        lastHurtNanos.keySet()
                .retainAll(entities);

        attackCounts.keySet()
                .retainAll(entities);

        rebirthTimes.keySet()
                .retainAll(entities);

        lastPounceTimes.keySet()
                .retainAll(entities);

        lastChaseEndTimes.keySet()
                .retainAll(entities);

        recoveryEndTimes.keySet()
                .retainAll(entities);

        lastRegenTimes.keySet()
                .retainAll(entities);

        lastSweepTimes.keySet()
                .retainAll(entities);

        lastSweepDisplaySeconds.keySet()
                .retainAll(entities);

        chasing.retainAll(entities);

        starvingAlerted.retainAll(entities);

        assistTargets.keySet()
                .retainAll(owners);

        /*
         * 0.9.0更新：dreamRescueLast 此前只有独立
         * 清理方法、未被 retainOnly 调用——每代猫实体 UUID 泄漏。
         */
        retainOnlyDreamRescue(
                entities
        );
    }
}

