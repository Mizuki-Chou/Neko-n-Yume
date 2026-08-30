package mizukichou.nekonyume.achievement;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatProgressionService;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.event.CatAchievementUnlockedEvent;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 成就服务。
 *
 * <p>
 * 职责：
 * 1. 计数器进度推进（抚摸 / 喂食 / 技能 / 底蕴 / 礼物 / 击杀）；
 * 2. 派生成就判定（领取 / 陪伴天数 / 等级 / 喵阶）；
 * 3. 解锁持久化与奖励发放（经验 / 喵力，
 *    严格走 CatProgressionService 唯一入口）；
 * 4. 解锁通知（title + 音效 + 消息 + 粒子）与对外事件。
 * </p>
 *
 * <p>
 * 铁律遵守：
 * - 只读 Cat 与 CatStore，绝不建档；
 * - 先持久化解锁，再发放奖励（防重入重复发放）；
 * - 全部逻辑运行在主线程（事件回调 / 命令）。
 * </p>
 *
 * <p>
 * 0.7.0：配置改走 ConfigManager 快照；
 * 玩家文案改走 Lang（achievement.* 节）。
 * </p>
 */
public class AchievementService {

    private final CatStore store;
    private final CatCache cache;
    private final CatProgressionService progression;
    private final ConfigManager configManager;
    private final Lang lang;
    private final Logger logger;

    public AchievementService(
            CatStore store,
            CatCache cache,
            CatProgressionService progression,
            ConfigManager configManager,
            Lang lang,
            Logger logger
    ) {

        this.store = store;
        this.cache = cache;
        this.progression = progression;
        this.configManager = configManager;
        this.lang = lang;
        this.logger = logger;
    }

    /*
     * ============================================================
     * 触发入口
     * ============================================================
     */

    /**
     * 全面检查（登录 / 领取时调用）。
     *
     * <p>
     * 覆盖派生成就：相遇即是缘 / 陪伴天数 / 等级 / 喵阶，
     * 以及离线期间积累的击杀进度。
     * </p>
     */
    public void checkAll(Player player) {

        if (!enabled(player)) {
            return;
        }

        Cat cat =
                cache.getCat(player);

        if (cat == null) {

            cat =
                    cache.loadCat(player);
        }

        if (cat == null) {
            return;
        }

        checkAndUnlock(
                player,
                cat
        );
    }

    public void onFeed(Player player) {

        bump(
                player,
                CatAchievement.KEY_FEED
        );
    }

    public void onPet(Player player) {

        bump(
                player,
                CatAchievement.KEY_PET
        );
    }

    public void onSkillActivate(Player player) {

        bump(
                player,
                CatAchievement.KEY_SKILL_ACTIVATE
        );
    }

    public void onSkillRefresh(Player player) {

        bump(
                player,
                CatAchievement.KEY_SKILL_REFRESH
        );
    }

    public void onTierUpgrade(Player player) {

        bump(
                player,
                CatAchievement.KEY_TIER_UPGRADE
        );
    }

    public void onGift(Player player) {

        bump(
                player,
                CatAchievement.KEY_GIFT
        );
    }

    /**
     * 怪物被本插件猫击杀。
     *
     * <p>
     * 主人离线时只推进持久化计数，
     * 解锁在下次登录的 checkAll 中完成。
     * </p>
     */
    public void onMonsterKill(UUID ownerUuid) {

        if (!isEnabled()) {
            return;
        }

        if (ownerUuid == null ||
                !store.hasCat(ownerUuid)) {

            return;
        }

        store.addAchievementProgress(
                ownerUuid,
                CatAchievement.KEY_MONSTER_KILL,
                1
        );

        Player player =
                Bukkit.getPlayer(ownerUuid);

        if (player != null &&
                player.isOnline()) {

            checkAll(player);
        }
    }

    /*
     * ============================================================
     * 查询（GUI / 命令使用）
     * ============================================================
     */

    private boolean isEnabled() {

        return configManager.snapshot()
                .getAchievements()
                .isEnabled();
    }

    public int rewardXp(CatAchievement achievement) {

        return configManager.snapshot()
                .getAchievements()
                .rewardXp(
                        achievement,
                        achievement == null
                                ? 0
                                : achievement.getDefaultRewardXp()
                );
    }

