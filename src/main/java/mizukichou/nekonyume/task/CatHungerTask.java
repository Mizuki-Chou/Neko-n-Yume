package mizukichou.nekonyume.task;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEquipItem;
import mizukichou.nekonyume.cat.CatPersonality;
import mizukichou.nekonyume.cat.EquipBonusAttribute;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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

    /*
     * 羁绊纪元（0.8.0）：饥饿好感衰减节流表（玩家 UUID → 上次扣减时间）。
     * 与饥饿 tick 解耦：旧实现每 5 分钟扣一次，日喂两次仍好感净亏损；
     * 现按 care.hunger-affection-loss-minutes 节流，与喂食节奏对齐。
     * 纯节奏状态，重启丢失无影响；每轮 run 按现存玩家收敛。
     */
    private final Map<UUID, Long> lastStarveLossAt =
            new HashMap<>();

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
         * 一次性取玩家集合：循环与节流表收敛共用，
         * 避免每轮两次全量键遍历。
         */
        Set<UUID> players =
                store.getCatPlayers();

        /*
         * ========================================================
         * 遍历所有拥有猫咪的玩家
         * ========================================================
         */
        for (UUID playerUUID :
                players) {

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

        /*
         * 收敛节流表：只保留仍有猫数据的玩家，
         * 防止长时间运行后表无限增长。
         */
        lastStarveLossAt.keySet()
                .retainAll(
                        players
                );
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

        /*
         * 羁绊纪元（0.8.0）：日常衰减与饥饿结算相互独立。
         */
        applyDailyDecayOnline(
                cat,
                playerUUID
        );

        long effectiveInterval =
                effectiveInterval(
                        cat.getPersonality()
                                .getHungerRate(),
                        baseInterval
                );

        /*
         * 装备（0.8.0）：围巾的饥饿衰减减缓。
         */
        CatEquipItem equip =
                cat.getEquippedItem();

        if (equip != null &&
                equip.getHungerSlowPercent() > 0) {

            effectiveInterval =
                    applyHungerSlow(
                            effectiveInterval,
                            equip.getHungerSlowPercent()
                    );
        }

        /*
         * 附加属性（0.8.0）：暖炉的饥饿衰减减缓。
         */
        EquipBonusAttribute equipBonus =
                cat.getEquippedBonus();

        if (equipBonus != null &&
                equipBonus.getHungerSlowPercent() > 0) {

            effectiveInterval =
                    applyHungerSlow(
                            effectiveInterval,
                            equipBonus.getHungerSlowPercent()
                    );
        }

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
                pacedAffectionLoss(
                        playerUUID,
                        now,
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

        applyDailyDecayOffline(
                playerUUID,
                catId
        );

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

        /*
         * 装备（0.8.0）：围巾的饥饿衰减减缓（离线同样生效）。
         */
        CatEquipItem equip =
                CatEquipItem.fromCode(
                        store.getCatEquipment(
                                playerUUID
                        )
                );

        if (equip != null &&
                equip.getHungerSlowPercent() > 0) {

            effectiveInterval =
                    applyHungerSlow(
                            effectiveInterval,
                            equip.getHungerSlowPercent()
                    );
        }

        /*
         * 附加属性（0.8.0）：暖炉的饥饿衰减减缓（离线同样生效）。
         */
        EquipBonusAttribute equipBonus =
                EquipBonusAttribute.fromCode(
                        store.getCatEquipmentBonus(
                                playerUUID
                        )
                );

        if (equipBonus != null &&
                equipBonus.getHungerSlowPercent() > 0) {

            effectiveInterval =
                    applyHungerSlow(
                            effectiveInterval,
                            equipBonus.getHungerSlowPercent()
                    );
        }

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
                pacedAffectionLoss(
                        playerUUID,
                        now,
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

    /*
     * 羁绊纪元（0.8.0）：好感日常衰减。
     *
     * 每猫每服务器日只结算一次（离线同样结算）；
     * 好感已归零时跳过且不写结算日期（维持零写优化）；
     * 性格化：贪吃 -1 加重、悠闲 -1 减轻，其余走配置默认。
     */

    private void applyDailyDecayOnline(
            Cat cat,
            UUID playerUUID
    ) {

        ConfigSnapshot.Care care =
                configManager.snapshot()
                        .getCare();

        int decay =
                decayFor(
                        cat.getPersonality(),
                        care
                );

        if (decay <= 0) {
            return;
        }

        /*
         * 装备（0.8.0）：围巾的每日好感衰减减免。
         */
        CatEquipItem equip =
                cat.getEquippedItem();

        if (equip != null &&
                equip.getAffectionDecayReduce() > 0) {

            decay =
                    Math.max(
                            0,
                            decay
                                    - equip.getAffectionDecayReduce()
                    );
        }

        if (decay <= 0) {
            return;
        }

        String today =
                java.time.LocalDate.now()
                        .toString();

        if (today.equals(
                store.getAffectionDecayDate(
                        playerUUID
                )
        )) {

            return;
        }

        if (cat.getAffection() <= 0) {
            return;
        }

        cat.removeAffection(
                decay
        );

        store.setCatAffection(
                playerUUID,
                cat.getAffection()
        );

        store.setAffectionDecayDate(
                playerUUID,
                today
        );
    }

    private void applyDailyDecayOffline(
            UUID playerUUID,
            UUID catId
    ) {

        ConfigSnapshot.Care care =
                configManager.snapshot()
                        .getCare();

        int decay =
                decayFor(
                        CatPersonality.fromCatId(
                                catId
                        ),
                        care
                );

        if (decay <= 0) {
            return;
        }

        /*
         * 装备（0.8.0）：围巾的每日好感衰减减免（离线同样生效）。
         */
        CatEquipItem equip =
                CatEquipItem.fromCode(
                        store.getCatEquipment(
                                playerUUID
                        )
                );

        if (equip != null &&
                equip.getAffectionDecayReduce() > 0) {

            decay =
                    Math.max(
                            0,
                            decay
                                    - equip.getAffectionDecayReduce()
                    );
        }

        if (decay <= 0) {
            return;
        }

        String today =
                java.time.LocalDate.now()
                        .toString();

        if (today.equals(
                store.getAffectionDecayDate(
                        playerUUID
                )
        )) {

            return;
        }

        int affection =
                store.getCatAffection(
                        playerUUID
                );

        if (affection <= 0) {
            return;
        }

        store.setCatAffection(
                playerUUID,
                Math.max(
                        0,
                        affection - decay
                )
        );

        store.setAffectionDecayDate(
                playerUUID,
                today
        );
    }

    private int decayFor(
            CatPersonality personality,
            ConfigSnapshot.Care care
    ) {

        int base =
                care.getAffectionDailyDecay();

        if (base <= 0) {
            return 0;
        }

        switch (personality) {

            case GOURMAND -> {

                return base + 1;
            }

            case LAZY -> {

                return Math.max(
                        1,
                        base - 1
                );
            }

            default -> {

                return base;
            }
        }
    }

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

    /*
     * 装备（0.8.0）：围巾的饥饿衰减减缓。
     *
     * 纯函数（供单测）：间隔按 (1 - slow%/100) 放大；
     * 防御钳制 slowPercent ∈ [0, 90]，异常输入原样返回。
     */

    static long applyHungerSlow(
            long intervalMillis,
            int slowPercent
    ) {

        if (intervalMillis <= 0) {
            return intervalMillis;
        }

        int clamped =
                Math.max(
                        0,
                        Math.min(
                                90,
                                slowPercent
                        )
                );

        if (clamped <= 0) {
            return intervalMillis;
        }

        double factor =
                1.0 - clamped / 100.0;

        long slowed =
                (long) Math.round(
                        intervalMillis / factor
                );

        return Math.max(
                intervalMillis,
                slowed
        );
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

    /*
     * 羁绊纪元（0.8.0）：按配置间隔节流后的饥饿好感衰减。
     */

    private int pacedAffectionLoss(
            UUID playerUUID,
            long now,
            int newHunger
    ) {

        int base =
                affectionLoss(
                        newHunger
                );

        if (base <= 0) {
            return 0;
        }

        long intervalMillis =
                configManager.snapshot()
                        .getCare()
                        .getHungerAffectionLossMinutes()
                        * 60_000L;

        if (intervalMillis <= 0) {
            return 0;
        }

        Long last =
                lastStarveLossAt.get(
                        playerUUID
                );

        if (!shouldApplyStarveLoss(
                now,
                last == null
                        ? 0L
                        : last,
                intervalMillis
        )) {

            return 0;
        }

        lastStarveLossAt.put(
                playerUUID,
                now
        );

        return base;
    }

    /*
     * 纯判定函数（供单测）：距上次扣减是否已满一个节流间隔。
     *
     * <p>
     * 哨兵分支：now 与 lastAt 同时为 0 表示时间戳从未
     * 初始化（首次判定），立即应用。生产中 now 为当前
     * 毫秒时间戳，该分支不会触发，无行为影响。
     * </p>
     */

    static boolean shouldApplyStarveLoss(
            long now,
            long lastAt,
            long intervalMillis
    ) {

        return intervalMillis > 0 &&
                (now - lastAt >= intervalMillis ||
                        (now == 0L && lastAt == 0L));
    }
}
