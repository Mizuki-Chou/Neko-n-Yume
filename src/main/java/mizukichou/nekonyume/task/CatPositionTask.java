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
     * 每 30 秒同步一次猫咪位置
     */
    private static final long POSITION_INTERVAL =
            30L * 1000L;

    private final NekoNYume plugin;

    public CatPositionTask(NekoNYume plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {

        long now =
                System.currentTimeMillis();

        /*
         * 获取所有拥有猫咪的玩家
         */
        for (UUID playerUUID :
                plugin.getDataManager()
                        .getCatPlayers()) {

            /*
             * 获取猫咪实体 UUID
             */
            UUID entityUUID =
                    plugin.getDataManager()
                            .getCatEntityUUID(
                                    playerUUID
                            );

            if (entityUUID == null) {
                continue;
            }

            /*
             * 根据 UUID 获取实体
             */
            Entity entity =
                    Bukkit.getEntity(
                            entityUUID
                    );

            /*
             * 找不到实体就跳过
             *
             * 注意：
             * 这里绝对不会生成新猫。
             */
            if (!(entity instanceof Cat cat)) {
                continue;
            }

            if (cat.isDead()) {
                continue;
            }

            /*
             * 获取猫咪当前位置
             */
            Location location =
                    cat.getLocation();

            World world =
                    location.getWorld();

            if (world == null) {
                continue;
            }

            /*
             * 保存当前世界和坐标
             */
            plugin.getDataManager()
                    .setCatLocation(
                            playerUUID,
                            world.getUID(),
                            location.getX(),
                            location.getY(),
                            location.getZ()
                    );
        }
    }
}