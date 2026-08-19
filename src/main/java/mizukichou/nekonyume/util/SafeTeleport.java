package mizukichou.nekonyume.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/**
 * 传送落点安全校验。
 *
 * <p>
 * 用于"传送式跟随"与"战斗扑击"：
 * 传送前检查脚部/头部可通行、
 * 下方有实体、且不在岩浆或水中。
 * </p>
 */
public final class SafeTeleport {

    private SafeTeleport() {
    }

    /**
     * 该位置能否安全容纳一只猫：
     * - 脚部方块与头部方块可通行；
     * - 下方不是空气/液体；
     * - 不落在岩浆或水中。
     */
    public static boolean isSafeForCat(
            Location location
    ) {

        if (location == null) {
            return false;
        }

        World world =
                location.getWorld();

        if (world == null) {
            return false;
        }

        int x =
                (int) Math.floor(
                        location.getX()
                );

        int y =
                (int) Math.floor(
                        location.getY()
                );

        int z =
                (int) Math.floor(
                        location.getZ()
                );

        Block feet =
                world.getBlockAt(
                        x,
                        y,
                        z
                );

        Block head =
                world.getBlockAt(
                        x,
                        y + 1,
                        z
                );

        Block below =
                world.getBlockAt(
                        x,
                        y - 1,
                        z
                );

        return isPassable(feet) &&
                isPassable(head) &&
                isGround(below);
    }

    /**
     * 在"起点 → 目标"连线上寻找安全的扑击落点。
     *
     * <p>
     * 依次尝试 hop × {1.0, 0.75, 0.5, 0.3} 四种距离，
     * 每种距离再扫描目标高度附近的
     * {0, +1, -1, +2, +3, -2} 六档高度，
     * 返回第一个安全位置；
     * 全部失败返回 null（调用方应放弃本次传送，
     * 下一个 tick 目标移动后自然重试）。
     * </p>
     */
    public static Location findPounceDestination(
            Location from,
            Location target,
            double maxHop
    ) {

        if (from == null ||
                target == null) {

            return null;
        }

        if (from.getWorld() == null ||
                target.getWorld() == null ||
                !from.getWorld()
                        .equals(
                                target.getWorld()
                        )) {

            return null;
        }

        Vector direction =
                target.toVector()
                        .subtract(
                                from.toVector()
                        );

        if (direction.lengthSquared() <= 0) {
            return null;
        }

        direction.normalize();

        double fullDistance =
                from.distance(
                        target
                );

        /*
         * 与目标保持至少 1 格距离，
         * 避免直接跳进目标的同一格。
         */
        double baseHop =
                Math.min(
                        fullDistance - 1.0,
                        maxHop
                );

        if (baseHop <= 0) {
            return null;
        }

        double[] hopFractions = {
                1.0,
                0.75,
                0.5,
                0.3
        };

        int[] yOffsets = {
                0,
                1,
                -1,
                2,
                3,
                -2
        };

        double targetY =
                target.getY();

        for (double fraction :
                hopFractions) {

            double hop =
                    baseHop
                            * fraction;

            Vector delta =
                    direction.clone()
                            .multiply(
                                    hop
                            );

            for (int yOffset :
                    yOffsets) {

                Location candidate =
                        from.clone()
                                .add(
                                        delta
                                );

                candidate.setY(
                        targetY
                                + yOffset
                );

                candidate.setYaw(
                        from.getYaw()
                );

                candidate.setPitch(
                        from.getPitch()
                );

                if (isSafeForCat(
                        candidate
                )) {

                    return candidate;
                }
            }
        }

        return null;
    }

    private static boolean isPassable(
            Block block
    ) {

        Material type =
                block.getType();

        /*
         * 绝不传送进岩浆或水。
         */
        if (type == Material.LAVA ||
                type == Material.WATER) {

            return false;
        }

        /*
         * 完整方块（石头 / 玻璃 / 墙等）不可通行；
         * 空气、半砖、树叶、草等可通行。
         */
        return !type.isOccluding();
    }

    private static boolean isGround(
            Block block
    ) {

        /*
         * 下方必须是"非空气、非液体"的实体方块。
         */
        return !block.isPassable();
    }
}
