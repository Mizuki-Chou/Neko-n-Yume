package mizukichou.nekonyume.skill;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 猫咪战斗运行时状态。
 *
 * <p>
 * 由 CatBattleTask 与 CatEntityListener 共享：
 * 虚弱计时、永恒重生冷却、攻击间隔、暴击计数。
 * </p>
 *
 * <p>
 * 全部为内存状态，重启重置。
 * </p>
 */
public class CatBattleState {

    /*
     * 猫实体 UUID → 上次致死保护时间。
     */
    private final Map<UUID, Long> protectedAt =
            new ConcurrentHashMap<>();

    /*
     * 猫实体 UUID → 上次满血重生时间（永恒）。
     */
    private final Map<UUID, Long> lastRebirth =
            new ConcurrentHashMap<>();

    /*
     * 猫实体 UUID → 上次攻击时间。
     */
    private final Map<UUID, Long> lastAttack =
            new ConcurrentHashMap<>();

    /*
     * 猫实体 UUID → 攻击次数（影袭暴击计数）。
     */
    private final Map<UUID, Integer> attackCounters =
            new ConcurrentHashMap<>();

    /*
     * 是否处于虚弱期。
     */
    public boolean isWeakened(
            UUID catUuid,
            long weaknessMillis
    ) {

        Long last =
                protectedAt.get(
                        catUuid
                );

        if (last == null) {
            return false;
        }

        return System.currentTimeMillis()
                - last < weaknessMillis;
    }

    public void markProtected(
            UUID catUuid
    ) {

        protectedAt.put(
                catUuid,
                System.currentTimeMillis()
        );
    }

    /*
     * 尝试满血重生。
     * 冷却未到返回 false。
     */
    public boolean tryRebirth(
            UUID catUuid,
            long cooldownMillis
    ) {

        long now =
                System.currentTimeMillis();

        Long last =
                lastRebirth.get(
                        catUuid
                );

        if (last != null &&
                now - last < cooldownMillis) {

            return false;
        }

        lastRebirth.put(
                catUuid,
                now
        );

        return true;
    }

    /*
     * 攻击间隔检查。
     */
    public boolean canAttack(
            UUID catUuid,
            long intervalMillis
    ) {

        Long last =
                lastAttack.get(
                        catUuid
                );

        if (last == null) {
            return true;
        }

        return System.currentTimeMillis()
                - last >= intervalMillis;
    }

    public void markAttack(
            UUID catUuid
    ) {

        lastAttack.put(
                catUuid,
                System.currentTimeMillis()
        );
    }

    /*
     * 攻击计数（从 1 开始递增）。
     */
    public int nextAttackCount(
            UUID catUuid
    ) {

        int next =
                attackCounters.getOrDefault(
                        catUuid,
                        0
                )
                        + 1;

        attackCounters.put(
                catUuid,
                next
        );

        return next;
    }
}
