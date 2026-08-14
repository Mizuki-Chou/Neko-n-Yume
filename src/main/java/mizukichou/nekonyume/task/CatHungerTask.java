package mizukichou.nekonyume.task;

import mizukichou.nekonyume.NekoNYume;
import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatPersonality;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class CatHungerTask implements Runnable {

    /*
     * ============================================================
     * 饥饿规则
     * ============================================================
     *
     * 基础间隔来自 config: hunger.base-interval-seconds
     * （默认 300 秒 = 5 分钟 -1 点）。
     *
     * 实际间隔由性格的饥饿速率倍率修正。
     */

    /*
     * 饱食度 <= 20：
     * 每次饥饿结算，好感度 -1。
     */
    private static final int LOW_HUNGER_THRESHOLD =
            20;

    private static final int LOW_HUNGER_AFFECTION_LOSS =
            1;

    /*
     * 饱食度 = 0：
     * 每次饥饿结算，好感度 -2。
     */
    private static final int EMPTY_HUNGER_AFFECTION_LOSS =
            2;

    /*
     * 防止一次异常运行产生过大的结算。
     */
    private static final int MAX_HUNGER_DECREASE =
            100;

    private final NekoNYume plugin;

    public CatHungerTask(
            NekoNYume plugin
    ) {

        this.plugin = plugin;
    }

    @Override
    public void run() {

        long now =
                System.currentTimeMillis();

        long baseInterval =
                plugin.getPluginConfig()
                        .getHungerIntervalMillis();

        /*
         * ========================================================
         * 遍历所有拥有猫咪的玩家
         * ========================================================
         */
        for (UUID playerUUID :
                plugin.getDataManager()
                        .getCatPlayers()) {

            /*
             * ====================================================
             * 读取上一次饥饿结算时间
             * ====================================================
             */
            long lastUpdate =
                    plugin.getDataManager()
                            .getCatHungerLastUpdate(
                                    playerUUID
                            );

            /*
             * 防止系统时间异常倒退。
             */
            if (lastUpdate > now) {

                plugin.getDataManager()
                        .setCatHungerLastUpdate(
                                playerUUID,
                                now
                        );

                continue;
            }

            Player owner =
                    Bukkit.getPlayer(
                            playerUUID
                    );

            if (owner != null &&
                    owner.isOnline()) {

                handleOnline(
                        playerUUID,
                        now,
                        baseInterval,
                        lastUpdate
                );

            } else {

                handleOffline(
                        playerUUID,
                        now,
                        baseInterval,
                        lastUpdate
                );
            }
        }
    }

    /*
     * ============================================================
     * 在线玩家
     * ============================================================
     *
     * 走运行时 Cat 结算，
     * 缓存保持（不卸载）。
     */

    private void handleOnline(
            UUID playerUUID,
            long now,
            long baseInterval,
            long lastUpdate
    ) {

        Cat cat =
                plugin.getCatManager()
                        .loadCat(
                                playerUUID
                        );

        if (cat == null) {
            return;
        }

        long effectiveInterval =
                effectiveInterval(
                        cat.getPersonality()
                                .getHungerRate(),
                        baseInterval
                );

        long elapsed =
                now - lastUpdate;

        if (elapsed < effectiveInterval) {
            return;
        }

        long decrease =
                elapsed / effectiveInterval;

        int decreaseAmount =
                (int) Math.min(
                        decrease,
                        MAX_HUNGER_DECREASE
                );

        int newHunger =
                Math.max(
                        0,
                        cat.getHunger()
                                - decreaseAmount
                );

        cat.setHunger(
                newHunger
        );

        int affectionLoss =
                affectionLoss(
                        newHunger
                );

        if (affectionLoss > 0) {

            cat.removeAffection(
                    affectionLoss
            );
        }

        /*
         * 持久化。
         */
        plugin.getDataManager()
                .setCatHunger(
                        playerUUID,
                        cat.getHunger()
                );

        plugin.getDataManager()
                .setCatAffection(
                        playerUUID,
                        cat.getAffection()
                );

        /*
         * 更新时间（保留未结算的余数）。
         */
        plugin.getDataManager()
                .setCatHungerLastUpdate(
                        playerUUID,
                        lastUpdate
                                + decrease
                                * effectiveInterval
                );
    }

    /*
     * ============================================================
     * 离线玩家
     * ============================================================
     *
     * 不加载运行时 Cat：
     * - 不打印 "Loaded cat" 日志；
     * - 不产生对象构造开销；
     * - 缓存永远只含在线玩家。
     *
     * 性格速率通过逻辑猫 UUID 推导
     * （UUID 直接存在于 players.yml）。
     */

    private void handleOffline(
            UUID playerUUID,
            long now,
            long baseInterval,
            long lastUpdate
    ) {

        UUID catId =
                plugin.getDataManager()
                        .getCatUUID(
                                playerUUID
                        );

        if (catId == null) {
            return;
        }

        double hungerRate =
                CatPersonality.fromCatId(
                                catId
                        )
                        .getHungerRate();

        long effectiveInterval =
                effectiveInterval(
                        hungerRate,
                        baseInterval
                );

        long elapsed =
                now - lastUpdate;

        if (elapsed < effectiveInterval) {
            return;
        }

        long decrease =
                elapsed / effectiveInterval;

        int decreaseAmount =
                (int) Math.min(
                        decrease,
                        MAX_HUNGER_DECREASE
                );

        int hunger =
                plugin.getDataManager()
                        .getCatHunger(
                                playerUUID
                        );

        int newHunger =
                Math.max(
                        0,
                        hunger - decreaseAmount
                );

        plugin.getDataManager()
                .setCatHunger(
                        playerUUID,
                        newHunger
                );

        int affectionLoss =
                affectionLoss(
                        newHunger
                );

        if (affectionLoss > 0) {

            plugin.getDataManager()
                    .setCatAffection(
                            playerUUID,
                            plugin.getDataManager()
                                    .getCatAffection(
                                            playerUUID
                                    )
                                    - affectionLoss
                    );
        }

        /*
         * 更新时间（保留未结算的余数）。
         */
        plugin.getDataManager()
                .setCatHungerLastUpdate(
                        playerUUID,
                        lastUpdate
                                + decrease
                                * effectiveInterval
                );
    }

    /*
     * ============================================================
     * 工具
     * ============================================================
     */

    private long effectiveInterval(
            double hungerRate,
            long baseInterval
    ) {

        if (hungerRate <= 0) {
            hungerRate = 1.0;
        }

        long interval =
                (long) Math.round(
                        baseInterval / hungerRate
                );

        return interval <= 0
                ? baseInterval
                : interval;
    }

    private int affectionLoss(
            int newHunger
    ) {

        if (newHunger <= 0) {
            return EMPTY_HUNGER_AFFECTION_LOSS;
        }

        if (newHunger <= LOW_HUNGER_THRESHOLD) {
            return LOW_HUNGER_AFFECTION_LOSS;
        }

        return 0;
    }
}
