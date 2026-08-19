package mizukichou.nekonyume.util;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Warden;

import java.lang.reflect.Method;

/**
 * 怪物目标清理工具。
 *
 * <p>
 * 受伤恢复期保护：
 * 1. 清空半径内所有以猫为攻击目标的怪物目标；
 * 2. 监守者特殊处理——它不走标准索敌而是用"愤怒系统"
 *    记忆目标，所以除了清目标，还要把对猫的愤怒值清零
 *    （通过 Paper 的 Warden#setAnger 反射调用，
 *    接口不存在时静默跳过，不影响编译与运行）。
 * </p>
 *
 * <p>
 * 性能约束：
 * - 只对恢复期中的猫调用；
 * - 半径受限（默认 24 格），代价与附近实体数成正比；
 * - 只读 getTarget() 与清空，不触发额外事件。
 * </p>
 */
public final class TargetGuard {

    /*
     * Paper: org.bukkit.entity.Warden#setAnger(Entity, int)
     * 用反射软依赖，避免硬编码 API 签名。
     */
    private static final Method WARDEN_SET_ANGER =
            findWardenSetAnger();

    private TargetGuard() {
    }

    /**
     * 清空半径 radius 内所有以 cat 为攻击目标的怪物目标，
     * 并清零监守者对猫的愤怒值。
     */
    public static void clearTargetsOn(
            org.bukkit.entity.Cat cat,
            double radius
    ) {

        if (cat == null) {
            return;
        }

        World world =
                cat.getWorld();

        if (world == null) {
            return;
        }

        for (Entity entity :
                world.getNearbyEntities(
                        cat.getLocation(),
                        radius,
                        radius,
                        radius
                )) {

            /*
             * 标准索敌路径：清空目标。
             */
            if (entity instanceof Mob mob) {

                LivingEntity target =
                        mob.getTarget();

                if (target != null &&
                        target.equals(
                                cat
                        )) {

                    mob.setTarget(
                            null
                    );
                }
            }

            /*
             * 监守者：愤怒系统不走标准目标，
             * 必须把对这只猫的愤怒值清零，
             * 否则它下一秒就重新锁定。
             */
            if (entity instanceof Warden warden) {

                resetWardenAnger(
                        warden,
                        cat
                );
            }
        }
    }

    private static void resetWardenAnger(
            Warden warden,
            org.bukkit.entity.Cat cat
    ) {

        if (WARDEN_SET_ANGER == null) {
            return;
        }

        try {

            WARDEN_SET_ANGER.invoke(
                    warden,
                    cat,
                    0
            );

        } catch (Exception ignored) {

            /*
             * 反射失败不影响主流程：
             * 愤怒值会随时间自然衰减，
             * 且猫已冻结/隐身/停止传送，
             * 监守者不会再收到新的刺激信号。
             */
        }
    }

    private static Method findWardenSetAnger() {

        try {

            return Warden.class.getMethod(
                    "setAnger",
                    Entity.class,
                    int.class
            );

        } catch (NoSuchMethodException ignored) {

            return null;
        }
    }
}

