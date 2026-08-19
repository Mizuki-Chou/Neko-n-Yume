package mizukichou.nekonyume.task;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatPersonality;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 饥饿结算任务。
 *
 * <p>
 * 0.7.0：配置改走 ConfigManager 快照。
 * 本任务无玩家消息（纯后台结算）。
 * </p>
 */
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

    private final ConfigManager configManager;
    private final CatStore store;
    private final CatCache cache;

    public CatHungerTask(
            ConfigManager configManager,
            CatStore store,
            CatCache cache
    ) {

        this.configManager = configManager;
        this.store = store;
        this.cache = cache;
    }

    @Override
    public void run() {

        long now =
                System.currentTimeMillis();

        long baseInterval =
                configManager.snapshot()
                        .getHunger()
                        .getIntervalMillis();

        /*
         * ========================================================
         * 遍历所有拥有猫咪的玩家
         * ========================================================
         */
        for (UUID playerUUID :
                store.getCatPlayers()) {

            /*
             * ====================================================
             * 读取上一次饥饿结算时间
             * ====================================================
             */
            long lastUpdate =
                    store.getCatHungerLastUpdate(
                            playerUUID
                    );

            /*
             * 防止系统时间异常倒退。
             */
            if (lastUpdate > now) {

                store.setCatHungerLastUpdate(
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
                cache.loadCat(
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

        /*
         * 已完全归零（饱食 0 且好感 0）：
         * 不再有任何可衰减的数值，
         * 直接返回——不写任何字段、不推进 lastUpdate、不置脏。
         *
         * 否则每个结算周期都会对“不变的 0”写盘并置脏，
         * 导致生产环境下 autosave 永远在重写整个文件。
         * （喂食会把 lastUpdate 重置为 now，因此冻结不影响正确性。）
         */
        if (cat.getHunger() <= 0 &&
                cat.getAffection() <= 0) {

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
        store.setCatHunger(
                playerUUID,
                cat.getHunger()
        );

        store.setCatAffection(
                playerUUID,
                cat.getAffection()
        );

        /*
         * 更新时间（保留未结算的余数）。
         */
        store.setCatHungerLastUpdate(
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
                store.getCatUUID(
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

        int hunger =
                store.getCatHunger(
                        playerUUID
                );

        /*
         * 已完全归零：跳过一切写入（与在线路径同语义）。
         */
        if (hunger <= 0 &&
                store.getCatAffection(
                        playerUUID
                ) <= 0) {

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
                        hunger - decreaseAmount
                );

        store.setCatHunger(
                playerUUID,
                newHunger
        );

        int affectionLoss =
                affectionLoss(
                        newHunger
                );

        if (affectionLoss > 0) {

            store.setCatAffection(
                    playerUUID,
                    store.getCatAffection(
                            playerUUID
                    )
                            - affectionLoss
            );
        }

        /*
         * 更新时间（保留未结算的余数）。
         */
        store.setCatHungerLastUpdate(
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
