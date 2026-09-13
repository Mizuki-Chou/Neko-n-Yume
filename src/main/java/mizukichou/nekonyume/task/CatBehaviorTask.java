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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class CatBehaviorTask implements Runnable {

    /*
     * ============================================================
     * 跟随寻路（0.9.0更新：传送式 → 寻路式）
     * ============================================================
     *
     * 猫真的走起来：walk 动画（速度驱动）自然触发，
     * 路径被卡 / 无法寻路时兜底传送（旧传送逻辑保留为
     * 安全网）。距离语义见 {@link FollowController}：
     *
     * ≤ 2.5 格 → 停；> 32 格 → 直接传送；之间 → 寻路，
     * 连续 4 轮（约 4 秒）无进展 → 兜底传送。
     */

    /*
     * 心情 → 寻路速度倍率（相对实体基础移速）。
     *
     * 非常开心 → 3.4（紧紧粘着你，走快一点）喵
     * 开心/平静 → 2.8
     * 低落/难过 → 2.2（懒洋洋地慢慢跟）
     */
    private static final double ECSTATIC_FOLLOW_SPEED = 3.4;

    private static final double NORMAL_FOLLOW_SPEED = 2.8;

    private static final double LOW_FOLLOW_SPEED = 2.2;

    /*
     * 追击结束后的收势宽限（毫秒）。
     *
     * 猫刚结束追击的 3 秒内不做跟随寻路，
     * 避免"刚咬死怪就被拉着往回走"的突兀感。
     */
    private static final long CHASE_END_GRACE_MS =
            3000L;

    private final CatCache cache;

    private final CatBattleState battleState;

    private final Logger logger;

    /*
     * 每猫跟随决策器（键 = 实体 UUID）。
     */
    private final Map<UUID, FollowController> controllers =
            new HashMap<>();

    /*
     * 上一轮 moveTo 是否找到路径（键 = 实体 UUID）。
     */
    private final Map<UUID, Boolean> lastPathFound =
            new HashMap<>();

    /*
     * 已清空原版 AI 的实体（只做一次）。
     */
    private final Set<UUID> clearedGoals =
            new HashSet<>();

    public CatBehaviorTask(
            CatCache cache,
            CatBattleState battleState,
            Logger logger
    ) {

        this.cache = cache;
        this.battleState = battleState;
        this.logger = logger;
    }

    @Override
    public void run() {

        for (Cat logicalCat :
                cache.getCats()) {

            /*
             * 单猫异常隔离，
             * 与 CatBattleTask 口径一致。
             */
            try {

                processCat(
                        logicalCat
                );

            } catch (Exception exception) {

                logger.warning(
                        "Behavior tick failed for cat "
                                + logicalCat.getId()
                                + ": "
                                + exception.getMessage()
                );
            }
        }
    }

    private void processCat(
            Cat logicalCat
    ) {

        /*
         * 只处理在线主人的猫。
         */
        Player owner =
                Bukkit.getPlayer(
                        logicalCat.getOwnerUuid()
                );

        if (owner == null ||
                !owner.isOnline()) {

            return;
        }

        UUID entityUuid =
                logicalCat.getEntityUuid();

        if (entityUuid == null) {
            return;
        }

        Entity entity =
                Bukkit.getEntity(
                        entityUuid
                );

        if (!(entity instanceof org.bukkit.entity.Cat cat) ||
                cat.isDead() ||
                !cat.isValid()) {

            /*
             * 实体已消失：回收该实体的跟随状态映射，
             * 防止长期运行下 Map 无界增长。
             */
            controllers.remove(
                    entityUuid
            );

            lastPathFound.remove(
                    entityUuid
            );

            clearedGoals.remove(
                    entityUuid
            );

            return;
        }

        switch (logicalCat.getBehaviorMode()) {

            case SIT -> {

                cat.setSitting(
                        true
                );

                /*
                 * 0.9.0更新：清空原版 AI 后 setSitting
                 * 只置标志位（26.2 反汇编验证：坐姿与停导航
                 * 由 SitWhenOrderedToGoal 实现，已随 goals
                 * 移除）——CatSitGoal 会在目标选择器内补
                 * 坐姿与停导航，这里显式停一次路径，
                 * 保证切换瞬间即停。
                 */
                cat.getPathfinder()
                        .stopPathfinding();
            }

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

    /*
     * ============================================================
     * 跟随处理（寻路式，0.9.0更新）
     * ============================================================
     *
     * 原版驯服猫已有"离主人太远自动传送"的 FollowOwnerGoal，
     * 且其寻路与我们的 moveTo 互相拉扯；猫的实体模型被
     * hideEntity 隐藏（玩家看到的是 ItemDisplay 模型），
     * 原版 AI 没有保留价值——因此每猫清空一次原版目标，
     * 跟随完全由插件接管：
     *
     * - Pathfinder 寻路走向主人（可开门、可浮水）；
     * - 卡住 / 无路径 / 太远 → 兜底传送（SafeTeleport）；
     * - 贴身时停下并看向主人。
     */

    private void handleFollow(
            Cat logicalCat,
            org.bukkit.entity.Cat cat,
            Player owner
    ) {

        /*
         * 受伤恢复期内完全不干预：
         * 猫原地冻结，移动振动会向监守者暴露猫的位置。
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
         * 猫正在追击敌人时不把它拉回主人身边，
         * 否则跟随寻路会与战斗扑击互相拉扯。
         *
         * 追击结束后保留 3 秒"收势宽限"，
         * 避免刚咬死怪就被拉着往回走。
         */
        if (battleState.isChasingOrRecentlyEnded(
                cat.getUniqueId(),
                CHASE_END_GRACE_MS
        )) {

            return;
        }

        /*
         * 原版 AI 只清一次：移除 FollowOwnerGoal 等全部
         * 目标（避免与我们抢导航），并开启开门 / 浮水，
         * 补偿被移除的 FloatGoal / OpenDoorGoal。
         */
        prepareForPathfinding(
                cat
        );

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

        FollowController controller =
                controllers.computeIfAbsent(
                        cat.getUniqueId(),
                        ignored ->
                                new FollowController()
                );

        FollowController.Action action =
                controller.tick(
                        distance,
                        lastPathFound.getOrDefault(
                                cat.getUniqueId(),
                                false
                        )
                );

        switch (action) {

            case STOP -> {

                cat.getPathfinder()
                        .stopPathfinding();

                lastPathFound.put(
                        cat.getUniqueId(),
                        false
                );

                /*
                 * 贴身时看向主人：
                 * 身体朝向（body yaw）会缓缓追头朝向，
                 * 模型随之转向主人。
                 */
                cat.lookAt(
                        owner
                );
            }

            case TELEPORT -> {

                cat.getPathfinder()
                        .stopPathfinding();

                teleportNearOwner(
                        cat,
                        catLoc,
                        ownerLoc
                );

                lastPathFound.put(
                        cat.getUniqueId(),
                        false
                );
            }

            case NAVIGATE -> {

                boolean found =
                        cat.getPathfinder()
                                .moveTo(
                                        ownerLoc,
                                        followSpeed(
                                                logicalCat
                                        )
                                );

                lastPathFound.put(
                        cat.getUniqueId(),
                        found
                );

                /*
                 * 跟随途中看向主人（head yaw），
                 * 模型朝向由 body yaw 决定，不受影响。
                 */
                cat.lookAt(
                        owner
                );
            }

            case NONE -> {
                /*
                 * 数据异常轮：不动，下一轮重试。
                 */
            }
        }
    }

    /**
     * 每猫一次的寻路准备：清空原版 AI、开启开门与浮水。
     */
    private void prepareForPathfinding(
            org.bukkit.entity.Cat cat
    ) {

        UUID uuid =
                cat.getUniqueId();

        if (clearedGoals.contains(
                uuid
        )) {

            return;
        }

        clearedGoals.add(
                uuid
        );

        Bukkit.getMobGoals()
                .removeAllGoals(
                        cat
                );

        /*
         * 注册坐姿目标（替代被移除的 SitWhenOrderedToGoal）：
         * 标志位为真时停导航 + 坐姿，恢复插件 SIT 模式
         * 与原版右键切换坐姿的完整语义。
         */
        Bukkit.getMobGoals()
                .addGoal(
                        cat,
                        1,
                        new CatSitGoal(
                                cat
                        )
                );

        cat.getPathfinder()
                .setCanOpenDoors(
                        true
                );

        cat.getPathfinder()
                .setCanFloat(
                        true
                );
    }

    /**
     * 兜底传送：传送到主人身边随机偏移处（卡墙回退主人脚下），
     * 与旧版传送跟随的安全口径一致。
     */
    private void teleportNearOwner(
            org.bukkit.entity.Cat cat,
            Location catLoc,
            Location ownerLoc
    ) {

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
         * 主人脚下也不安全（如游泳 / 贴墙）则本轮放弃，
         * 下一轮会自然重试。
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

    /**
     * 心情 → 寻路速度倍率。
     */
    private double followSpeed(
            Cat cat
    ) {

        CatMood mood =
                cat.getMood();

        return switch (mood) {

            case ECSTATIC ->
                    ECSTATIC_FOLLOW_SPEED;

            case LOW, SAD ->
                    LOW_FOLLOW_SPEED;

            default ->
                    NORMAL_FOLLOW_SPEED;
        };
    }
}
