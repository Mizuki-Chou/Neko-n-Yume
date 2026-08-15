package mizukichou.nekonyume.task;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatMood;
import mizukichou.nekonyume.skill.CatBattleState;
import mizukichou.nekonyume.util.SafeTeleport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CatBehaviorTask implements Runnable {

    /*
     * ============================================================
     * 跟随传送阈值（水平距离，格）
     * ============================================================
     *
     * 心情影响阈值：
     *
     * 非常开心 → 8 格（紧紧粘着你）
     * 开心/平静 → 10 格
     * 低落/难过 → 16 格（懒洋洋，离得远才跟）
     */
    private static final double ECSTATIC_FOLLOW_DISTANCE = 8.0;

    private static final double NORMAL_FOLLOW_DISTANCE = 10.0;

    private static final double LOW_FOLLOW_DISTANCE = 16.0;

    /*
     * 追击结束后的收势宽限（毫秒）。
     *
     * 猫刚结束追击的 3 秒内不做跟随传送，
     * 避免"刚咬死怪就被瞬移拉回主人身边"的突兀感。
     */
    private static final long CHASE_END_GRACE_MS =
            3000L;

    private final CatCache cache;

    private final CatBattleState battleState;

    public CatBehaviorTask(
            CatCache cache,
            CatBattleState battleState
    ) {

        this.cache = cache;
        this.battleState = battleState;
    }

    @Override
    public void run() {

        for (Cat logicalCat :
                cache.getCats()) {

            /*
             * 只处理在线主人的猫。
             */
            Player owner =
                    Bukkit.getPlayer(
                            logicalCat.getOwnerUuid()
                    );

            if (owner == null ||
                    !owner.isOnline()) {

                continue;
            }

            UUID entityUuid =
                    logicalCat.getEntityUuid();

            if (entityUuid == null) {
                continue;
            }

            Entity entity =
                    Bukkit.getEntity(
                            entityUuid
                    );

            if (!(entity instanceof org.bukkit.entity.Cat cat) ||
                    cat.isDead() ||
                    !cat.isValid()) {

                continue;
            }

            switch (logicalCat.getBehaviorMode()) {

                case SIT ->
                        cat.setSitting(
                                true
                        );

                case FREE -> {
                    /*
                     * 自由模式不干预，
                     * 玩家可空手右键切换坐姿。
                     */
                }

                case FOLLOW ->
                        handleFollow(
                                logicalCat,
                                cat,
                                owner
                        );
            }
        }
    }

    /*
     * ============================================================
     * 跟随处理
     * ============================================================
     *
     * 传送而不是寻路：
     *
     * - Entity#teleport 是 Bukkit 核心 API，
     *   不依赖可能变化的 Paper 专有接口；
     * - 原版驯服猫本来就有
     *   "离主人太远自动传送"的行为，
     *   玩家不会觉得违和；
     * - 没有路径计算开销。
     */

    private void handleFollow(
            Cat logicalCat,
            org.bukkit.entity.Cat cat,
            Player owner
    ) {

        /*
         * 受伤恢复期内完全不干预：
         * 猫原地冻结，传送振动会向监守者暴露猫的位置。
         */
        if (battleState.isRecovering(
                cat.getUniqueId()
        )) {

            return;
        }

        /*
         * 跟随前先解除坐姿。
         */
        if (cat.isSitting()) {

            cat.setSitting(
                    false
            );
        }

        Location catLoc =
                cat.getLocation();

        Location ownerLoc =
                owner.getLocation();

        /*
         * 跨世界不跟随。
         * 玩家可以用 /nekoyume summon 把猫带过去。
         */
        if (catLoc.getWorld() == null ||
                !catLoc.getWorld()
                        .equals(ownerLoc.getWorld())) {

            return;
        }

        /*
         * Issue #6：
         * 猫正在追击敌人时不把它传送回主人身边，
         * 否则跟随传送会与战斗扑击互相拉扯。
         *
         * 追击结束后保留 3 秒"收势宽限"，
         * 避免刚咬死怪就被瞬移拉回。
         */
        if (battleState.isChasingOrRecentlyEnded(
                cat.getUniqueId(),
                CHASE_END_GRACE_MS
        )) {

            return;
        }

        /*
         * 水平距离。
         */
        double dx =
                catLoc.getX()
                        - ownerLoc.getX();

        double dz =
                catLoc.getZ()
                        - ownerLoc.getZ();

        double distance =
                Math.sqrt(
                        dx * dx
                                + dz * dz
                );

        double threshold =
                followThreshold(
                        logicalCat
                );

        if (distance <= threshold) {
            return;
        }

        /*
         * 传送到主人身边随机偏移处，
         * 避免与玩家重叠。
         */
        double offsetX =
                ThreadLocalRandom.current()
                        .nextDouble(
                                -1.5,
                                1.5
                        );

        double offsetZ =
                ThreadLocalRandom.current()
                        .nextDouble(
                                -1.5,
                                1.5
                        );

        Location candidate =
                ownerLoc.clone()
                        .add(
                                offsetX,
                                0.5,
                                offsetZ
                        );

        candidate.setYaw(
                catLoc.getYaw()
        );

        candidate.setPitch(
                catLoc.getPitch()
        );

        /*
         * 候选点卡墙时回退到主人脚下；
         * 主人脚下也不安全（如游泳 / 贴墙）则本 tick 放弃，
         * 下一个 tick 会自然重试。
         */
        if (!SafeTeleport.isSafeForCat(
                candidate
        )) {

            Location fallback =
                    ownerLoc.clone();

            fallback.setYaw(
                    catLoc.getYaw()
            );

            fallback.setPitch(
                    catLoc.getPitch()
            );

            if (!SafeTeleport.isSafeForCat(
                    fallback
            )) {

                return;
            }

            candidate = fallback;
        }

        cat.teleport(
                candidate
        );
    }

    private double followThreshold(
            Cat cat
    ) {

        CatMood mood =
                cat.getMood();

        return switch (mood) {

            case ECSTATIC ->
                    ECSTATIC_FOLLOW_DISTANCE;

            case LOW, SAD ->
                    LOW_FOLLOW_DISTANCE;

            default ->
                    NORMAL_FOLLOW_DISTANCE;
        };
    }
}
