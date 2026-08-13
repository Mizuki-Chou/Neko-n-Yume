package mizukichou.nekonyume.task;

import mizukichou.nekonyume.NekoNYume;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class CatHungerTask implements Runnable {

    /*
     * ============================================================
     * 饥饿规则
     * ============================================================
     *
     * 每 5 分钟减少 1 点饱食度。
     */
    private static final long HUNGER_INTERVAL =
            5 * 60 * 1000L;

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

            /*
             * ====================================================
             * 计算经过时间
             * ====================================================
             */
            long elapsed =
                    now - lastUpdate;

            /*
             * 尚未达到一次饥饿结算。
             */
            if (elapsed < HUNGER_INTERVAL) {
                continue;
            }

            /*
             * ====================================================
             * 计算应该减少多少点
             * ====================================================
             *
             * 5 分钟  → -1
             * 10 分钟 → -2
             * 15 分钟 → -3
             */
            long decrease =
                    elapsed / HUNGER_INTERVAL;

            int decreaseAmount =
                    (int) Math.min(
                            decrease,
                            MAX_HUNGER_DECREASE
                    );

            /*
             * ====================================================
             * 加载运行时 Cat
             * ====================================================
             *
             * 注意：
             * 即使玩家离线，
             * 这里仍然可以加载 Cat。
             */
            mizukichou.nekonyume.cat.Cat cat =
                    plugin.getCatManager()
                            .loadCat(
                                    playerUUID
                            );

            /*
             * 如果猫咪数据异常，
             * 不继续修改。
             */
            if (cat == null) {
                continue;
            }

            /*
             * ====================================================
             * 从运行时 Cat 获取饱食度
             * ====================================================
             */
            int currentHunger =
                    cat.getHunger();

            /*
             * ====================================================
             * 计算新的饱食度
             * ====================================================
             */
            int newHunger =
                    Math.max(
                            0,
                            currentHunger
                                    - decreaseAmount
                    );

            /*
             * ====================================================
             * 更新运行时 Cat
             * ====================================================
             */
            cat.setHunger(
                    newHunger
            );

            /*
             * ====================================================
             * 饱食度影响好感度
             * ====================================================
             *
             * 结算后的饱食度：
             *
             * 21~100
             * → 不影响
             *
             * 1~20
             * → -1
             *
             * 0
             * → -2
             */
            int affectionLoss = 0;

            if (newHunger <= 0) {

                affectionLoss =
                        EMPTY_HUNGER_AFFECTION_LOSS;

            } else if (newHunger <= LOW_HUNGER_THRESHOLD) {

                affectionLoss =
                        LOW_HUNGER_AFFECTION_LOSS;
            }

            /*
             * ====================================================
             * 更新运行时好感度
             * ====================================================
             */
            if (affectionLoss > 0) {

                cat.removeAffection(
                        affectionLoss
                );
            }

            /*
             * ====================================================
             * 持久化运行时状态
             * ====================================================
             *
             * Cat 是运行时唯一真相。
             *
             * PlayerDataManager 负责保存。
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
             * ====================================================
             * 更新时间
             * ====================================================
             *
             * 例如：
             *
             * 经过 12 分钟
             * ↓
             * decrease = 2
             * ↓
             * -2 饱食度
             * ↓
             * lastUpdate + 10 分钟
             * ↓
             * 剩余 2 分钟继续累计
             */
            long newLastUpdate =
                    lastUpdate
                            + decrease
                            * HUNGER_INTERVAL;

            plugin.getDataManager()
                    .setCatHungerLastUpdate(
                            playerUUID,
                            newLastUpdate
                    );

            /*
             * ====================================================
             * 离线玩家：结算完成后立即卸载运行时缓存
             * ====================================================
             *
             * 饥饿任务每分钟都会加载所有猫主人，
             * 包括离线玩家。
             *
             * 如果不在这里卸载，
             * 缓存会长期累积全部离线玩家，
             * 违背"退出后移除运行时对象"的设计。
             *
             * 数据已经通过上面的 setter
             * 写入 YAML 内存，
             * 后续由自动保存 / flush 落盘。
             *
             * saveCat 再做一次完整同步，
             * 然后从缓存移除。
             */
            Player owner =
                    Bukkit.getPlayer(
                            playerUUID
                    );

            if (owner == null ||
                    !owner.isOnline()) {

                plugin.getCatManager()
                        .saveCat(
                                cat
                        );

                plugin.getCatManager()
                        .removeLogicalCat(
                                playerUUID
                        );
            }
        }
    }
}