    public int rewardMeowPower(CatAchievement achievement) {

        return configManager.snapshot()
                .getAchievements()
                .rewardMeowPower(
                        achievement,
                        achievement == null
                                ? 0
                                : achievement.getDefaultRewardMeowPower()
                );
    }

    /**
     * 当前进度值（GUI 进度显示）。
     */
    public int currentValue(
            CatAchievement achievement,
            Player player,
            Cat cat
    ) {

        if (achievement == null) {
            return 0;
        }

        return valueOf(
                achievement,
                cat,
                System.currentTimeMillis(),
                key -> store.getAchievementProgress(
                        player.getUniqueId(),
                        key
                )
        );
    }

    /*
     * ============================================================
     * 内部
     * ============================================================
     */

    private boolean enabled(Player player) {

        return isEnabled() &&
                player != null &&
                player.isOnline();
    }

    private void bump(
            Player player,
            String key
    ) {

        if (!enabled(player)) {
            return;
        }

        UUID playerUuid =
                player.getUniqueId();

        if (!store.hasCat(playerUuid)) {
            return;
        }

        store.addAchievementProgress(
                playerUuid,
                key,
                1
        );

        checkAll(player);
    }

    /*
     * 解锁主循环（不动点迭代）：
     *
     * 奖励发放可能推进等级 / 喵阶，
     * 从而在同一检查中连锁解锁派生成就。
     * 循环上限 = 成就总数，保证必然终止。
     *
     * P0-2：每轮先补发 pending 队列中的奖励
     * （崩溃恢复路径），再做普通解锁判定。
     */
    private void checkAndUnlock(
            Player player,
            Cat cat
    ) {

        UUID playerUuid =
                player.getUniqueId();

        long now =
                System.currentTimeMillis();

        int rounds = 0;

        boolean progressed = true;

        while (progressed &&
                rounds < CatAchievement.values().length) {

            progressed = false;

            rounds++;

            /*
             * 1. 崩溃恢复：补发 pending 奖励。
             *
             * 解锁/台账/奖励写在同一个 YAML 文档里，
             * 由同一份快照原子落盘：
             * - 崩溃于落盘前 → 解锁与奖励都未持久化，
             *   下次登录重新解锁，不重复、不丢失；
             * - 崩溃于奖励发放后、pending 清除前 →
             *   奖励也未随崩溃前的快照落盘，
             *   下次登录补发恰好一次；
             * - 发放环节抛异常（0.7.4 台账）→
             *   "先记台账、后发奖励"使失败方向被约束为
             *   少发而非多发，绝不重复发放。
             */
            for (String pendingName :
                    store.getAchievementsPendingList(
                            playerUuid
                    )) {

                CatAchievement pending =
                        CatAchievement.fromName(
                                pendingName
                        );

                if (pending == null) {

                    /*
                     * 未知条目（未来版本回滚等异常）：
                     * 清除标记，避免每次登录重复扫描。
                     */
                    store.removeAchievementPending(
                            playerUuid,
                            pendingName
                    );

                    continue;
                }

                try {

                    completePendingReward(
                            player,
                            cat,
                            pending
                    );

                    /*
                     * 只有补发成功才推进循环：
                     * 失败时 pending 标记仍在，下一轮由外层循环
                     * 自然重试，不在同一轮内反复尝试。
                     */
                    progressed = true;

                } catch (Exception exception) {

                    /*
                     * 单条恢复异常隔离：
                     * 保留 pending 标记下次重试，
                     * 绝不阻断其余成就的判定与解锁。
                     */
                    logger.log(
                            Level.SEVERE,
                            "Failed to complete pending reward for "
                                    + pendingName
                                    + " ("
                                    + player.getName()
                                    + "), will retry later.",
                            exception
                    );
                }
            }

            /*
             * 2. 普通解锁判定。
             */
            Set<String> unlocked =
                    new HashSet<>(
                            store.getAchievementsUnlockedList(
                                    playerUuid
                            )
                    );

            for (CatAchievement achievement :
                    CatAchievement.values()) {

                /*
                 * add 返回 false = 已解锁或本循环已尝试；
                 * 已解锁的成就永不重复判定。
                 */
                if (!unlocked.add(
                        achievement.name()
                )) {

                    continue;
                }

                int value =
                        valueOf(
                                achievement,
                                cat,
                                now,
                                key -> store
                                        .getAchievementProgress(
                                                playerUuid,
                                                key
                                        )
                        );

                if (value >=
                        achievement.getThreshold()) {

                    unlock(
                            player,
                            cat,
                            achievement
                    );

                    progressed = true;

                } else {

                    /*
                     * 未达成：回滚尝试标记，
                     * 后续循环（状态被奖励推进后）可重判。
                     */
                    unlocked.remove(
                            achievement.name()
                    );
                }
            }
        }
    }

