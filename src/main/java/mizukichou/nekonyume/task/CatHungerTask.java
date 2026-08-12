package mizukichou.nekonyume.task;

import mizukichou.nekonyume.NekoNYume;

import java.util.UUID;

public class CatHungerTask implements Runnable {

    /*
     * 每 5 分钟减少 1 点饱食度
     */
    private static final long HUNGER_INTERVAL =
            5 * 60 * 1000L;

    /*
     * 饱食度低于等于 20：
     * 每次饥饿结算，好感度 -1
     */
    private static final int LOW_HUNGER_THRESHOLD =
            20;

    /*
     * 饱食度 1~20：
     * 每次结算，好感度 -1
     */
    private static final int LOW_HUNGER_AFFECTION_LOSS =
            1;

    /*
     * 饱食度 0：
     * 每次结算，好感度 -2
     */
    private static final int EMPTY_HUNGER_AFFECTION_LOSS =
            2;

    private final NekoNYume plugin;

    public CatHungerTask(NekoNYume plugin) {
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
             * 上次计算饱食度的时间
             */
            long lastUpdate =
                    plugin.getDataManager()
                            .getCatHungerLastUpdate(
                                    playerUUID
                            );

            /*
             * 已经过了多少时间
             */
            long elapsed =
                    now - lastUpdate;

            /*
             * 还没到 5 分钟
             */
            if (elapsed < HUNGER_INTERVAL) {
                continue;
            }

            /*
             * 计算应该减少多少点
             *
             * 5 分钟  → -1
             * 10 分钟 → -2
             * 15 分钟 → -3
             */
            long decrease =
                    elapsed / HUNGER_INTERVAL;

            /*
             * 防止异常时间造成过大的数值
             */
            int decreaseAmount =
                    (int) Math.min(
                            decrease,
                            100
                    );

            /*
             * 获取当前饱食度
             */
            int currentHunger =
                    plugin.getDataManager()
                            .getCatHunger(
                                    playerUUID
                            );

            /*
             * 饱食度最低为 0
             */
            int newHunger =
                    Math.max(
                            0,
                            currentHunger
                                    - decreaseAmount
                    );

            /*
             * 保存新的饱食度
             */
            plugin.getDataManager()
                    .setCatHunger(
                            playerUUID,
                            newHunger
                    );

            /*
             * =========================
             * 饱食度影响好感度
             * =========================
             *
             * 结算后的饱食度：
             *
             * 21~100
             * → 不影响好感度
             *
             * 1~20
             * → 好感度 -1
             *
             * 0
             * → 好感度 -2
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
             * 扣除好感度
             */
            if (affectionLoss > 0) {

                plugin.getDataManager()
                        .addCatAffection(
                                playerUUID,
                                -affectionLoss
                        );
            }

            /*
             * 更新时间
             *
             * 例如：
             * 经过 12 分钟
             * ↓
             * 减少 2 点饱食度
             * ↓
             * 剩余 2 分钟继续累计
             */
            plugin.getDataManager()
                    .setCatHungerLastUpdate(
                            playerUUID,
                            lastUpdate
                                    + decrease
                                    * HUNGER_INTERVAL
                    );
        }
    }
}