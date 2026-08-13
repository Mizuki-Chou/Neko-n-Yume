package mizukichou.nekonyume.task;

import mizukichou.nekonyume.NekoNYume;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;

import java.util.UUID;

public class CatPositionTask implements Runnable {

    /*
     * ============================================================
     * 猫咪位置同步
     * ============================================================
     *
     * NekoNYume.onEnable() 中每 30 秒执行一次。
     *
     * 这里不直接写 players.yml。
     *
     * 正确的数据流：
     *
     * Bukkit Cat
     *     ↓
     * 运行时 Cat
     *     ↓
     * 自动保存系统
     *     ↓
     * players.yml
     */

    private static final long POSITION_INTERVAL =
            30L * 1000L;

    private final NekoNYume plugin;

    public CatPositionTask(
            NekoNYume plugin
    ) {

        this.plugin = plugin;
    }

    @Override
    public void run() {

        /*
         * ========================================================
         * 只处理当前已经加载到内存中的 Cat
         * ========================================================
         *
         * 不再遍历 players.yml。
         *
         * 这样可以保证：
         *
         * CatManager 中的 Cat
         * 是当前运行时唯一状态。
         */

        for (mizukichou.nekonyume.cat.Cat logicalCat :
                plugin.getCatManager().getCats()) {

            /*
             * 获取当前 Bukkit Entity UUID。
             */
            UUID entityUUID =
                    logicalCat.getEntityUuid();

            if (entityUUID == null) {
                continue;
            }

            /*
             * 根据 UUID 获取 Minecraft 实体。
             */
            Entity entity =
                    Bukkit.getEntity(
                            entityUUID
                    );

            /*
             * 找不到实体直接跳过。
             *
             * 注意：
             * 这里绝对不会自动生成新猫。
             *
             * 实体恢复属于 CatManager 的职责。
             */
            if (!(entity instanceof Cat cat)) {
                continue;
            }

            /*
             * 实体已经死亡。
             */
            if (cat.isDead() ||
                    !cat.isValid()) {

                continue;
            }

            /*
             * ====================================================
             * 安全验证主人
             * ====================================================
             *
             * 防止逻辑 Cat 和 Bukkit Entity 错绑。
             */

            String ownerUUID =
                    cat.getPersistentDataContainer()
                            .get(
                                    plugin.getCatManager()
                                            .getOwnerKey(),
                                    org.bukkit.persistence.PersistentDataType.STRING
                            );

            if (ownerUUID == null) {
                continue;
            }

            if (!logicalCat.getOwnerUuid()
                    .toString()
                    .equals(ownerUUID)) {

                continue;
            }

            /*
             * ====================================================
             * 获取实际位置
             * ====================================================
             */

            Location location =
                    cat.getLocation();

            World world =
                    location.getWorld();

            if (world == null) {
                continue;
            }

            /*
             * ====================================================
             * 更新运行时 Cat
             * ====================================================
             */

            logicalCat.setWorldName(
                    world.getName()
            );

            logicalCat.setX(
                    location.getX()
            );

            logicalCat.setY(
                    location.getY()
            );

            logicalCat.setZ(
                    location.getZ()
            );

            logicalCat.setYaw(
                    location.getYaw()
            );

            logicalCat.setPitch(
                    location.getPitch()
            );

            /*
             * 确保 Entity UUID 仍然对应当前实体。
             */
            logicalCat.setEntityUuid(
                    cat.getUniqueId()
            );
        }
    }
}