    /*
     * 解锁（P0-2 三态：未解锁 → pending → 已解锁+已发奖）：
     *
     * 1. 先持久化解锁标记与 pending 标记；
     * 2. 发放奖励；
     * 3. 成功后移除 pending 标记。
     *
     * 任何一步抛异常：保留 unlocked + pending 标记，
     * 下次 checkAll 通过 completePendingReward 补发，
     * 奖励绝不静默丢失；已解锁标记保证绝不重复发放。
     */
    private void unlock(
            Player player,
            Cat cat,
            CatAchievement achievement
    ) {

        UUID playerUuid =
                player.getUniqueId();

        store.addAchievementUnlocked(
                playerUuid,
                achievement.name()
        );

        store.addAchievementPending(
                playerUuid,
                achievement.name()
        );

        try {

            completePendingReward(
                    player,
                    cat,
                    achievement
            );

            notifyUnlock(
                    player,
                    cat,
                    achievement,
                    rewardXp(achievement),
                    rewardMeowPower(achievement)
            );

            Bukkit.getPluginManager()
                    .callEvent(
                            new CatAchievementUnlockedEvent(
                                    player,
                                    cat,
                                    achievement,
                                    rewardXp(achievement),
                                    rewardMeowPower(achievement)
                            )
                    );

        } catch (Exception exception) {

            logger.log(
                    Level.SEVERE,
                    "Failed to complete reward for achievement "
                            + achievement.name()
                            + " for "
                            + player.getName()
                            + ". Ledger recorded; next check will verify and skip re-grant.",
                    exception
            );
        }
    }

    /*
     * 补发一个成就的奖励并清除 pending 标记。
     *
     * 0.7.4 防重台账（issues 核查）：
     * 顺序为"先记台账 → 再发奖励 → 最后清 pending"：
     * - 台账与经验/喵力同文档同快照，任何进程崩溃点
     *   都不会出现"奖励已落盘而台账未落盘"的组合；
     * - 已记台账的成就绝不重复发奖；
     * - 任何一步抛异常时 pending 标记保持，
     *   由下次 checkAll 继续重试。
     */
    private void completePendingReward(
            Player player,
            Cat cat,
            CatAchievement achievement
    ) {

        UUID playerUuid =
                player.getUniqueId();

        /*
         * 幂等补记解锁标记：
         * 封堵"pending 存在而 unlocked 缺失"的异常数据组合。
         */
        store.addAchievementUnlocked(
                playerUuid,
                achievement.name()
        );

        /*
         * 防重核心：台账已记账 → 只清 pending，绝不重发。
         */
        if (store.isAchievementRewarded(
                playerUuid,
                achievement.name()
        )) {

            store.removeAchievementPending(
                    playerUuid,
                    achievement.name()
            );

            return;
        }

        /*
         * 先记台账，后发奖励。
         * 若此后的发放环节抛异常，pending 保持而台账已记，
         * 下次补发会因台账命中而跳过——失败方向被
         * 约束为"少发"而非"多发"（经济系统正确方向）。
         */
        store.addAchievementRewarded(
                playerUuid,
                achievement.name()
        );

        int xp =
                rewardXp(achievement);

        int meow =
                rewardMeowPower(achievement);

        if (xp > 0) {

            progression.gainExperience(
                    player,
                    cat,
                    xp
            );
        }

        if (meow > 0) {

            progression.grantMeowPower(
                    player,
                    cat,
                    meow
            );
        }

        store.removeAchievementPending(
                playerUuid,
                achievement.name()
        );
    }

