package mizukichou.nekonyume.skill;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 猫咪战斗运行状态。
 *
 * <p>
 * 不持久化，重启重置：
 * 攻击间隔 / 攻击计数 / 重生冷却 / 协助目标 /
 * 追击状态 / 扑击冷却 / 追击收势 /
 * 受伤恢复期 / 缓慢回血 / 恢复期清扫节流。
 * </p>
 */
public class CatBattleState {

    /*
     * 实体 UUID → 上次攻击时间（毫秒）。
     */
    private final Map<UUID, Long> lastAttackTimes =
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

        if (entityUuid == null) {
            return;
        }

        lastAttackTimes.put(
                entityUuid,
                System.currentTimeMillis()
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

        int count =
                attackCounts.getOrDefault(
                        entityUuid,
                        0
                ) + 1;

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

        if (entityUuid == null) {
            return;
        }

        recoveryEndTimes.put(
                entityUuid,
                System.currentTimeMillis()
                        + recoveryMillis
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

        lastAttackTimes.keySet()
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

        assistTargets.keySet()
                .retainAll(owners);
    }
}