package mizukichou.nekonyume.model;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.skill.CatBattleState;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * 猫实体信号 → 动画名适配器（架构 §47：Entity Adapter）。
 *
 * <p>
 * 动画名即模型作者约定的标准名（§20）。每个信号带<b>候选名表</b>：
 * 模型作者可用别名（真实 Blockbench 模型的动画名往往非标准，
 * 如 "sit down"、"siting and prone"）；候选全部缺失时回退
 * {@link #ANIM_IDLE}（知识包 P1-21：绝不残留上一个姿态——
 * "坐着的猫腿还在走"）。</p>
 *
 * <p>
 * 动作信号（attack / hurt）由<b>时间窗口</b>驱动（知识包 P1-23/P1-24）：
 * attack = 最近 {@link #ATTACK_WINDOW_MILLIS} 内攻击过，
 * hurt = 最近 {@link #HURT_WINDOW_MILLIS} 内受过伤——
 * 动作只播放一次，绝不用 {@code getTarget() != null} 近似
 * （那会导致动画反复 restart）。</p>
 *
 * <p>
 * 判定优先级：恢复 &gt; 坐下 &gt; 动作（攻击/受伤）&gt; 移动 &gt; 进食 &gt; 空闲。
 * </p>
 */
public final class CatAnimationAdapter {

    public static final String ANIM_IDLE = "idle";

    public static final String ANIM_WALK = "walk";

    public static final String ANIM_SIT = "sit";

    public static final String ANIM_ATTACK = "attack";

    public static final String ANIM_HURT = "hurt";

    public static final String ANIM_EAT = "eat";

    /**
     * 攻击动作窗口（毫秒）。
     */
    private static final long ATTACK_WINDOW_NANOS =
            600_000_000L;

    /**
     * 受伤动作窗口（毫秒）。
     */
    private static final long HURT_WINDOW_NANOS =
            1_000_000_000L;

    /**
     * 喂食动画窗口（毫秒）。
     */
    private static final long EAT_WINDOW_MILLIS = 2500L;

    /**
     * 判定为移动的最小水平速度平方（防御：null 速度视为静止）。
     */
    private static final double WALK_VELOCITY_SQUARED = 0.01;

    /**
     * 候选名表（§20 标准名映射由 Adapter 定义）：
     * 真实模型作者的常见别名也在表内。
     */
    private static final List<String> SIT_CANDIDATES =
            List.of(
                    "sit",
                    "sit_down",
                    "sitting",
                    "siting_and_prone",
                    "prone"
            );

    private static final List<String> IDLE_CANDIDATES =
            List.of(
                    "idle",
                    "stand"
            );

    private static final List<String> WALK_CANDIDATES =
            List.of(
                    "walk"
            );

    private static final List<String> ATTACK_CANDIDATES =
            List.of(
                    "attack"
            );

    private static final List<String> HURT_CANDIDATES =
            List.of(
                    "hurt"
            );

    private static final List<String> EAT_CANDIDATES =
            List.of(
                    "eat"
            );

    private CatAnimationAdapter() {
    }

    /**
     * 动作信号（一次性触发，边沿驱动）还是状态信号
     * （持续重采样）——0.9.0更新。
     */
    public static boolean isActionSignal(
            String signal
    ) {

        return ANIM_ATTACK.equals(
                        signal
                ) ||
                ANIM_HURT.equals(
                        signal
                ) ||
                ANIM_EAT.equals(
                        signal
                );
    }

    /**
     * 从猫的领域状态与实体状态推导候选动画名（有序）。
     *
     * <p>
     * 调用方逐个尝试（第一个存在者命中）；全部缺失
     * 时以 idle 结尾（知识包 P1-21：绝不残留上一姿态）。
     * </p>
     */
    public static List<String> resolveAnimationCandidates(
            Cat logicalCat,
            Entity entity,
            CatBattleState battleState
    ) {

        if (logicalCat == null ||
                entity == null ||
                battleState == null) {

            return IDLE_CANDIDATES;
        }

        UUID entityUuid =
                entity.getUniqueId();

        /*
         * 0.9.0更新：动画信号统一单调时钟
         * （nanoTime），与 AnimationController 同一时间线；
         * 窗口判定防御时间戳损坏导致的溢出。
         */
        long now =
                System.nanoTime();

        if (battleState.isRecovering(
                entityUuid
        )) {

            return HURT_CANDIDATES;
        }

        if (logicalCat.getBehaviorMode() ==
                CatBehaviorMode.SIT) {

            return SIT_CANDIDATES;
        }

        /*
         * 动作窗口：最近攻击 / 受伤（时间戳驱动）。
         */
        Long lastAttack =
                battleState.getLastAttackNanos(
                        entityUuid
                );

        if (lastAttack != null &&
                now >= lastAttack &&
                now - lastAttack <
                        ATTACK_WINDOW_NANOS) {

            return ATTACK_CANDIDATES;
        }

        Long lastHurt =
                battleState.getLastHurtNanos(
                        entityUuid
                );

        if (lastHurt != null &&
                now >= lastHurt &&
                now - lastHurt <
                        HURT_WINDOW_NANOS) {

            return HURT_CANDIDATES;
        }

        Vector velocity =
                entity.getVelocity();

        if (velocity != null) {

            double horizontal =
                    velocity.getX() * velocity.getX() +
                            velocity.getZ() * velocity.getZ();

            if (horizontal >
                    WALK_VELOCITY_SQUARED) {

                return WALK_CANDIDATES;
            }
        }

        long fedAt =
                logicalCat.getLastFedAt();

        /*
         * 喂食时间戳来自 Cat 领域（wall-clock 持久化字段），
         * 该判定独立使用 wall 时钟（防御溢出）。
         */
        long wallNow =
                System.currentTimeMillis();

        if (fedAt > 0 &&
                wallNow >= fedAt &&
                wallNow - fedAt <
                        EAT_WINDOW_MILLIS) {

            return EAT_CANDIDATES;
        }

        return IDLE_CANDIDATES;
    }
}