    private void notifyUnlock(
            Player player,
            Cat cat,
            CatAchievement achievement,
            int xp,
            int meow
    ) {

        /*
         * title 大字提示。
         */
        player.sendTitle(
                lang.forPlayer(player).text(
                        "achievement.unlock-title"
                ),
                lang.forPlayer(player).text(
                        "achievement.unlock-subtitle",
                        lang.forPlayer(player).text(
                                "achievement-name."
                                        + achievement.getConfigId()
                        )
                ),
                10,
                60,
                10
        );

        player.playSound(
                player.getLocation(),
                Sound.UI_TOAST_CHALLENGE_COMPLETE,
                1.0f,
                1.0f
        );

        /*
         * 成就名与描述是枚举固定文本，安全拼接。
         */
        player.sendMessage(
                lang.forPlayer(player).message(
                        "achievement.unlocked",
                        lang.forPlayer(player).text(
                                "achievement-name."
                                        + achievement.getConfigId()
                        ),
                        lang.forPlayer(player).text(
                                "achievement-desc."
                                        + achievement.getConfigId()
                        )
                )
        );

        String rewardText;

        if (xp > 0 && meow > 0) {

            rewardText =
                    lang.forPlayer(player).text(
                            "achievement-gui.reward-both",
                            String.valueOf(xp),
                            String.valueOf(meow)
                    );

        } else if (meow > 0) {

            rewardText =
                    lang.forPlayer(player).text(
                            "achievement-gui.reward-meow",
                            String.valueOf(meow)
                    );

        } else {

            rewardText =
                    lang.forPlayer(player).text(
                            "achievement-gui.reward-xp",
                            String.valueOf(xp)
                    );
        }

        player.sendMessage(
                lang.forPlayer(player).message(
                        "achievement.reward",
                        rewardText
                )
        );

        spawnParticles(cat);
    }

    private void spawnParticles(Cat cat) {

        if (cat == null) {
            return;
        }

        UUID entityUuid =
                cat.getEntityUuid();

        if (entityUuid == null) {
            return;
        }

        Entity entity =
                Bukkit.getEntity(
                        entityUuid
                );

        if (entity == null ||
                !entity.isValid()) {

            return;
        }

        entity.getWorld()
                .spawnParticle(
                        Particle.HEART,
                        entity.getLocation()
                                .add(
                                        0,
                                        1,
                                        0
                                ),
                        25,
                        0.4,
                        0.4,
                        0.4,
                        0.02
                );
    }

    /*
     * ============================================================
     * 纯函数（可单元测试）
     * ============================================================
     */

    /**
     * 计算成就当前值。
     */
    static int valueOf(
            CatAchievement achievement,
            Cat cat,
            long now,
            ToIntFunction<String> counterReader
    ) {

        if (achievement == null ||
                cat == null) {

            return 0;
        }

        return switch (achievement.getMetric()) {

            case CLAIM -> 1;

            case COMPANION_DAYS ->
                    cat.getCompanionDays(now);

            case LEVEL ->
                    cat.getLevel();

            case MEOW_RANK ->
                    cat.getMeowRank();

            case COUNTER ->
                    Math.max(
                            0,
                            counterReader.applyAsInt(
                                    achievement.getCounterKey()
                            )
                    );

            case AFFECTION ->
                    cat.getAffection();
        };
    }

    /**
     * 单轮评估：返回可解锁的成就列表
     * （已解锁的除外）。仅供测试与调试使用。
     */
    static List<CatAchievement> evaluateUnlocks(
            Set<String> unlocked,
            Cat cat,
            long now,
            ToIntFunction<String> counterReader
    ) {

        List<CatAchievement> result =
                new ArrayList<>();

        if (cat == null) {
            return result;
        }

        for (CatAchievement achievement :
                CatAchievement.values()) {

            if (unlocked != null &&
                    unlocked.contains(
                            achievement.name()
                    )) {

                continue;
            }

            int value =
                    valueOf(
                            achievement,
                            cat,
                            now,
                            counterReader
                    );

            if (value >=
                    achievement.getThreshold()) {

                result.add(
                        achievement
                );
            }
        }

        return result;
    }
}
