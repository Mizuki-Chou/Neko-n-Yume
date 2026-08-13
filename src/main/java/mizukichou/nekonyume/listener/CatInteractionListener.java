package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.NekoNYume;
import org.bukkit.Sound;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CatInteractionListener implements Listener {

    private final NekoNYume plugin;

    /*
     * 防止连续快速按 Shift 刷好感度
     *
     * 1 秒最多触发一次
     */
    private final Map<UUID, Long> cooldowns =
            new HashMap<>();

    private static final long COOLDOWN =
            1000L;

    /*
     * 每天最多抚摸 20 次
     */
    private static final int MAX_DAILY_PETS =
            20;

    /*
     * 抚摸距离
     */
    private static final double PET_DISTANCE =
            3.0;

    public CatInteractionListener(
            NekoNYume plugin
    ) {
        this.plugin = plugin;
    }

    /*
     * =========================
     * 玩家开始潜行
     * =========================
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerSneak(
            PlayerToggleSneakEvent event
    ) {

        /*
         * 只有按下 Shift 时触发
         *
         * 松开 Shift 不触发
         */
        if (!event.isSneaking()) {
            return;
        }

        Player player =
                event.getPlayer();

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 找附近自己的猫
         */
        Cat cat =
                findNearbyCat(
                        player
                );

        /*
         * 附近没有猫
         */
        if (cat == null) {
            return;
        }

        /*
         * =========================
         * 每日抚摸次数
         * =========================
         */

        int petCount =
                plugin.getDataManager()
                        .getCatPetCount(
                                playerUUID
                        );

        if (petCount >= MAX_DAILY_PETS) {

            player.sendMessage(
                    "§e🐱 今天已经摸过猫咪 20 次啦！"
            );

            return;
        }

        /*
         * =========================
         * 冷却
         * =========================
         */

        long now =
                System.currentTimeMillis();

        Long last =
                cooldowns.get(
                        playerUUID
                );

        if (last != null &&
                now - last < COOLDOWN) {

            return;
        }

        cooldowns.put(
                playerUUID,
                now
        );

        /*
         * =========================
         * 抚摸次数 +1
         * =========================
         */

        plugin.getDataManager()
                .addCatPetCount(
                        playerUUID
                );

        /*
         * =========================
         * 好感度 +1
         * =========================
         */

        plugin.getDataManager()
                .addCatAffection(
                        playerUUID,
                        1
                );

        /*
         * 获取最新数据
         */
        int affection =
                plugin.getDataManager()
                        .getCatAffection(
                                playerUUID
                        );

        int currentPetCount =
                plugin.getDataManager()
                        .getCatPetCount(
                                playerUUID
                        );

        int remaining =
                MAX_DAILY_PETS
                        - currentPetCount;

        String name =
                plugin.getDataManager()
                        .getCatName(
                                playerUUID
                        );

        /*
         * =========================
         * 播放呼噜声
         * =========================
         */

        player.getWorld().playSound(
                cat.getLocation(),
                Sound.ENTITY_CAT_PURR,
                1.0f,
                1.0f
        );

        /*
         * =========================
         * 玩家提示
         * =========================
         */

        player.sendMessage(
                "§d🐱 " + name
                        + " §f蹭了蹭你！"
        );

        player.sendMessage(
                "§c❤ 好感度 §a+1"
                        + " §7("
                        + affection
                        + "/100)"
        );

        player.sendMessage(
                "§e🐾 今日抚摸："
                        + currentPetCount
                        + "/"
                        + MAX_DAILY_PETS
                        + " §7| 剩余 "
                        + remaining
                        + " 次"
        );
    }

    /*
     * =========================
     * 寻找附近自己的猫
     * =========================
     */

    private Cat findNearbyCat(
            Player player
    ) {

        Cat closestCat =
                null;

        double closestDistance =
                PET_DISTANCE *
                        PET_DISTANCE;

        for (Entity entity :
                player.getNearbyEntities(
                        PET_DISTANCE,
                        PET_DISTANCE,
                        PET_DISTANCE
                )) {

            if (!(entity instanceof Cat cat)) {
                continue;
            }

            if (cat.isDead() ||
                    !cat.isValid()) {
                continue;
            }

            /*
             * 必须是 Neko n' Yume 的猫
             */
            if (!cat.getPersistentDataContainer()
                    .has(
                            plugin.getCatManager()
                                    .getCatKey(),
                            PersistentDataType.BYTE
                    )) {
                continue;
            }

            /*
             * 获取主人
             */
            String ownerUUID =
                    cat.getPersistentDataContainer()
                            .get(
                                    plugin.getCatManager()
                                            .getOwnerKey(),
                                    PersistentDataType.STRING
                            );

            if (ownerUUID == null) {
                continue;
            }

            /*
             * 必须是自己的猫
             */
            if (!ownerUUID.equals(
                    player.getUniqueId()
                            .toString()
            )) {
                continue;
            }

            /*
             * 计算距离
             */
            double distance =
                    player.getLocation()
                            .distanceSquared(
                                    cat.getLocation()
                            );

            /*
             * 选择最近的一只
             */
            if (distance <
                    closestDistance) {

                closestDistance =
                        distance;

                closestCat =
                        cat;
            }
        }

        return closestCat;
    }
}